package com.example.data.local

import android.content.Context
import com.example.data.model.AppLanguage
import com.example.data.model.AvailableModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("sasuke_settings", Context.MODE_PRIVATE)

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("is_dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _language = MutableStateFlow(
        try {
            AppLanguage.valueOf(prefs.getString("app_language", AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name)
        } catch (e: Exception) {
            AppLanguage.ENGLISH
        }
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        prefs.getString("selected_model", AvailableModels.GEMINI_3_5_FLASH.id) ?: AvailableModels.GEMINI_3_5_FLASH.id
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _searchWorkerUrl = MutableStateFlow(
        prefs.getString("search_worker_url", "") ?: ""
    )
    val searchWorkerUrl: StateFlow<String> = _searchWorkerUrl.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit().putBoolean("is_dark_theme", enabled).apply()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        prefs.edit().putString("app_language", lang.name).apply()
    }

    fun setSelectedModel(modelId: String) {
        _selectedModel.value = modelId
        prefs.edit().putString("selected_model", modelId).apply()
    }

    fun setSearchWorkerUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        _searchWorkerUrl.value = trimmed
        prefs.edit().putString("search_worker_url", trimmed).apply()
    }
}
