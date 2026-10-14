package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TavilySearchResultItem(
    val title: String,
    val url: String,
    val snippet: String,
    val publishedDate: String? = null
)

sealed class TavilySearchResponse {
    data class Success(
        val query: String,
        val results: List<TavilySearchResultItem>,
        val sources: List<WebSource>
    ) : TavilySearchResponse()

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val isQuotaExhausted: Boolean = false,
        val isUnconfigured: Boolean = false
    ) : TavilySearchResponse()
}

class TavilySearchWorkerClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "TavilySearchWorker"
        private const val PREFS_NAME = "sasuke_settings"
        private const val KEY_SEARCH_WORKER_URL = "search_worker_url"
        private const val KEY_TAVILY_API_KEY = "tavily_api_key"
        private const val KEY_CLOUDFLARE_TOKEN = "cloudflare_api_token"
        const val DEFAULT_PLACEHOLDER_URL = "https://sasuke-search-worker.workers.dev"
        const val TAVILY_DIRECT_API_URL = "https://api.tavily.com/search"
        const val CLOUDFLARE_ACCOUNT_ID = "a3ed4c18631b54537803ca8e119779d1"
        const val CLOUDFLARE_TOKENS_URL = "https://dash.cloudflare.com/a3ed4c18631b54537803ca8e119779d1/api-tokens"
        const val TAVILY_DASHBOARD_URL = "https://app.tavily.com/home"
    }

    fun getWorkerUrl(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SEARCH_WORKER_URL, "")?.trim()?.removeSuffix("/") ?: ""
    }

    fun saveWorkerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SEARCH_WORKER_URL, cleanUrl).apply()
    }

    fun getTavilyApiKey(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TAVILY_API_KEY, "")?.trim() ?: ""
    }

    fun saveTavilyApiKey(key: String) {
        val cleanKey = key.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TAVILY_API_KEY, cleanKey).apply()
    }

    fun getCloudflareToken(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CLOUDFLARE_TOKEN, "")?.trim() ?: ""
    }

    fun saveCloudflareToken(token: String) {
        val cleanToken = token.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CLOUDFLARE_TOKEN, cleanToken).apply()
    }

    fun isWorkerConfigured(): Boolean {
        val url = getWorkerUrl()
        return url.isNotEmpty() && url.startsWith("http")
    }

    fun isTavilyKeyConfigured(): Boolean {
        val key = getTavilyApiKey()
        return key.isNotEmpty() && (key.startsWith("tvly-") || key.length >= 8)
    }

    /**
     * Search is configured if either Cloudflare Worker URL or Tavily API Key is set!
     */
    fun isConfigured(): Boolean {
        return isWorkerConfigured() || isTavilyKeyConfigured()
    }

    fun getActiveConfigurationLabel(): String {
        return when {
            isWorkerConfigured() && isTavilyKeyConfigured() -> "Cloudflare Worker + Direct Tavily (Dual Mode)"
            isWorkerConfigured() -> "Cloudflare Worker Proxy Active"
            isTavilyKeyConfigured() -> "Direct Tavily API Active (Zero Server)"
            else -> "Not Configured"
        }
    }

    /**
     * Checks if the Worker endpoint is reachable and healthy
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val workerUrl = getWorkerUrl()
        if (workerUrl.isEmpty()) return@withContext false

        val healthUrl = "$workerUrl/health"
        try {
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .header("X-App-Client", "sasuke-ai-android")
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed: ${e.message}")
            false
        }
    }

    /**
     * Validates a Tavily API Key by making a quick ping to the official Tavily API
     */
    suspend fun verifyTavilyKey(key: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext false to "API Key cannot be empty."
        }
        if (!trimmedKey.startsWith("tvly-") && trimmedKey.length < 8) {
            return@withContext false to "Unrecognized format. Tavily keys typically start with 'tvly-'."
        }

        val requestJson = JSONObject().apply {
            put("api_key", trimmedKey)
            put("query", "test")
            put("max_results", 1)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(TAVILY_DIRECT_API_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $trimmedKey")
            .build()

        try {
            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string().orEmpty()
            when (code) {
                in 200..299 -> true to "Valid Tavily Key! Free tier (1,000 monthly searches) is active."
                401, 403 -> false to "Invalid API key (HTTP $code). Please check key at app.tavily.com."
                429 -> false to "Monthly quota exhausted or rate limit reached (HTTP 429)."
                else -> false to "Verification failed with status $code: $body"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tavily key verification failed: ${e.message}")
            false to "Connection error: ${e.localizedMessage ?: "Check your network connection"}"
        }
    }

    /**
     * Executes real-time web search via Cloudflare Worker or Direct Tavily API
     */
    suspend fun search(
        query: String,
        firebaseIdToken: String? = null
    ): TavilySearchResponse = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            return@withContext TavilySearchResponse.Error(
                message = "Search query is too short (minimum 2 characters).",
                statusCode = 400
            )
        }

        val workerConfigured = isWorkerConfigured()
        val tavilyKeyConfigured = isTavilyKeyConfigured()

        if (!workerConfigured && !tavilyKeyConfigured) {
            return@withContext TavilySearchResponse.Error(
                message = "Web search is not configured. Add a free Tavily API Key (app.tavily.com) or Cloudflare Worker URL in Settings.",
                isUnconfigured = true
            )
        }

        // 1. Try Cloudflare Worker first if configured
        if (workerConfigured) {
            val workerUrl = getWorkerUrl()
            val requestUrl = "$workerUrl/search"
            val requestJson = JSONObject().apply {
                put("query", trimmedQuery.take(400))
                put("max_results", 5)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("X-App-Client", "sasuke-ai-android")

            val tavilyKey = getTavilyApiKey()
            if (tavilyKey.isNotEmpty()) {
                requestBuilder.header("X-Tavily-Key", tavilyKey)
            }

            if (!firebaseIdToken.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $firebaseIdToken")
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string().orEmpty()
                val code = response.code

                // If Worker returns 503 (unconfigured on server) and we have a Tavily Key, fallback to direct!
                if (code == 503 && tavilyKeyConfigured) {
                    Log.i(TAG, "Worker reported unconfigured. Falling back to direct Tavily API.")
                    return@withContext executeDirectTavilySearch(trimmedQuery, tavilyKey)
                }

                return@withContext parseResponse(code, responseBody, trimmedQuery)
            } catch (e: IOException) {
                Log.w(TAG, "Search network error on worker: ${e.message}")
                if (tavilyKeyConfigured) {
                    Log.i(TAG, "Worker connection failed, falling back to direct Tavily API")
                    return@withContext executeDirectTavilySearch(trimmedQuery, tavilyKey)
                }
                return@withContext TavilySearchResponse.Error(
                    message = "Unable to connect to Search Worker. Please check your internet connection or verify your Worker URL in Settings.",
                    statusCode = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Search error on worker: ${e.message}", e)
                if (tavilyKeyConfigured) {
                    return@withContext executeDirectTavilySearch(trimmedQuery, tavilyKey)
                }
                return@withContext TavilySearchResponse.Error(
                    message = "Search error: ${e.localizedMessage ?: "Unknown error"}",
                    statusCode = null
                )
            }
        }

        // 2. Direct Tavily API Search (Instant zero-server mode)
        val tavilyKey = getTavilyApiKey()
        return@withContext executeDirectTavilySearch(trimmedQuery, tavilyKey)
    }

    private fun executeDirectTavilySearch(query: String, apiKey: String): TavilySearchResponse {
        val requestJson = JSONObject().apply {
            put("api_key", apiKey)
            put("query", query.take(400))
            put("search_depth", "basic")
            put("max_results", 5)
            put("include_answer", false)
            put("include_images", false)
            put("include_raw_content", false)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(TAVILY_DIRECT_API_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string().orEmpty()
            parseResponse(code, body, query)
        } catch (e: Exception) {
            Log.e(TAG, "Direct Tavily error: ${e.message}", e)
            TavilySearchResponse.Error(
                message = "Direct Tavily search error: ${e.localizedMessage ?: "Network connection failure"}",
                statusCode = null
            )
        }
    }

    fun parseResponse(statusCode: Int, responseBody: String, query: String): TavilySearchResponse {
        if (statusCode !in 200..299) {
            val errorMsg = parseErrorMessage(statusCode, responseBody)
            val isQuotaExhausted = statusCode == 429 || responseBody.contains("quota", ignoreCase = true)
            val isUnconfigured = statusCode == 503 || responseBody.contains("not configured", ignoreCase = true)

            return TavilySearchResponse.Error(
                message = errorMsg,
                statusCode = statusCode,
                isQuotaExhausted = isQuotaExhausted,
                isUnconfigured = isUnconfigured
            )
        }

        return try {
            val root = JSONObject(responseBody)
            val resultsArray = root.optJSONArray("results") ?: JSONArray()
            val items = mutableListOf<TavilySearchResultItem>()
            val sources = mutableListOf<WebSource>()

            for (i in 0 until resultsArray.length()) {
                val itemObj = resultsArray.optJSONObject(i) ?: continue
                val title = itemObj.optString("title", "Web Result").trim()
                val url = itemObj.optString("url", "").trim()
                val snippet = itemObj.optString("snippet", itemObj.optString("content", "")).trim()
                val publishedDate = if (itemObj.has("published_date") && !itemObj.isNull("published_date")) {
                    itemObj.optString("published_date").ifEmpty { null }
                } else null

                if (url.isNotEmpty() && url.startsWith("http")) {
                    items.add(
                        TavilySearchResultItem(
                            title = title.ifEmpty { "Web Result" },
                            url = url,
                            snippet = snippet,
                            publishedDate = publishedDate
                        )
                    )

                    if (sources.none { it.url == url }) {
                        sources.add(WebSource(title = title.ifEmpty { "Web Result" }, url = url))
                    }
                }
            }

            TavilySearchResponse.Success(
                query = query,
                results = items,
                sources = sources
            )
        } catch (e: Exception) {
            TavilySearchResponse.Error(
                message = "Malformed response from Search Provider: ${e.localizedMessage}",
                statusCode = statusCode
            )
        }
    }

    private fun parseErrorMessage(code: Int, errorBody: String): String {
        return try {
            val root = JSONObject(errorBody)
            val error = root.optString("error", "")
            when {
                error.isNotEmpty() -> error
                code == 401 || code == 403 -> "Invalid or unauthorized Tavily API Key. Check your key at app.tavily.com."
                code == 404 -> "Search endpoint not found (404). Check your Cloudflare Worker URL."
                code == 429 -> "Tavily free monthly quota exhausted or rate limit reached (HTTP 429)."
                code == 503 -> "Search provider is not configured. Please add your Tavily API Key in Settings."
                code == 504 -> "Search request timed out after 15 seconds."
                else -> "Search provider returned status $code."
            }
        } catch (e: Exception) {
            when (code) {
                401, 403 -> "Invalid or unauthorized Tavily API Key. Check your key at app.tavily.com."
                429 -> "Tavily free monthly quota exhausted or rate limit reached (HTTP 429)."
                503 -> "Search provider is not configured. Please add your Tavily API Key in Settings."
                504 -> "Search request timed out after 15 seconds."
                else -> "Search provider returned status $code: $errorBody"
            }
        }
    }
}
