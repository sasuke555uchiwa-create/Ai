package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiStreamEvent {
    data class Chunk(val text: String) : GeminiStreamEvent()
    data class Sources(val list: List<WebSource>) : GeminiStreamEvent()
    data class Done(val fullText: String, val totalLatencyMs: Long) : GeminiStreamEvent()
    data class Error(val message: String, val statusCode: Int? = null) : GeminiStreamEvent()
}

data class GeminiDirectResponse(
    val text: String,
    val sources: List<WebSource> = emptyList(),
    val imageBase64: String? = null,
    val latencyMs: Long = 0,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

class GeminiApiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val PREFS_NAME = "sasuke_ai_prefs"
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
    }

    fun getEffectiveApiKey(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customKey = prefs.getString(KEY_CUSTOM_API_KEY, "")?.trim()
        if (!customKey.isNullOrEmpty()) {
            return customKey
        }
        return try {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
                buildConfigKey
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun saveCustomApiKey(key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
    }

    fun isApiKeyConfigured(): Boolean {
        return getEffectiveApiKey().isNotEmpty()
    }

    /**
     * Streams content generation using SSE
     */
    fun streamGenerateContent(
        modelId: String,
        messages: List<Pair<String, String>>, // role ("user" or "model"), text
        userImageBase64: String? = null,
        userPdfBase64: String? = null,
        enableSearchGrounding: Boolean = false,
        systemInstruction: String? = null
    ): Flow<GeminiStreamEvent> = flow {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            emit(GeminiStreamEvent.Error(
                "Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel or enter a custom key in Sasuke AI Settings."
            ))
            return@flow
        }

        val startTime = System.currentTimeMillis()
        val requestJson = buildGenerateJson(
            messages = messages,
            userImageBase64 = userImageBase64,
            userPdfBase64 = userPdfBase64,
            enableSearchGrounding = enableSearchGrounding,
            systemInstruction = systemInstruction,
            responseModalities = listOf("TEXT")
        )

        val url = "$BASE_URL/models/$modelId:streamGenerateContent?alt=sse&key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        var accumulatedText = StringBuilder()
        val collectedSources = mutableListOf<WebSource>()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val parsedError = parseApiErrorMessage(response.code, errorBody)
                emit(GeminiStreamEvent.Error(parsedError, response.code))
                return@flow
            }

            val source = response.body?.source()
            if (source == null) {
                emit(GeminiStreamEvent.Error("Empty response stream from Gemini server."))
                return@flow
            }

            var line: String?
            while (source.readUtf8Line().also { line = it } != null) {
                val rawLine = line?.trim() ?: continue
                if (!rawLine.startsWith("data:")) continue
                val jsonPayload = rawLine.removePrefix("data:").trim()
                if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                try {
                    val root = JSONObject(jsonPayload)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)

                        // Grounding metadata check
                        val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                        if (groundingMetadata != null) {
                            val chunks = groundingMetadata.optJSONArray("groundingChunks")
                            if (chunks != null) {
                                for (i in 0 until chunks.length()) {
                                    val chunk = chunks.getJSONObject(i)
                                    val web = chunk.optJSONObject("web")
                                    if (web != null) {
                                        val title = web.optString("title", "Web Result")
                                        val uri = web.optString("uri", "")
                                        if (uri.isNotEmpty() && collectedSources.none { it.url == uri }) {
                                            collectedSources.add(WebSource(title = title, url = uri))
                                        }
                                    }
                                }
                            }
                        }

                        val content = candidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val textPart = part.optString("text", "")
                                    if (textPart.isNotEmpty()) {
                                        accumulatedText.append(textPart)
                                        emit(GeminiStreamEvent.Chunk(textPart))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing stream chunk: ${e.message}")
                }
            }

            if (collectedSources.isNotEmpty()) {
                emit(GeminiStreamEvent.Sources(collectedSources))
            }

            val latency = System.currentTimeMillis() - startTime
            emit(GeminiStreamEvent.Done(accumulatedText.toString(), latency))

        } catch (e: Exception) {
            emit(GeminiStreamEvent.Error("Network error: ${e.localizedMessage ?: "Connection timed out"}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming content generation (ideal for Arena tests and quick queries)
     */
    suspend fun generateContent(
        modelId: String,
        prompt: String,
        systemInstruction: String? = null,
        enableSearchGrounding: Boolean = false
    ): GeminiDirectResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext GeminiDirectResponse(
                text = "",
                isError = true,
                errorMessage = "Gemini API key is not configured. Please add GEMINI_API_KEY in the Secrets panel."
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val requestJson = buildGenerateJson(
                messages = listOf("user" to prompt),
                userImageBase64 = null,
                enableSearchGrounding = enableSearchGrounding,
                systemInstruction = systemInstruction,
                responseModalities = listOf("TEXT")
            )

            val url = "$BASE_URL/models/$modelId:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext GeminiDirectResponse(
                    text = "",
                    latencyMs = latency,
                    isError = true,
                    errorMessage = parseApiErrorMessage(response.code, responseString)
                )
            }

            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val textBuilder = StringBuilder()
            val sources = mutableListOf<WebSource>()

            if (candidate != null) {
                val grounding = candidate.optJSONObject("groundingMetadata")
                val chunks = grounding?.optJSONArray("groundingChunks")
                if (chunks != null) {
                    for (i in 0 until chunks.length()) {
                        val chunk = chunks.getJSONObject(i)
                        val web = chunk.optJSONObject("web")
                        if (web != null) {
                            val uri = web.optString("uri", "")
                            val title = web.optString("title", "Web Result")
                            if (uri.isNotEmpty()) sources.add(WebSource(title, uri))
                        }
                    }
                }

                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        textBuilder.append(p.optString("text", ""))
                    }
                }
            }

            GeminiDirectResponse(
                text = textBuilder.toString(),
                sources = sources,
                latencyMs = latency,
                isError = false
            )
        } catch (e: Exception) {
            GeminiDirectResponse(
                text = "",
                latencyMs = System.currentTimeMillis() - startTime,
                isError = true,
                errorMessage = "Request failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Synthesizes image using gemini-2.5-flash-image or gemini-nano-banana-2.1
     */
    suspend fun generateImage(
        modelId: String = "gemini-2.5-flash-image",
        prompt: String,
        aspectRatio: String = "1:1",
        inputImageBase64: String? = null
    ): GeminiDirectResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext GeminiDirectResponse(
                text = "",
                isError = true,
                errorMessage = "API key not configured in AI Studio Secrets."
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val requestJson = JSONObject()
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()

            val textPart = JSONObject()
            textPart.put("text", prompt)
            parts.put(textPart)

            if (!inputImageBase64.isNullOrEmpty()) {
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", inputImageBase64)
                val imgPart = JSONObject()
                imgPart.put("inlineData", inlineData)
                parts.put(imgPart)
            }

            contentObj.put("parts", parts)
            contents.put(contentObj)
            requestJson.put("contents", contents)

            val genConfig = JSONObject()
            val imageConfig = JSONObject()
            imageConfig.put("aspectRatio", aspectRatio)
            imageConfig.put("imageSize", "1K")
            genConfig.put("imageConfig", imageConfig)

            val modalities = JSONArray()
            modalities.put("TEXT")
            modalities.put("IMAGE")
            genConfig.put("responseModalities", modalities)
            requestJson.put("generationConfig", genConfig)

            val url = "$BASE_URL/models/$modelId:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext GeminiDirectResponse(
                    text = "",
                    latencyMs = latency,
                    isError = true,
                    errorMessage = parseApiErrorMessage(response.code, responseString)
                )
            }

            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            var generatedImageBase64: String? = null
            var textDescription = ""

            if (candidate != null) {
                val partsArray = candidate.optJSONObject("content")?.optJSONArray("parts")
                if (partsArray != null) {
                    for (i in 0 until partsArray.length()) {
                        val part = partsArray.getJSONObject(i)
                        if (part.has("inlineData")) {
                            generatedImageBase64 = part.getJSONObject("inlineData").optString("data", "")
                        } else if (part.has("text")) {
                            textDescription += part.optString("text", "")
                        }
                    }
                }
            }

            if (generatedImageBase64.isNullOrEmpty()) {
                GeminiDirectResponse(
                    text = textDescription,
                    latencyMs = latency,
                    isError = true,
                    errorMessage = "The model did not return image data. Note: image generation requires models with image modality such as gemini-2.5-flash-image."
                )
            } else {
                GeminiDirectResponse(
                    text = textDescription,
                    imageBase64 = generatedImageBase64,
                    latencyMs = latency,
                    isError = false
                )
            }
        } catch (e: Exception) {
            GeminiDirectResponse(
                text = "",
                latencyMs = System.currentTimeMillis() - startTime,
                isError = true,
                errorMessage = "Image generation error: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Veo 3.1 Video generation endpoint request
     */
    suspend fun generateVideo(
        prompt: String,
        aspectRatio: String = "16:9",
        resolution: String = "1080p"
    ): GeminiDirectResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext GeminiDirectResponse(
                text = "",
                isError = true,
                errorMessage = "API key not configured."
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val requestJson = JSONObject()
            requestJson.put("prompt", prompt)
            val config = JSONObject()
            config.put("numberOfVideos", 1)
            config.put("resolution", resolution)
            config.put("aspectRatio", aspectRatio)
            requestJson.put("config", config)

            val url = "$BASE_URL/models/veo-3.1-fast-generate-preview:generateVideos?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext GeminiDirectResponse(
                    text = "",
                    latencyMs = latency,
                    isError = true,
                    errorMessage = parseApiErrorMessage(response.code, responseString)
                )
            }

            val root = JSONObject(responseString)
            val opName = root.optString("name", "Operation started")
            GeminiDirectResponse(
                text = "Video generation request accepted by Veo 3.1 Fast engine.\nOperation ID: $opName\n\nNote: Google Veo video generation processes asynchronously in the cloud.",
                latencyMs = latency,
                isError = false
            )
        } catch (e: Exception) {
            GeminiDirectResponse(
                text = "",
                latencyMs = System.currentTimeMillis() - startTime,
                isError = true,
                errorMessage = "Veo video generation error: ${e.localizedMessage}"
            )
        }
    }

    suspend fun checkVideoOperation(operationName: String): GeminiDirectResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext GeminiDirectResponse(
                text = "",
                isError = true,
                errorMessage = "API key not configured."
            )
        }
        val cleanOp = if (operationName.startsWith("operations/")) operationName else "operations/$operationName"
        val url = "$BASE_URL/$cleanOp?key=$apiKey"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext GeminiDirectResponse(
                    text = "",
                    isError = true,
                    errorMessage = parseApiErrorMessage(response.code, responseString)
                )
            }
            val root = JSONObject(responseString)
            val done = root.optBoolean("done", false)
            if (done) {
                if (root.has("error")) {
                    val err = root.getJSONObject("error").optString("message", "Video generation failed")
                    GeminiDirectResponse(text = "Video generation error: $err", isError = true, errorMessage = err)
                } else {
                    GeminiDirectResponse(text = "Veo 3.1 video render completed successfully! Output ready in cloud session.", isError = false)
                }
            } else {
                GeminiDirectResponse(text = "Veo video generation is actively processing in Google Cloud...", isError = false)
            }
        } catch (e: Exception) {
            GeminiDirectResponse(text = "", isError = true, errorMessage = "Error checking operation: ${e.localizedMessage}")
        }
    }

    private fun buildGenerateJson(
        messages: List<Pair<String, String>>,
        userImageBase64: String?,
        userPdfBase64: String? = null,
        enableSearchGrounding: Boolean,
        systemInstruction: String?,
        responseModalities: List<String>
    ): JSONObject {
        val root = JSONObject()

        // System Instruction
        if (!systemInstruction.isNullOrEmpty()) {
            val sysObj = JSONObject()
            val sysParts = JSONArray()
            val sysText = JSONObject()
            sysText.put("text", systemInstruction)
            sysParts.put(sysText)
            sysObj.put("parts", sysParts)
            root.put("systemInstruction", sysObj)
        }

        // Contents
        val contentsArray = JSONArray()
        messages.forEachIndexed { index, (role, text) ->
            val contentObj = JSONObject()
            contentObj.put("role", if (role == "user") "user" else "model")
            val partsArray = JSONArray()

            val textPart = JSONObject()
            textPart.put("text", text)
            partsArray.put(textPart)

            // Attach user image and/or PDF to last user message if present
            if (index == messages.lastIndex && role == "user") {
                if (!userImageBase64.isNullOrEmpty()) {
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "image/jpeg")
                    inlineData.put("data", userImageBase64)
                    val imgPart = JSONObject()
                    imgPart.put("inlineData", inlineData)
                    partsArray.put(imgPart)
                }
                if (!userPdfBase64.isNullOrEmpty()) {
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "application/pdf")
                    inlineData.put("data", userPdfBase64)
                    val pdfPart = JSONObject()
                    pdfPart.put("inlineData", inlineData)
                    partsArray.put(pdfPart)
                }
            }

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        root.put("contents", contentsArray)

        // Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.7)
        val modsArray = JSONArray()
        responseModalities.forEach { modsArray.put(it) }
        genConfig.put("responseModalities", modsArray)
        root.put("generationConfig", genConfig)

        // Google Search Grounding Tool
        if (enableSearchGrounding) {
            val toolsArray = JSONArray()
            val toolObj = JSONObject()
            toolObj.put("googleSearch", JSONObject())
            toolsArray.put(toolObj)
            root.put("tools", toolsArray)
        }

        return root
    }

    private fun parseApiErrorMessage(code: Int, errorBody: String): String {
        return try {
            val root = JSONObject(errorBody)
            val error = root.optJSONObject("error")
            val message = error?.optString("message", "")
            val status = error?.optString("status", "")

            when {
                code == 400 && message?.contains("API key not valid", ignoreCase = true) == true ->
                    "API key is invalid. Please verify your GEMINI_API_KEY in the AI Studio Secrets panel."
                code == 403 ->
                    "Access Forbidden (403): The selected model or feature may not be enabled for your project or region ($message)."
                code == 429 ->
                    "Rate limit or quota exceeded (429). Please wait a moment before sending another request."
                code == 404 ->
                    "Model not found (404): $message"
                message.isNullOrEmpty() -> "Server returned HTTP status $code ($status)."
                else -> message
            }
        } catch (e: Exception) {
            when (code) {
                401 -> "Unauthorized: GEMINI_API_KEY is missing or invalid."
                403 -> "Forbidden: Quota, permissions, or API restrictions apply."
                429 -> "Rate limit reached (429). Please slow down requests."
                else -> "HTTP $code: $errorBody"
            }
        }
    }
}
