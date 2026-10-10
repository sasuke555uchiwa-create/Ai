package com.example.data.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class ActiveToolType(val id: String) {
    NONE("none"),
    IMAGE_GEN("image_gen"),
    VIDEO_GEN("video_gen"),
    WEB_SEARCH("web_search"),
    DOCUMENT("document"),
    ADD_IMAGE("add_image"),
    MODEL_ARENA("model_arena"),
    CODING("coding"),
    STUDY("study"),
    VOICE("voice"),
    REASONING("reasoning")
}

data class WebSource(
    val title: String,
    val url: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val modelId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val latencyMs: Long? = null,
    val sources: List<WebSource> = emptyList(),
    // In-Chat Tool Extensions
    val toolType: String? = null,
    val generatedImageBase64: String? = null,
    val imageAspectRatio: String? = null,
    val videoStatus: String? = null,
    val videoOperationId: String? = null,
    val attachedFileName: String? = null,
    val comparisonModelA: String? = null,
    val comparisonModelB: String? = null,
    val comparisonResponseA: String? = null,
    val comparisonResponseB: String? = null,
    val latencyA: Long? = null,
    val latencyB: Long? = null,
    val ratingA: Int = 0,
    val ratingB: Int = 0,
    val winner: String? = null
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Conversation",
    val messages: List<ChatMessage> = emptyList(),
    val selectedModelId: String = AvailableModels.GEMINI_3_5_FLASH.id,
    val searchGroundingEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ArenaResult(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val category: String,
    val modelAId: String,
    val modelBId: String,
    val responseA: String,
    val responseB: String,
    val latencyMsA: Long,
    val latencyMsB: Long,
    val ratingA: Int = 0, // 0 to 5
    val ratingB: Int = 0, // 0 to 5
    val winner: String? = null, // "A", "B", "TIE"
    val timestamp: Long = System.currentTimeMillis()
)

data class GeneratedImageRecord(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val style: String,
    val aspectRatio: String,
    val imageBase64: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GeneratedVideoRecord(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val resolution: String,
    val status: String, // "REQUESTED", "PROCESSING", "READY", "FAILED"
    val operationId: String? = null,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
