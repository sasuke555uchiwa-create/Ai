package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AvailableModels
import com.example.data.remote.GeminiApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CodingUiState(
    val inputCode: String = "",
    val language: String = "Kotlin",
    val mode: String = "Explain", // Explain, Generate, Debug, Review, Unit Tests
    val isRunning: Boolean = false,
    val outputResult: String = "",
    val latencyMs: Long = 0,
    val errorMessage: String? = null
)

data class StudyUiState(
    val topic: String = "",
    val discipline: String = "Physics", // Physics, Chemistry, Biology, Astronomy, Quantum, Neuroscience
    val difficulty: String = "Intermediate", // Beginner, Intermediate, Advanced
    val languageOutput: String = "English", // English, French, Arabic
    val isRunning: Boolean = false,
    val explanationResult: String = "",
    val quizResult: String = "",
    val latencyMs: Long = 0,
    val errorMessage: String? = null
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = GeminiApiClient(application)

    private val _codingState = MutableStateFlow(CodingUiState())
    val codingState: StateFlow<CodingUiState> = _codingState.asStateFlow()

    private val _studyState = MutableStateFlow(StudyUiState())
    val studyState: StateFlow<StudyUiState> = _studyState.asStateFlow()

    val codingModes = listOf("Explain", "Generate", "Debug & Fix", "Code Review", "Unit Tests")
    val codingLanguages = listOf("Kotlin", "Python", "TypeScript/JS", "Rust", "C++", "SQL", "Go")

    val scienceDisciplines = listOf(
        "Physics",
        "Chemistry",
        "Biology",
        "Astronomy & Cosmology",
        "Quantum Computing",
        "Neuroscience & Bioengineering"
    )

    val difficultyLevels = listOf("Beginner", "Intermediate", "Advanced / University")

    fun setCodeInput(code: String) {
        _codingState.value = _codingState.value.copy(inputCode = code)
    }

    fun setCodingLanguage(lang: String) {
        _codingState.value = _codingState.value.copy(language = lang)
    }

    fun setCodingMode(mode: String) {
        _codingState.value = _codingState.value.copy(mode = mode)
    }

    fun runCodingTask() {
        val input = _codingState.value.inputCode.trim()
        if (input.isEmpty() || _codingState.value.isRunning) return

        _codingState.value = _codingState.value.copy(
            isRunning = true,
            outputResult = "",
            errorMessage = null
        )

        val lang = _codingState.value.language
        val mode = _codingState.value.mode

        val prompt = when (mode) {
            "Explain" -> "Analyze and explain the following $lang code in clear detail, breaking down key patterns, time/space complexity, and architecture:\n\n```$lang\n$input\n```"
            "Generate" -> "Write clean, idiomatic, production-ready $lang code for the following specification. Include doc comments and edge case handling:\n\n$input"
            "Debug & Fix" -> "Find and fix bugs, race conditions, memory leaks, or logical errors in the following $lang code. Show the corrected code block and explain what caused the bug:\n\n```$lang\n$input\n```"
            "Code Review" -> "Perform a senior engineer code review for this $lang code. Score readability, security, performance, and suggest concrete optimizations:\n\n```$lang\n$input\n```"
            "Unit Tests" -> "Write comprehensive unit tests for this $lang code covering standard cases, boundary conditions, nullability, and failure cases:\n\n```$lang\n$input\n```"
            else -> input
        }

        viewModelScope.launch {
            val response = apiClient.generateContent(
                modelId = AvailableModels.GEMINI_3_1_PRO.id,
                prompt = prompt,
                systemInstruction = "You are SASUKE AI Coding Engine, an expert software architect and compiler specialist. Produce clean markdown formatting with annotated code blocks."
            )

            if (response.isError) {
                _codingState.value = _codingState.value.copy(
                    isRunning = false,
                    errorMessage = response.errorMessage
                )
            } else {
                _codingState.value = _codingState.value.copy(
                    isRunning = false,
                    outputResult = response.text,
                    latencyMs = response.latencyMs
                )
            }
        }
    }

    fun setStudyTopic(topic: String) {
        _studyState.value = _studyState.value.copy(topic = topic)
    }

    fun setDiscipline(disc: String) {
        _studyState.value = _studyState.value.copy(discipline = disc)
    }

    fun setDifficulty(diff: String) {
        _studyState.value = _studyState.value.copy(difficulty = diff)
    }

    fun setStudyLanguage(lang: String) {
        _studyState.value = _studyState.value.copy(languageOutput = lang)
    }

    fun explainConcept() {
        val topic = _studyState.value.topic.trim()
        if (topic.isEmpty() || _studyState.value.isRunning) return

        _studyState.value = _studyState.value.copy(
            isRunning = true,
            explanationResult = "",
            quizResult = "",
            errorMessage = null
        )

        val disc = _studyState.value.discipline
        val diff = _studyState.value.difficulty
        val lang = _studyState.value.languageOutput

        val prompt = "Provide a comprehensive, pedagogical, step-by-step scientific breakdown of '$topic' within the field of $disc at a $diff level in $lang. Structure your explanation with: 1. Core Intuition & Historical Context, 2. Fundamental Equations and Physical Laws, 3. Real-world Applications & Modern Experiments, 4. Common Misconceptions."

        viewModelScope.launch {
            val response = apiClient.generateContent(
                modelId = AvailableModels.GEMINI_3_1_PRO.id,
                prompt = prompt,
                systemInstruction = "You are SASUKE AI Science Professor, a world-class STEM educator specializing in Physics, Chemistry, Biology, and Advanced Mathematics. Explain with clarity, rigorous scientific facts, and engaging structure."
            )

            if (response.isError) {
                _studyState.value = _studyState.value.copy(
                    isRunning = false,
                    errorMessage = response.errorMessage
                )
            } else {
                _studyState.value = _studyState.value.copy(
                    isRunning = false,
                    explanationResult = response.text,
                    latencyMs = response.latencyMs
                )
            }
        }
    }

    fun generateQuiz() {
        val topic = _studyState.value.topic.trim()
        if (topic.isEmpty() || _studyState.value.isRunning) return

        _studyState.value = _studyState.value.copy(
            isRunning = true,
            quizResult = "",
            errorMessage = null
        )

        val disc = _studyState.value.discipline
        val diff = _studyState.value.difficulty
        val lang = _studyState.value.languageOutput

        val prompt = "Create a 4-question interactive scientific quiz about '$topic' in $disc at $diff difficulty in $lang. Each question should have 4 multiple-choice options (A, B, C, D), followed by a spoiler or hidden section revealing the correct answer and a thorough conceptual explanation."

        viewModelScope.launch {
            val response = apiClient.generateContent(
                modelId = AvailableModels.GEMINI_3_1_PRO.id,
                prompt = prompt,
                systemInstruction = "You are SASUKE AI STEM Quiz Master. Provide high-quality diagnostic questions that test deep comprehension rather than rote memorization."
            )

            if (response.isError) {
                _studyState.value = _studyState.value.copy(
                    isRunning = false,
                    errorMessage = response.errorMessage
                )
            } else {
                _studyState.value = _studyState.value.copy(
                    isRunning = false,
                    quizResult = response.text,
                    latencyMs = response.latencyMs
                )
            }
        }
    }
}
