package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SavedImageEntity
import com.example.data.model.AvailableModels
import com.example.data.model.GeneratedImageRecord
import com.example.data.model.GeneratedVideoRecord
import com.example.data.remote.GeminiApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ImageStudioUiState(
    val prompt: String = "",
    val selectedModelId: String = AvailableModels.GEMINI_2_5_FLASH_IMAGE.id,
    val selectedAspectRatio: String = "1:1",
    val selectedStyle: String = "Cyberpunk / Anime",
    val inputImageBase64: String? = null,
    val isGenerating: Boolean = false,
    val currentImageBase64: String? = null,
    val currentImageDescription: String? = null,
    val latencyMs: Long = 0,
    val errorMessage: String? = null,
    val history: List<GeneratedImageRecord> = emptyList()
)

data class VideoStudioUiState(
    val prompt: String = "",
    val resolution: String = "1080p",
    val aspectRatio: String = "16:9",
    val isGenerating: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val history: List<GeneratedVideoRecord> = emptyList()
)

class MediaStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.appDao()
    private val apiClient = GeminiApiClient(application)

    private val _imageState = MutableStateFlow(ImageStudioUiState())
    val imageState: StateFlow<ImageStudioUiState> = _imageState.asStateFlow()

    private val _videoState = MutableStateFlow(VideoStudioUiState())
    val videoState: StateFlow<VideoStudioUiState> = _videoState.asStateFlow()

    val stylePresets = listOf(
        "Cyberpunk / Anime",
        "Photorealistic 8K",
        "Dark Fantasy Concept",
        "Sci-Fi Holographic",
        "Minimalist Vector",
        "Digital Matte Painting"
    )

    val aspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4")

    init {
        loadSavedImages()
    }

    private fun loadSavedImages() {
        viewModelScope.launch {
            dao.getAllSavedImages().collect { images ->
                _imageState.value = _imageState.value.copy(
                    history = images.map {
                        GeneratedImageRecord(
                            id = it.id,
                            prompt = it.prompt,
                            style = it.style,
                            aspectRatio = it.aspectRatio,
                            imageBase64 = it.imageBase64,
                            timestamp = it.timestamp
                        )
                    }
                )
            }
        }
    }

    fun setImagePrompt(text: String) {
        _imageState.value = _imageState.value.copy(prompt = text)
    }

    fun setImageModel(modelId: String) {
        _imageState.value = _imageState.value.copy(selectedModelId = modelId)
    }

    fun setAspectRatio(ratio: String) {
        _imageState.value = _imageState.value.copy(selectedAspectRatio = ratio)
    }

    fun setStyle(style: String) {
        _imageState.value = _imageState.value.copy(selectedStyle = style)
    }

    fun setInputImage(base64: String?) {
        _imageState.value = _imageState.value.copy(inputImageBase64 = base64)
    }

    fun generateImage() {
        val prompt = _imageState.value.prompt.trim()
        if (prompt.isEmpty() || _imageState.value.isGenerating) return

        val fullPrompt = if (_imageState.value.selectedStyle.isNotEmpty()) {
            "$prompt, ${_imageState.value.selectedStyle} style, high detail, masterpiece"
        } else prompt

        _imageState.value = _imageState.value.copy(
            isGenerating = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val response = apiClient.generateImage(
                modelId = _imageState.value.selectedModelId,
                prompt = fullPrompt,
                aspectRatio = _imageState.value.selectedAspectRatio,
                inputImageBase64 = _imageState.value.inputImageBase64
            )

            if (response.isError) {
                _imageState.value = _imageState.value.copy(
                    isGenerating = false,
                    errorMessage = response.errorMessage
                )
            } else {
                val base64 = response.imageBase64
                if (base64 != null) {
                    val id = UUID.randomUUID().toString()
                    val record = SavedImageEntity(
                        id = id,
                        prompt = prompt,
                        style = _imageState.value.selectedStyle,
                        aspectRatio = _imageState.value.selectedAspectRatio,
                        imageBase64 = base64,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertSavedImage(record)

                    _imageState.value = _imageState.value.copy(
                        isGenerating = false,
                        currentImageBase64 = base64,
                        currentImageDescription = response.text,
                        latencyMs = response.latencyMs,
                        errorMessage = null
                    )
                } else {
                    _imageState.value = _imageState.value.copy(
                        isGenerating = false,
                        errorMessage = "No image data was generated by the model. Try a different prompt or model."
                    )
                }
            }
        }
    }

    fun setVideoPrompt(text: String) {
        _videoState.value = _videoState.value.copy(prompt = text)
    }

    fun setVideoResolution(res: String) {
        _videoState.value = _videoState.value.copy(resolution = res)
    }

    fun setVideoAspectRatio(aspect: String) {
        _videoState.value = _videoState.value.copy(aspectRatio = aspect)
    }

    fun generateVideo() {
        val prompt = _videoState.value.prompt.trim()
        if (prompt.isEmpty() || _videoState.value.isGenerating) return

        _videoState.value = _videoState.value.copy(
            isGenerating = true,
            errorMessage = null,
            statusMessage = null
        )

        viewModelScope.launch {
            val response = apiClient.generateVideo(
                prompt = prompt,
                aspectRatio = _videoState.value.aspectRatio,
                resolution = _videoState.value.resolution
            )

            if (response.isError) {
                _videoState.value = _videoState.value.copy(
                    isGenerating = false,
                    errorMessage = response.errorMessage
                )
            } else {
                val newRecord = GeneratedVideoRecord(
                    prompt = prompt,
                    resolution = _videoState.value.resolution,
                    status = "SUBMITTED",
                    message = response.text
                )
                _videoState.value = _videoState.value.copy(
                    isGenerating = false,
                    statusMessage = response.text,
                    history = listOf(newRecord) + _videoState.value.history
                )
            }
        }
    }
}
