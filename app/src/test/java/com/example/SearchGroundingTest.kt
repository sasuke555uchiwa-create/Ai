package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActiveToolType
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.remote.GeminiApiClient
import com.example.data.remote.SearchGroundingStatus
import com.example.ui.viewmodel.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchGroundingTest {

    private lateinit var app: Application
    private lateinit var apiClient: GeminiApiClient
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = ChatViewModel(app)
        apiClient = viewModel.apiClient
        apiClient.resetSearchGroundingStatus()
    }

    @Test
    fun testGroundingStatus_initialState_isUnknown() {
        apiClient.resetSearchGroundingStatus()
        assertEquals(SearchGroundingStatus.Unknown, apiClient.getSearchGroundingStatus())
    }

    @Test
    fun testMarkSearchGroundingUnavailable_updatesStatusAndCache() {
        val testReason = "Google Search Grounding Quota Exhausted (HTTP 429 RESOURCE_EXHAUSTED)"
        apiClient.markSearchGroundingUnavailable(testReason, 429)

        val status = apiClient.getSearchGroundingStatus()
        assertTrue("Status should be Unavailable", status is SearchGroundingStatus.Unavailable)
        val unavail = status as SearchGroundingStatus.Unavailable
        assertEquals(429, unavail.statusCode)
        assertEquals(testReason, unavail.reason)
    }

    @Test
    fun testSaveCustomApiKey_resetsGroundingStatusForReVerification() {
        apiClient.markSearchGroundingUnavailable("Old key exhausted", 429)
        assertTrue(apiClient.getSearchGroundingStatus() is SearchGroundingStatus.Unavailable)

        apiClient.saveCustomApiKey("new-test-key-12345")
        assertEquals(
            "Changing API key should reset grounding cache to Unknown",
            SearchGroundingStatus.Unknown,
            apiClient.getSearchGroundingStatus()
        )
    }

    @Test
    fun testToggleSearchGrounding_whenUnavailable_keepsToggleOffAndShowsDialog() {
        // Arrange: Mark grounding as unavailable (HTTP 429 quota exhausted)
        apiClient.markSearchGroundingUnavailable("Google Search Grounding Quota Exhausted (HTTP 429)", 429)

        // Act: User attempts to turn on search
        viewModel.toggleSearchGrounding()

        // Assert: Honest toggle must stay false, not pretend search is enabled!
        val state = viewModel.uiState.value
        assertFalse("Search grounding must remain OFF when unavailable", state.isSearchGroundingEnabled)
        assertTrue("Search grounding unavailable flag must be true", state.searchGroundingUnavailable)
        assertTrue("Explanation dialog must be shown", state.showSearchUnavailableDialog)
        assertTrue("Explanation must mention quota or 429", state.searchGroundingExplanation?.contains("429") == true)
    }

    @Test
    fun testDismissSearchUnavailableDialog_closesDialog() {
        apiClient.markSearchGroundingUnavailable("429 Quota Exhausted", 429)
        viewModel.toggleSearchGrounding()
        assertTrue(viewModel.uiState.value.showSearchUnavailableDialog)

        viewModel.dismissSearchUnavailableDialog()
        assertFalse(viewModel.uiState.value.showSearchUnavailableDialog)
    }

    @Test
    fun testSelectWebSearchTool_whenUnavailable_keepsToolNoneAndExplains() {
        apiClient.markSearchGroundingUnavailable("429 Quota Exhausted", 429)

        viewModel.setActiveTool(ActiveToolType.WEB_SEARCH)

        val state = viewModel.uiState.value
        assertFalse("Search grounding must not be enabled", state.isSearchGroundingEnabled)
        assertEquals("Active tool must not remain WEB_SEARCH", ActiveToolType.NONE, state.activeTool)
        assertTrue("Explanation dialog must be shown", state.showSearchUnavailableDialog)
    }
}
