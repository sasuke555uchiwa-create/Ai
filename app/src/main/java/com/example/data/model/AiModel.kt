package com.example.data.model

enum class ModelCapability {
    TEXT,
    CODE,
    VISION,
    GROUNDING,
    IMAGE_GEN,
    VIDEO_GEN,
    REASONING
}

data class AiModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val capabilities: List<ModelCapability>,
    val contextWindow: String,
    val speedRating: String, // "Fast", "Ultra-Fast", "Deep"
    val isDefault: Boolean = false,
    val provider: String = "Google Gemini"
)

object AvailableModels {
    val GEMINI_3_5_FLASH = AiModelInfo(
        id = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        description = "Balanced intelligence, ultra-fast streaming, code, vision, and web grounding.",
        capabilities = listOf(
            ModelCapability.TEXT,
            ModelCapability.CODE,
            ModelCapability.VISION,
            ModelCapability.GROUNDING
        ),
        contextWindow = "1M tokens",
        speedRating = "Ultra-Fast",
        isDefault = true
    )

    val GEMINI_3_1_PRO = AiModelInfo(
        id = "gemini-3.1-pro-preview",
        displayName = "Gemini 3.1 Pro Preview",
        description = "Advanced reasoning, complex science & STEM analysis, math, and code architecture.",
        capabilities = listOf(
            ModelCapability.TEXT,
            ModelCapability.CODE,
            ModelCapability.VISION,
            ModelCapability.REASONING,
            ModelCapability.GROUNDING
        ),
        contextWindow = "2M tokens",
        speedRating = "Deep Reasoning"
    )

    val GEMINI_3_1_FLASH_LITE = AiModelInfo(
        id = "gemini-3.1-flash-lite-preview",
        displayName = "Gemini 3.1 Flash Lite",
        description = "Lightweight, lowest-latency model for rapid mobile chat and quick lookups.",
        capabilities = listOf(
            ModelCapability.TEXT,
            ModelCapability.CODE
        ),
        contextWindow = "500k tokens",
        speedRating = "Instant"
    )

    val GEMINI_2_5_FLASH_IMAGE = AiModelInfo(
        id = "gemini-2.5-flash-image",
        displayName = "Gemini 2.5 Flash Image",
        description = "Native multimodal image generation and conversational image editing.",
        capabilities = listOf(
            ModelCapability.TEXT,
            ModelCapability.IMAGE_GEN
        ),
        contextWindow = "Standard",
        speedRating = "Fast"
    )

    val GEMINI_NANO_BANANA = AiModelInfo(
        id = "gemini-nano-banana-2.1",
        displayName = "Nano Banana 2.1",
        description = "High-fidelity photorealistic image synthesis supporting 1K, 2K, and 4K outputs.",
        capabilities = listOf(
            ModelCapability.IMAGE_GEN
        ),
        contextWindow = "N/A",
        speedRating = "High-Res"
    )

    val VEO_3_1_FAST = AiModelInfo(
        id = "veo-3.1-fast-generate-preview",
        displayName = "Veo 3.1 Fast Generate",
        description = "Google Veo generative video model for text-to-video and image-to-video animation.",
        capabilities = listOf(
            ModelCapability.VIDEO_GEN
        ),
        contextWindow = "Video",
        speedRating = "Async Render"
    )

    val textChatModels = listOf(
        GEMINI_3_5_FLASH,
        GEMINI_3_1_PRO,
        GEMINI_3_1_FLASH_LITE
    )

    val imageModels = listOf(
        GEMINI_2_5_FLASH_IMAGE,
        GEMINI_NANO_BANANA
    )

    val videoModels = listOf(
        VEO_3_1_FAST
    )

    val allModels = listOf(
        GEMINI_3_5_FLASH,
        GEMINI_3_1_PRO,
        GEMINI_3_1_FLASH_LITE,
        GEMINI_2_5_FLASH_IMAGE,
        GEMINI_NANO_BANANA,
        VEO_3_1_FAST
    )
}
