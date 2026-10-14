package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActiveToolType
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.remote.TavilySearchResponse
import com.example.data.remote.TavilySearchWorkerClient
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TavilySearchWorkerTest {

    private lateinit var app: Application
    private lateinit var client: TavilySearchWorkerClient
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        client = TavilySearchWorkerClient(app)
        viewModel = ChatViewModel(app)
    }

    @Test
    fun testSearchSuccess_preservesOriginalUrlsAndTitles() {
        val mockJson = """
            {
                "query": "Mars rover discovery 2026",
                "provider": "tavily",
                "count": 2,
                "results": [
                    {
                        "title": "NASA Mars Rover Update",
                        "url": "https://www.nasa.gov/missions/mars-rover-2026",
                        "snippet": "Perseverance finds organic molecules in Jezero crater.",
                        "published_date": "2026-04-12"
                    },
                    {
                        "title": "ESA ExoMars Collaboration",
                        "url": "https://www.esa.int/Space_Science/ExoMars_Latest",
                        "snippet": "ESA announces joint scientific analysis with international partners.",
                        "published_date": "2026-05-01"
                    }
                ]
            }
        """.trimIndent()

        val response = client.parseResponse(200, mockJson, "Mars rover discovery 2026")
        assertTrue("Response should be Success", response is TavilySearchResponse.Success)

        val success = response as TavilySearchResponse.Success
        assertEquals(2, success.results.size)
        assertEquals(2, success.sources.size)

        // Strict preservation of original URLs
        assertEquals("https://www.nasa.gov/missions/mars-rover-2026", success.sources[0].url)
        assertEquals("NASA Mars Rover Update", success.sources[0].title)
        assertEquals("https://www.esa.int/Space_Science/ExoMars_Latest", success.sources[1].url)
        assertEquals("ESA ExoMars Collaboration", success.sources[1].title)

        // Snippets and published dates preserved
        assertEquals("2026-04-12", success.results[0].publishedDate)
        assertTrue(success.results[0].snippet.contains("Perseverance"))
    }

    @Test
    fun testSearchSuccess_filtersOutInvalidOrEmptyUrls() {
        val mockJson = """
            {
                "query": "test",
                "results": [
                    {
                        "title": "Valid result",
                        "url": "https://example.com/valid",
                        "snippet": "valid content"
                    },
                    {
                        "title": "Invalid result with blank url",
                        "url": "",
                        "snippet": "invalid"
                    },
                    {
                        "title": "Invalid result with non-http",
                        "url": "ftp://bad-url",
                        "snippet": "bad"
                    }
                ]
            }
        """.trimIndent()

        val response = client.parseResponse(200, mockJson, "test")
        assertTrue(response is TavilySearchResponse.Success)
        val success = response as TavilySearchResponse.Success

        assertEquals(1, success.results.size)
        assertEquals("https://example.com/valid", success.results[0].url)
    }

    @Test
    fun testMalformedResponse_handledGracefully() {
        val brokenJson = "{ invalid json content: 123"

        val response = client.parseResponse(200, brokenJson, "query")
        assertTrue("Malformed JSON must return Error, not crash", response is TavilySearchResponse.Error)
        val error = response as TavilySearchResponse.Error
        assertTrue(error.message.contains("Malformed"))
    }

    @Test
    fun testHttp400_queryValidationFailure() {
        val errorBody = """{"error":"Search query must be at least 2 characters long","code":400}"""

        val response = client.parseResponse(400, errorBody, "a")
        assertTrue(response is TavilySearchResponse.Error)
        val error = response as TavilySearchResponse.Error
        assertEquals(400, error.statusCode)
        assertTrue(error.message.contains("at least 2 characters"))
    }

    @Test
    fun testHttp401_invalidTavilyApiKey() {
        val errorBody = """{"error":"Tavily API Key is invalid or expired. Please check your TAVILY_API_KEY secret.","code":502,"upstream_status":401}"""

        val response = client.parseResponse(502, errorBody, "space exploration")
        assertTrue(response is TavilySearchResponse.Error)
        val error = response as TavilySearchResponse.Error
        assertTrue(error.message.contains("TAVILY_API_KEY"))
    }

    @Test
    fun testHttp429_quotaExhausted() {
        val errorBody = """{"error":"Tavily free monthly quota exhausted or rate limit exceeded (HTTP 429).","code":429,"is_quota_exhausted":true}"""

        val response = client.parseResponse(429, errorBody, "live scores")
        assertTrue(response is TavilySearchResponse.Error)
        val error = response as TavilySearchResponse.Error
        assertEquals(429, error.statusCode)
        assertTrue("isQuotaExhausted must be true for 429", error.isQuotaExhausted)
        assertTrue(error.message.contains("quota exhausted") || error.message.contains("429"))
    }

    @Test
    fun testHttp503_unconfiguredWorkerSecret() {
        val errorBody = """{"error":"Tavily API key is not configured in Worker secrets. Run: npx wrangler secret put TAVILY_API_KEY","code":503,"is_unconfigured":true}"""

        val response = client.parseResponse(503, errorBody, "climate report")
        assertTrue(response is TavilySearchResponse.Error)
        val error = response as TavilySearchResponse.Error
        assertEquals(503, error.statusCode)
        assertTrue("isUnconfigured must be true for 503", error.isUnconfigured)
        assertTrue(error.message.contains("not configured"))
    }

    @Test
    fun testHttp504_searchTimeout() {
        val errorBody = """{"error":"Search request timed out after 12 seconds","code":504}"""

        val response = client.parseResponse(504, errorBody, "quantum computing")
        assertTrue(response is TavilySearchResponse.Error)
        val error = response as TavilySearchResponse.Error
        assertEquals(504, error.statusCode)
        assertTrue(error.message.contains("timed out"))
    }

    @Test
    fun testFallbackToRegularChat_sendLastWithoutSearch() {
        // Arrange: User sent a message with search enabled, but search failed with 429
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            text = "What is the latest Hubble telescope discovery?",
            toolType = "WEB_SEARCH"
        )
        val errorMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            text = "Web search error: Tavily quota exhausted (HTTP 429)",
            isError = true
        )

        viewModel.sendMessage("What is the latest Hubble telescope discovery?")

        // Simulate search error state
        viewModel.sendLastWithoutSearch()

        val state = viewModel.uiState.value
        assertFalse("Search must be disabled after choosing regular chat fallback", state.isSearchGroundingEnabled)
        assertNull("Error message must be cleared on fallback", state.errorMessage)
        assertEquals(ActiveToolType.NONE, state.activeTool)
    }

    @Test
    fun testWorkerUrlConfiguration() {
        // Initially not configured or default
        client.saveWorkerUrl("  https://sasuke-search-worker.my-account.workers.dev/  ")
        assertEquals(
            "https://sasuke-search-worker.my-account.workers.dev",
            client.getWorkerUrl()
        )
        assertTrue(client.isConfigured())

        client.saveWorkerUrl("")
        assertFalse(client.isConfigured())
    }
}
