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
import com.example.data.model.FirestoreChatMessage
import com.example.data.model.FirestoreChatSession
import com.example.data.remote.FirestoreChatRepository
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

data class ChatUiState(
    val currentSessionId: String = UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingSources: List<WebSource> = emptyList(),
    val selectedModelId: String = AvailableModels.GEMINI_3_5_FLASH.id,
    val isSearchGroundingEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isApiKeyConfigured: Boolean = true,
    // Plus Tools Menu State
    val isPlusMenuOpen: Boolean = false,
    val activeTool: ActiveToolType = ActiveToolType.NONE,
    val activeToolParam: String = "",
    // Staged Attachments
    val attachedImageBase64: String? = null,
    val attachedImageName: String? = null,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val attachedPdfBase64: String? = null,
    val attachedFileSizeFormatted: String? = null,
    val isPdfFile: Boolean = false
) {
    val selectedImageBase64: String? get() = attachedImageBase64
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.appDao()
    val apiClient = GeminiApiClient(application)
    private val firestoreRepo = lazy { FirestoreChatRepository(application) }

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

    fun setSelectedImage(base64: String?, name: String? = "photo.jpg") {
        _uiState.value = _uiState.value.copy(
            attachedImageBase64 = base64,
            attachedImageName = if (base64 != null) (name ?: "photo.jpg") else null
        )
    }

    fun removeAttachedImage() {
        _uiState.value = _uiState.value.copy(
            attachedImageBase64 = null,
            attachedImageName = null
        )
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
        // If reasoning or advanced research chosen, set to Pro model
        if (tool == ActiveToolType.REASONING || tool == ActiveToolType.ADVANCED_RESEARCH) {
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

    fun attachTextDocument(fileName: String, content: String, sizeFormatted: String = "") {
        _uiState.value = _uiState.value.copy(
            attachedFileName = fileName,
            attachedFileContent = content,
            attachedPdfBase64 = null,
            attachedFileSizeFormatted = sizeFormatted,
            isPdfFile = false,
            isPlusMenuOpen = false
        )
    }

    fun attachPdfDocument(fileName: String, base64: String, sizeFormatted: String = "") {
        _uiState.value = _uiState.value.copy(
            attachedFileName = fileName,
            attachedFileContent = null,
            attachedPdfBase64 = base64,
            attachedFileSizeFormatted = sizeFormatted,
            isPdfFile = true,
            isPlusMenuOpen = false
        )
    }

    fun attachDocument(fileName: String, content: String) {
        attachTextDocument(fileName, content)
    }

    fun removeAttachedDocument() {
        _uiState.value = _uiState.value.copy(
            attachedFileName = null,
            attachedFileContent = null,
            attachedPdfBase64 = null,
            attachedFileSizeFormatted = null,
            isPdfFile = false
        )
    }

    fun clearAttachedDocument() {
        removeAttachedDocument()
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
            attachedImageBase64 = null,
            attachedImageName = null,
            attachedFileName = null,
            attachedFileContent = null,
            attachedPdfBase64 = null,
            attachedFileSizeFormatted = null,
            isPdfFile = false,
            activeTool = ActiveToolType.NONE,
            activeToolParam = ""
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
                attachedImageBase64 = null,
                attachedImageName = null,
                attachedFileName = null,
                attachedFileContent = null,
                attachedPdfBase64 = null,
                attachedFileSizeFormatted = null,
                isPdfFile = false,
                activeTool = ActiveToolType.NONE,
                activeToolParam = ""
            )
        }
    }

    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        val imageBase64 = _uiState.value.attachedImageBase64
        val attachedFileName = _uiState.value.attachedFileName
        val attachedFileContent = _uiState.value.attachedFileContent
        val attachedPdfBase64 = _uiState.value.attachedPdfBase64
        val activeTool = _uiState.value.activeTool
        val toolParam = _uiState.value.activeToolParam

        if (trimmed.isEmpty() && imageBase64 == null && attachedFileContent == null && attachedPdfBase64 == null) return
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
            attachedImageBase64 = null,
            attachedImageName = null,
            attachedFileName = null,
            attachedFileContent = null,
            attachedPdfBase64 = null,
            attachedFileSizeFormatted = null,
            isPdfFile = false,
            errorMessage = null,
            activeTool = ActiveToolType.NONE,
            activeToolParam = ""
        )

        val currentSessionId = _uiState.value.currentSessionId
        persistMessage(userMessage, currentSessionId)

        // 2. Prepare combined prompt if text document attached
        val effectivePrompt = if (!attachedFileContent.isNullOrEmpty()) {
            "Attached File ($attachedFileName):\n```\n$attachedFileContent\n```\n\nUser Question:\n$trimmed"
        } else trimmed

        // 3. Route by tool
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
            ActiveToolType.CODING -> {
                val codingSys = "You are SASUKE AI Coding Engine. Provide production-grade, syntax-highlighted code with architectural reasoning and edge-case handling in modern languages (Kotlin, Python, TypeScript, etc.)."
                executeStandardStreaming(effectivePrompt, imageBase64, attachedPdfBase64, false, currentSessionId, "CODING", codingSys)
            }
            ActiveToolType.STUDY -> {
                val studySys = "You are SASUKE AI Science & STEM Professor. Provide a structured, step-by-step pedagogical scientific breakdown with equations, principles, and practice insights."
                executeStandardStreaming(effectivePrompt, imageBase64, attachedPdfBase64, false, currentSessionId, "STUDY", studySys)
            }
            ActiveToolType.REASONING, ActiveToolType.ADVANCED_RESEARCH -> {
                val reasoningSys = "You are SASUKE AI Advanced Research & Deep Reasoning Core (powered by Gemini 3.1 Pro). Perform multi-step analytical deduction, verify mathematical premises, cross-examine hypotheses, and detail the complete analytical deduction process."
                executeStandardStreaming(effectivePrompt, imageBase64, attachedPdfBase64, false, currentSessionId, "ADVANCED_RESEARCH", reasoningSys, AvailableModels.GEMINI_3_1_PRO.id)
            }
            ActiveToolType.VOICE -> {
                val voiceSys = "You are SASUKE AI in Voice Mode. Provide natural, spoken-style, engaging conversational answers suitable for audio playback."
                executeStandardStreaming(effectivePrompt, imageBase64, attachedPdfBase64, false, currentSessionId, "VOICE", voiceSys)
            }
            ActiveToolType.DOCUMENT -> {
                executeStandardStreaming(effectivePrompt, imageBase64, attachedPdfBase64, false, currentSessionId, "DOCUMENT")
            }
            else -> {
                val grounding = _uiState.value.isSearchGroundingEnabled || activeTool == ActiveToolType.WEB_SEARCH
                executeStandardStreaming(effectivePrompt, imageBase64, attachedPdfBase64, grounding, currentSessionId, if (grounding) "WEB_SEARCH" else null)
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
        userPdfBase64: String? = null,
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
                userPdfBase64 = userPdfBase64,
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

            // Dual persistence: sync to Cloud Firestore when user is authenticated
            try {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUid != null) {
                    val sessionTitle = if (msg.text.length > 28) msg.text.take(28) + "..." else "Chat"
                    firestoreRepo.value.saveSession(
                        userId = currentUid,
                        session = FirestoreChatSession(
                            id = sessionId,
                            userId = currentUid,
                            title = sessionTitle,
                            model = _uiState.value.selectedModelId
                        )
                    )
                    firestoreRepo.value.saveMessage(
                        userId = currentUid,
                        sessionId = sessionId,
                        message = FirestoreChatMessage(
                            id = msg.id,
                            sessionId = sessionId,
                            userId = currentUid,
                            sender = if (msg.role == MessageRole.USER) "user" else "model",
                            content = msg.text,
                            modelUsed = msg.modelId
                        )
                    )
                }
            } catch (e: Exception) {
                // Silently ignore if offline or unauthenticated
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
            try {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUid != null) {
                    firestoreRepo.value.deleteSession(currentUid, sessionId)
                }
            } catch (e: Exception) {
                // Non-fatal if offline
            }
            startNewChat()
        }
    }
}
