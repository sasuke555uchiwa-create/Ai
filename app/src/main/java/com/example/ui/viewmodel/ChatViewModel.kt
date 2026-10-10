package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.AppTypeConverters
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.ActiveToolType
import com.example.data.model.AvailableModels
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.WebSource
import com.example.data.remote.GeminiApiClient
import com.example.data.remote.GeminiStreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val currentSessionId: String = UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingSources: List<WebSource> = emptyList(),
    val selectedModelId: String = AvailableModels.GEMINI_3_5_FLASH.id,
    val isSearchGroundingEnabled: Boolean = false,
    val selectedImageBase64: String? = null,
    val errorMessage: String? = null,
    val isApiKeyConfigured: Boolean = true,
    // Plus Tools Menu State
    val isPlusMenuOpen: Boolean = false,
    val activeTool: ActiveToolType = ActiveToolType.NONE,
    val activeToolParam: String = "",
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.appDao()
    val apiClient = GeminiApiClient(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null

    init {
        checkApiKey()
        loadOrCreateInitialSession()
    }

    fun checkApiKey() {
        val configured = apiClient.isApiKeyConfigured()
        _uiState.value = _uiState.value.copy(isApiKeyConfigured = configured)
    }

    private fun loadOrCreateInitialSession() {
        viewModelScope.launch {
            val sessions = dao.getAllSessions().first()
            if (sessions.isNotEmpty()) {
                val latest = sessions.first()
                switchToSession(latest.id)
            } else {
                startNewChat()
            }
        }
    }

    fun setModel(modelId: String) {
        _uiState.value = _uiState.value.copy(selectedModelId = modelId)
        viewModelScope.launch {
            val session = ChatSessionEntity(
                id = _uiState.value.currentSessionId,
                title = "Chat",
                selectedModelId = modelId,
                searchGroundingEnabled = _uiState.value.isSearchGroundingEnabled,
                createdAt = System.currentTimeMillis()
            )
            dao.insertSession(session)
        }
    }

    fun toggleSearchGrounding() {
        _uiState.value = _uiState.value.copy(
            isSearchGroundingEnabled = !_uiState.value.isSearchGroundingEnabled
        )
    }

    fun setSelectedImage(base64: String?) {
        _uiState.value = _uiState.value.copy(selectedImageBase64 = base64)
    }

    fun setPlusMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isPlusMenuOpen = open)
    }

    fun setActiveTool(tool: ActiveToolType, param: String = "") {
        _uiState.value = _uiState.value.copy(
            activeTool = tool,
            activeToolParam = param,
            isPlusMenuOpen = false
        )
        // If web search tool chosen, toggle grounding
        if (tool == ActiveToolType.WEB_SEARCH) {
            _uiState.value = _uiState.value.copy(isSearchGroundingEnabled = true)
        }
        // If reasoning tool chosen, set to Pro model
        if (tool == ActiveToolType.REASONING) {
            _uiState.value = _uiState.value.copy(selectedModelId = AvailableModels.GEMINI_3_1_PRO.id)
        }
    }

    fun clearActiveTool() {
        _uiState.value = _uiState.value.copy(
            activeTool = ActiveToolType.NONE,
            activeToolParam = "",
            isSearchGroundingEnabled = false
        )
    }

    fun attachDocument(fileName: String, content: String) {
        _uiState.value = _uiState.value.copy(
            activeTool = ActiveToolType.DOCUMENT,
            attachedFileName = fileName,
            attachedFileContent = content,
            isPlusMenuOpen = false
        )
    }

    fun clearAttachedDocument() {
        _uiState.value = _uiState.value.copy(
            attachedFileName = null,
            attachedFileContent = null,
            activeTool = if (_uiState.value.activeTool == ActiveToolType.DOCUMENT) ActiveToolType.NONE else _uiState.value.activeTool
        )
    }

    fun startNewChat() {
        activeStreamJob?.cancel()
        val newId = UUID.randomUUID().toString()
        _uiState.value = _uiState.value.copy(
            currentSessionId = newId,
            messages = emptyList(),
            isStreaming = false,
            streamingText = "",
            streamingSources = emptyList(),
            errorMessage = null,
            selectedImageBase64 = null,
            activeTool = ActiveToolType.NONE,
            activeToolParam = "",
            attachedFileName = null,
            attachedFileContent = null
        )
        viewModelScope.launch {
            val session = ChatSessionEntity(
                id = newId,
                title = "New Chat",
                selectedModelId = _uiState.value.selectedModelId,
                searchGroundingEnabled = _uiState.value.isSearchGroundingEnabled,
                createdAt = System.currentTimeMillis()
            )
            dao.insertSession(session)
        }
    }

    fun switchToSession(sessionId: String) {
        activeStreamJob?.cancel()
        viewModelScope.launch {
            val entities = dao.getMessagesSnapshot(sessionId)
            val messages = entities.map { entity ->
                AppTypeConverters.parseExtraJson(entity)
            }
            _uiState.value = _uiState.value.copy(
                currentSessionId = sessionId,
                messages = messages,
                isStreaming = false,
                streamingText = "",
                streamingSources = emptyList(),
                errorMessage = null,
                activeTool = ActiveToolType.NONE,
                activeToolParam = "",
                attachedFileName = null,
                attachedFileContent = null
            )
        }
    }

    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        val imageBase64 = _uiState.value.selectedImageBase64
        val attachedFileName = _uiState.value.attachedFileName
        val attachedFileContent = _uiState.value.attachedFileContent
        val activeTool = _uiState.value.activeTool
        val toolParam = _uiState.value.activeToolParam

        if (trimmed.isEmpty() && imageBase64 == null && attachedFileContent == null) return
        if (_uiState.value.isStreaming) return

        // 1. Create User Message with appropriate tool tags
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = trimmed,
            imageBase64 = imageBase64,
            attachedFileName = attachedFileName,
            toolType = if (activeTool != ActiveToolType.NONE) activeTool.name else null,
            timestamp = System.currentTimeMillis()
        )

        // Clear transient inputs
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            selectedImageBase64 = null,
            attachedFileName = null,
            attachedFileContent = null,
            errorMessage = null
        )

        val currentSessionId = _uiState.value.currentSessionId
        persistMessage(userMessage, currentSessionId)

        // Reset active tool for next message
        _uiState.value = _uiState.value.copy(activeTool = ActiveToolType.NONE, activeToolParam = "")

        // 2. Route by tool
        when (activeTool) {
            ActiveToolType.IMAGE_GEN -> {
                executeInChatImageGen(trimmed, toolParam.ifEmpty { "1:1" }, imageBase64, currentSessionId)
            }
            ActiveToolType.VIDEO_GEN -> {
                executeInChatVideoGen(trimmed, currentSessionId)
            }
            ActiveToolType.MODEL_ARENA -> {
                executeInChatModelArena(trimmed, currentSessionId)
            }
            ActiveToolType.DOCUMENT -> {
                val fullDocPrompt = if (!attachedFileContent.isNullOrEmpty()) {
                    "Document Content ($attachedFileName):\n\n$attachedFileContent\n\nUser Question:\n$trimmed"
                } else trimmed
                executeStandardStreaming(fullDocPrompt, null, false, currentSessionId, "DOCUMENT")
            }
            ActiveToolType.CODING -> {
                val codingSys = "You are SASUKE AI Coding Engine. Provide production-grade, syntax-highlighted code with architectural reasoning and edge-case handling."
                executeStandardStreaming(trimmed, imageBase64, false, currentSessionId, "CODING", codingSys)
            }
            ActiveToolType.STUDY -> {
                val studySys = "You are SASUKE AI Science & STEM Professor. Provide a structured, step-by-step pedagogical scientific breakdown with equations and practice insights."
                executeStandardStreaming(trimmed, imageBase64, false, currentSessionId, "STUDY", studySys)
            }
            ActiveToolType.REASONING -> {
                val reasoningSys = "You are SASUKE AI Deep Reasoning Core (Gemini 3.1 Pro). Perform multi-step analytical deduction, verify mathematical premises, and explain the thought process."
                executeStandardStreaming(trimmed, imageBase64, false, currentSessionId, "REASONING", reasoningSys, AvailableModels.GEMINI_3_1_PRO.id)
            }
            ActiveToolType.VOICE -> {
                val voiceSys = "You are SASUKE AI in Voice Mode. Provide natural, spoken-style, engaging conversational answers suitable for audio playback."
                executeStandardStreaming(trimmed, imageBase64, false, currentSessionId, "VOICE", voiceSys)
            }
            else -> {
                val grounding = _uiState.value.isSearchGroundingEnabled
                executeStandardStreaming(trimmed, imageBase64, grounding, currentSessionId, if (grounding) "WEB_SEARCH" else null)
            }
        }
    }

    private fun executeInChatImageGen(prompt: String, aspectRatio: String, inputImageBase64: String?, sessionId: String) {
        _uiState.value = _uiState.value.copy(isStreaming = true, streamingText = "🎨 Synthesizing image with Gemini 2.5 Flash Image...")

        viewModelScope.launch {
            val response = apiClient.generateImage(
                modelId = AvailableModels.GEMINI_2_5_FLASH_IMAGE.id,
                prompt = prompt,
                aspectRatio = aspectRatio,
                inputImageBase64 = inputImageBase64
            )

            _uiState.value = _uiState.value.copy(isStreaming = false, streamingText = "")

            val assistantMsg = if (response.isError) {
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    text = "Image generation failed: ${response.errorMessage}",
                    isError = true,
                    toolType = "IMAGE_GEN"
                )
            } else {
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    text = response.text.ifEmpty { "Image generated successfully with Gemini 2.5 Flash Image ($aspectRatio)." },
                    generatedImageBase64 = response.imageBase64,
                    imageAspectRatio = aspectRatio,
                    latencyMs = response.latencyMs,
                    modelId = AvailableModels.GEMINI_2_5_FLASH_IMAGE.id,
                    toolType = "IMAGE_GEN"
                )
            }

            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + assistantMsg)
            persistMessage(assistantMsg, sessionId)
        }
    }

    private fun executeInChatVideoGen(prompt: String, sessionId: String) {
        _uiState.value = _uiState.value.copy(isStreaming = true, streamingText = "🎬 Submitting video scene to Veo 3.1 Fast engine...")

        viewModelScope.launch {
            val response = apiClient.generateVideo(
                prompt = prompt,
                aspectRatio = "16:9",
                resolution = "1080p"
            )

            _uiState.value = _uiState.value.copy(isStreaming = false, streamingText = "")

            val assistantMsg = if (response.isError) {
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    text = "Video request failed: ${response.errorMessage}",
                    isError = true,
                    toolType = "VIDEO_GEN"
                )
            } else {
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    text = response.text,
                    videoStatus = "PROCESSING",
                    videoOperationId = response.text.substringAfter("Operation ID: ").substringBefore("\n"),
                    latencyMs = response.latencyMs,
                    modelId = AvailableModels.VEO_3_1_FAST.id,
                    toolType = "VIDEO_GEN"
                )
            }

            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + assistantMsg)
            persistMessage(assistantMsg, sessionId)
        }
    }

    private fun executeInChatModelArena(prompt: String, sessionId: String) {
        _uiState.value = _uiState.value.copy(isStreaming = true, streamingText = "⚔️ Running concurrent Model Battle: Gemini 3.5 Flash vs Gemini 3.1 Pro...")

        val modelA = AvailableModels.GEMINI_3_5_FLASH.id
        val modelB = AvailableModels.GEMINI_3_1_PRO.id

        viewModelScope.launch {
            val defA = async { apiClient.generateContent(modelId = modelA, prompt = prompt) }
            val defB = async { apiClient.generateContent(modelId = modelB, prompt = prompt) }

            val resA = defA.await()
            val resB = defB.await()

            _uiState.value = _uiState.value.copy(isStreaming = false, streamingText = "")

            val assistantMsg = ChatMessage(
                role = MessageRole.ASSISTANT,
                text = "Model Battle Comparison Result",
                toolType = "ARENA",
                comparisonModelA = modelA,
                comparisonModelB = modelB,
                comparisonResponseA = if (resA.isError) "Error: ${resA.errorMessage}" else resA.text,
                comparisonResponseB = if (resB.isError) "Error: ${resB.errorMessage}" else resB.text,
                latencyA = resA.latencyMs,
                latencyB = resB.latencyMs
            )

            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + assistantMsg)
            persistMessage(assistantMsg, sessionId)
        }
    }

    fun checkVideoStatusInChat(messageId: String, operationId: String) {
        viewModelScope.launch {
            val res = apiClient.checkVideoOperation(operationId)
            val updated = _uiState.value.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(text = "${msg.text}\n\n[Status Update]: ${if (res.isError) res.errorMessage else res.text}")
                } else msg
            }
            _uiState.value = _uiState.value.copy(messages = updated)
            updated.find { it.id == messageId }?.let { persistMessage(it, _uiState.value.currentSessionId) }
        }
    }

    fun rateArenaInChat(messageId: String, isA: Boolean, rating: Int) {
        val updated = _uiState.value.messages.map { msg ->
            if (msg.id == messageId) {
                if (isA) msg.copy(ratingA = rating) else msg.copy(ratingB = rating)
            } else msg
        }
        _uiState.value = _uiState.value.copy(messages = updated)
        updated.find { it.id == messageId }?.let { persistMessage(it, _uiState.value.currentSessionId) }
    }

    fun chooseArenaWinnerInChat(messageId: String, winner: String) {
        val updated = _uiState.value.messages.map { msg ->
            if (msg.id == messageId) {
                msg.copy(winner = winner)
            } else msg
        }
        _uiState.value = _uiState.value.copy(messages = updated)
        updated.find { it.id == messageId }?.let { persistMessage(it, _uiState.value.currentSessionId) }
    }

    private fun executeStandardStreaming(
        prompt: String,
        userImageBase64: String?,
        enableGrounding: Boolean,
        sessionId: String,
        toolType: String? = null,
        customSys: String? = null,
        modelOverride: String? = null
    ) {
        val modelId = modelOverride ?: _uiState.value.selectedModelId

        _uiState.value = _uiState.value.copy(
            isStreaming = true,
            streamingText = "",
            streamingSources = emptyList()
        )

        val historyPayload = _uiState.value.messages.map { msg ->
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            role to msg.text
        }

        val systemInstruction = customSys ?: "You are SASUKE AI, an elite intelligent assistant. Provide accurate, structured, insightful answers with clear markdown formatting, syntax-highlighted code blocks, and multilingual fluency in Arabic, French, and English."

        activeStreamJob = viewModelScope.launch {
            apiClient.streamGenerateContent(
                modelId = modelId,
                messages = historyPayload,
                userImageBase64 = userImageBase64,
                enableSearchGrounding = enableGrounding,
                systemInstruction = systemInstruction
            ).collect { event ->
                when (event) {
                    is GeminiStreamEvent.Chunk -> {
                        _uiState.value = _uiState.value.copy(
                            streamingText = _uiState.value.streamingText + event.text
                        )
                    }
                    is GeminiStreamEvent.Sources -> {
                        _uiState.value = _uiState.value.copy(
                            streamingSources = event.list
                        )
                    }
                    is GeminiStreamEvent.Done -> {
                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            text = event.fullText,
                            modelId = modelId,
                            latencyMs = event.totalLatencyMs,
                            sources = _uiState.value.streamingSources,
                            toolType = toolType,
                            timestamp = System.currentTimeMillis()
                        )
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistantMessage,
                            isStreaming = false,
                            streamingText = "",
                            streamingSources = emptyList()
                        )
                        persistMessage(assistantMessage, sessionId)
                    }
                    is GeminiStreamEvent.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            errorMessage = event.message
                        )
                    }
                }
            }
        }
    }

    private fun persistMessage(msg: ChatMessage, sessionId: String) {
        viewModelScope.launch {
            val extraJson = AppTypeConverters.extraToJson(msg)
            dao.insertMessage(
                ChatMessageEntity(
                    id = msg.id,
                    sessionId = sessionId,
                    role = msg.role,
                    text = msg.text,
                    modelId = msg.modelId,
                    timestamp = msg.timestamp,
                    imageBase64 = msg.imageBase64,
                    latencyMs = msg.latencyMs,
                    sourcesJson = AppTypeConverters.sourcesToJson(msg.sources),
                    extraJson = extraJson
                )
            )

            // Update title if needed
            if (_uiState.value.messages.size <= 2 && msg.role == MessageRole.USER) {
                val title = if (msg.text.length > 28) msg.text.take(28) + "..." else msg.text
                dao.insertSession(
                    ChatSessionEntity(
                        id = sessionId,
                        title = title.ifEmpty { "Chat" },
                        selectedModelId = _uiState.value.selectedModelId,
                        searchGroundingEnabled = _uiState.value.isSearchGroundingEnabled,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun regenerateLast() {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER } ?: return
        if (_uiState.value.messages.lastOrNull()?.role == MessageRole.ASSISTANT) {
            val trimmedList = _uiState.value.messages.dropLast(1)
            _uiState.value = _uiState.value.copy(messages = trimmedList)
        }
        sendMessage(lastUserMessage.text)
    }

    fun deleteCurrentSession() {
        val sessionId = _uiState.value.currentSessionId
        viewModelScope.launch {
            dao.deleteMessagesForSession(sessionId)
            dao.deleteSession(sessionId)
            startNewChat()
        }
    }
}
