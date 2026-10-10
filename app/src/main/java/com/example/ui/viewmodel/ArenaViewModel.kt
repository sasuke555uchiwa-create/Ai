package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ArenaRecordEntity
import com.example.data.model.ArenaResult
import com.example.data.model.AvailableModels
import com.example.data.remote.GeminiApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ArenaUiState(
    val modelAId: String = AvailableModels.GEMINI_3_5_FLASH.id,
    val modelBId: String = AvailableModels.GEMINI_3_1_PRO.id,
    val prompt: String = "",
    val category: String = "General",
    val isRunning: Boolean = false,
    val responseA: String = "",
    val responseB: String = "",
    val latencyMsA: Long = 0,
    val latencyMsB: Long = 0,
    val errorA: String? = null,
    val errorB: String? = null,
    val ratingA: Int = 0,
    val ratingB: Int = 0,
    val winner: String? = null,
    val isSaved: Boolean = false,
    val savedRecords: List<ArenaResult> = emptyList()
)

data class BenchmarkPreset(
    val title: String,
    val category: String,
    val prompt: String
)

class ArenaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.appDao()
    private val apiClient = GeminiApiClient(application)

    private val _uiState = MutableStateFlow(ArenaUiState())
    val uiState: StateFlow<ArenaUiState> = _uiState.asStateFlow()

    val benchmarkPresets = listOf(
        BenchmarkPreset(
            title = "Coding: High-Performance LRU Cache",
            category = "Coding",
            prompt = "Write a complete, thread-safe, high-performance LRU Cache in Kotlin with generic Key and Value types. Explain its time and space complexities."
        ),
        BenchmarkPreset(
            title = "Science: Quantum Entanglement & Decoherence",
            category = "Science",
            prompt = "Explain the quantum mechanical principles behind EPR entanglement and quantum decoherence. How does environmental entanglement lead to apparent classical behavior?"
        ),
        BenchmarkPreset(
            title = "Mathematics: Definite Integration Step-by-Step",
            category = "Mathematics",
            prompt = "Calculate the exact value of the integral from 0 to infinity of x^3 * e^(-2x) dx using integration by parts and gamma function relations."
        ),
        BenchmarkPreset(
            title = "Multilingual: Arabic & French Translation",
            category = "Multilingual",
            prompt = "Translate the following poetic sentence into fluent classical Arabic (Fusha) and elegant French, preserving rhythmic tone: 'Knowledge illuminates the shadows of uncertainty like lightning breaking through storm clouds.'"
        ),
        BenchmarkPreset(
            title = "Logical Reasoning: Multi-Step Riddle",
            category = "Reasoning",
            prompt = "Three boxes are labeled Apples, Oranges, and Mixed. Every label is incorrect. You can pick only one fruit from one box without looking inside. How do you correctly label all three boxes?"
        )
    )

    init {
        loadSavedRecords()
    }

    private fun loadSavedRecords() {
        viewModelScope.launch {
            dao.getAllArenaRecords().collect { records ->
                _uiState.value = _uiState.value.copy(
                    savedRecords = records.map {
                        ArenaResult(
                            id = it.id,
                            prompt = it.prompt,
                            category = it.category,
                            modelAId = it.modelAId,
                            modelBId = it.modelBId,
                            responseA = it.responseA,
                            responseB = it.responseB,
                            latencyMsA = it.latencyMsA,
                            latencyMsB = it.latencyMsB,
                            ratingA = it.ratingA,
                            ratingB = it.ratingB,
                            winner = it.winner,
                            timestamp = it.timestamp
                        )
                    }
                )
            }
        }
    }

    fun setModelA(id: String) {
        _uiState.value = _uiState.value.copy(modelAId = id)
    }

    fun setModelB(id: String) {
        _uiState.value = _uiState.value.copy(modelBId = id)
    }

    fun setPrompt(text: String) {
        _uiState.value = _uiState.value.copy(prompt = text)
    }

    fun setCategory(cat: String) {
        _uiState.value = _uiState.value.copy(category = cat)
    }

    fun setRatingA(rating: Int) {
        _uiState.value = _uiState.value.copy(ratingA = rating)
    }

    fun setRatingB(rating: Int) {
        _uiState.value = _uiState.value.copy(ratingB = rating)
    }

    fun setWinner(winner: String) {
        _uiState.value = _uiState.value.copy(winner = winner)
    }

    fun applyBenchmark(preset: BenchmarkPreset) {
        _uiState.value = _uiState.value.copy(
            prompt = preset.prompt,
            category = preset.category
        )
    }

    fun runBattle() {
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isEmpty() || _uiState.value.isRunning) return

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            responseA = "",
            responseB = "",
            latencyMsA = 0,
            latencyMsB = 0,
            errorA = null,
            errorB = null,
            ratingA = 0,
            ratingB = 0,
            winner = null,
            isSaved = false
        )

        val modelA = _uiState.value.modelAId
        val modelB = _uiState.value.modelBId

        viewModelScope.launch {
            val deferredA = async {
                apiClient.generateContent(modelId = modelA, prompt = prompt)
            }
            val deferredB = async {
                apiClient.generateContent(modelId = modelB, prompt = prompt)
            }

            val resA = deferredA.await()
            val resB = deferredB.await()

            _uiState.value = _uiState.value.copy(
                isRunning = false,
                responseA = resA.text,
                latencyMsA = resA.latencyMs,
                errorA = if (resA.isError) resA.errorMessage else null,
                responseB = resB.text,
                latencyMsB = resB.latencyMs,
                errorB = if (resB.isError) resB.errorMessage else null
            )
        }
    }

    fun saveComparison() {
        val s = _uiState.value
        if (s.responseA.isEmpty() && s.responseB.isEmpty()) return

        val id = UUID.randomUUID().toString()
        val record = ArenaRecordEntity(
            id = id,
            prompt = s.prompt,
            category = s.category,
            modelAId = s.modelAId,
            modelBId = s.modelBId,
            responseA = s.responseA,
            responseB = s.responseB,
            latencyMsA = s.latencyMsA,
            latencyMsB = s.latencyMsB,
            ratingA = s.ratingA,
            ratingB = s.ratingB,
            winner = s.winner,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            dao.insertArenaRecord(record)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun deleteArenaRecord(id: String) {
        viewModelScope.launch {
            dao.deleteArenaRecord(id)
        }
    }
}
