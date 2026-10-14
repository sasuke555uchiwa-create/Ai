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
        const val DEFAULT_PLACEHOLDER_URL = "https://sasuke-search-worker.workers.dev"
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

    fun isConfigured(): Boolean {
        val url = getWorkerUrl()
        return url.isNotEmpty() && url.startsWith("http")
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
     * Executes real-time web search via Cloudflare Worker and Tavily
     */
    suspend fun search(
        query: String,
        firebaseIdToken: String? = null
    ): TavilySearchResponse = withContext(Dispatchers.IO) {
        val workerUrl = getWorkerUrl()
        if (workerUrl.isEmpty()) {
            return@withContext TavilySearchResponse.Error(
                message = "Search Worker URL is not configured. Please set your Cloudflare Worker URL in Settings or continue with regular chat.",
                isUnconfigured = true
            )
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            return@withContext TavilySearchResponse.Error(
                message = "Search query is too short (minimum 2 characters).",
                statusCode = 400
            )
        }

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

        if (!firebaseIdToken.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $firebaseIdToken")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string().orEmpty()
            val code = response.code

            return@withContext parseResponse(code, responseBody, trimmedQuery)
        } catch (e: IOException) {
            Log.e(TAG, "Search network error: ${e.message}", e)
            TavilySearchResponse.Error(
                message = "Unable to connect to Search Worker. Please check your internet connection or verify your Worker URL in Settings.",
                statusCode = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
            TavilySearchResponse.Error(
                message = "Search error: ${e.localizedMessage ?: "Unknown error"}",
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
                val snippet = itemObj.optString("snippet", "").trim()
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
                message = "Malformed response from Search Worker: ${e.localizedMessage}",
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
                code == 404 -> "Search endpoint not found (404). Check your Cloudflare Worker URL."
                code == 429 -> "Tavily free monthly quota exhausted or rate limit reached (HTTP 429)."
                code == 503 -> "Search provider is not configured. Please add TAVILY_API_KEY to your Worker secrets."
                code == 504 -> "Search request timed out after 15 seconds."
                else -> "Search Worker returned status $code."
            }
        } catch (e: Exception) {
            when (code) {
                429 -> "Tavily free monthly quota exhausted or rate limit reached (HTTP 429)."
                503 -> "Search provider is not configured. Please add TAVILY_API_KEY to your Worker secrets."
                504 -> "Search request timed out after 15 seconds."
                else -> "Search Worker returned status $code: $errorBody"
            }
        }
    }
}
