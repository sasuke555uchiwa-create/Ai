package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.AppTypeConverters
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.AvailableModels
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.WebSource
import com.example.data.remote.GeminiApiClient
import com.example.data.remote.GeminiStreamEvent
import kotlinx.coroutines.Job
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
    val isApiKeyConfigured: Boolean = true
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
            selectedImageBase64 = null
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
            val messages = entities.map {
                ChatMessage(
                    id = it.id,
                    role = it.role,
                    text = it.text,
                    modelId = it.modelId,
                    timestamp = it.timestamp,
                    imageBase64 = it.imageBase64,
                    latencyMs = it.latencyMs,
                    sources = AppTypeConverters.jsonToSources(it.sourcesJson)
                )
            }
            _uiState.value = _uiState.value.copy(
                currentSessionId = sessionId,
                messages = messages,
                isStreaming = false,
                streamingText = "",
                streamingSources = emptyList(),
                errorMessage = null
            )
        }
    }

    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        val imageBase64 = _uiState.value.selectedImageBase64
        if (trimmed.isEmpty() && imageBase64 == null) return
        if (_uiState.value.isStreaming) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = trimmed,
            imageBase64 = imageBase64,
            timestamp = System.currentTimeMillis()
        )

        // Clear image selection
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            selectedImageBase64 = null,
            errorMessage = null,
            isStreaming = true,
            streamingText = "",
            streamingSources = emptyList()
        )

        val currentSessionId = _uiState.value.currentSessionId
        val modelId = _uiState.value.selectedModelId
        val grounding = _uiState.value.isSearchGroundingEnabled

        // Persist user message
        viewModelScope.launch {
            dao.insertMessage(
                ChatMessageEntity(
                    id = userMessage.id,
                    sessionId = currentSessionId,
                    role = userMessage.role,
                    text = userMessage.text,
                    modelId = null,
                    timestamp = userMessage.timestamp,
                    imageBase64 = userMessage.imageBase64,
                    latencyMs = null,
                    sourcesJson = "[]"
                )
            )

            // Update session title if first message
            if (_uiState.value.messages.size == 1) {
                val title = if (trimmed.length > 28) trimmed.take(28) + "..." else trimmed
                dao.insertSession(
                    ChatSessionEntity(
                        id = currentSessionId,
                        title = title,
                        selectedModelId = modelId,
                        searchGroundingEnabled = grounding,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // Build history payload for Gemini
        val historyPayload = _uiState.value.messages.map { msg ->
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            role to msg.text
        }

        val systemInstruction = "You are SASUKE AI, a premier elite multi-modal intelligent assistant. Provide accurate, structured, insightful answers with clear markdown formatting, syntax-highlighted code blocks, and multilingual fluency in Arabic, French, and English."

        activeStreamJob = viewModelScope.launch {
            apiClient.streamGenerateContent(
                modelId = modelId,
                messages = historyPayload,
                userImageBase64 = userMessage.imageBase64,
                enableSearchGrounding = grounding,
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
                            timestamp = System.currentTimeMillis()
                        )
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistantMessage,
                            isStreaming = false,
                            streamingText = "",
                            streamingSources = emptyList()
                        )
                        // Persist assistant message
                        dao.insertMessage(
                            ChatMessageEntity(
                                id = assistantMessage.id,
                                sessionId = currentSessionId,
                                role = assistantMessage.role,
                                text = assistantMessage.text,
                                modelId = assistantMessage.modelId,
                                timestamp = assistantMessage.timestamp,
                                imageBase64 = null,
                                latencyMs = assistantMessage.latencyMs,
                                sourcesJson = AppTypeConverters.sourcesToJson(assistantMessage.sources)
                            )
                        )
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

    fun regenerateLast() {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER } ?: return
        // Remove trailing assistant message if present
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
