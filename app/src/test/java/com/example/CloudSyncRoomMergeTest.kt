package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDao
import com.example.data.local.AppDatabase
import com.example.data.local.AppTypeConverters
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.ChatMessage
import com.example.data.model.FirestoreChatMessage
import com.example.data.model.FirestoreChatSession
import com.example.data.model.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudSyncRoomMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.appDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCloudSyncMerge_preservesGuestSessionsWithoutDuplication() = runBlocking {
        // 1. Arrange: Existing local guest session
        val guestSessionId = "guest_session_123"
        val guestSession = ChatSessionEntity(
            id = guestSessionId,
            title = "Guest Conversation",
            selectedModelId = "gemini-3.5-flash",
            searchGroundingEnabled = false,
            createdAt = 1000L
        )
        dao.insertSession(guestSession)

        val guestMsg = ChatMessageEntity(
            id = "guest_msg_1",
            sessionId = guestSessionId,
            role = MessageRole.USER,
            text = "Hello from Guest",
            modelId = null,
            timestamp = 1005L,
            imageBase64 = null,
            latencyMs = null,
            sourcesJson = "[]"
        )
        dao.insertMessage(guestMsg)

        // 2. Simulate restoring 2 cloud sessions with messages
        val cloudSession1 = FirestoreChatSession(
            id = "cloud_session_A",
            userId = "user_abc",
            title = "Deep Research on Quantum Computing",
            model = "gemini-3.1-pro"
        )
        val cloudSession2 = FirestoreChatSession(
            id = "cloud_session_B",
            userId = "user_abc",
            title = "Kotlin Compose Optimization",
            model = "gemini-3.5-flash"
        )

        // Simulate insertion (as done by FirestoreChatRepository.syncUserHistoryToRoom)
        listOf(cloudSession1, cloudSession2).forEach { s ->
            dao.insertSession(
                ChatSessionEntity(
                    id = s.id,
                    title = s.title,
                    selectedModelId = s.model,
                    searchGroundingEnabled = false,
                    createdAt = 2000L
                )
            )
        }

        // Add cloud messages
        val cloudMessages = listOf(
            FirestoreChatMessage(
                id = "cloud_msg_1",
                sessionId = "cloud_session_A",
                userId = "user_abc",
                sender = "user",
                content = "Explain superposition"
            ),
            FirestoreChatMessage(
                id = "cloud_msg_2",
                sessionId = "cloud_session_A",
                userId = "user_abc",
                sender = "model",
                content = "Quantum superposition is..."
            )
        )

        cloudMessages.forEach { m ->
            val chatMsg = ChatMessage(
                id = m.id,
                role = if (m.sender == "user") MessageRole.USER else MessageRole.ASSISTANT,
                text = m.content,
                modelId = m.modelUsed,
                timestamp = 2010L
            )
            dao.insertMessage(
                ChatMessageEntity(
                    id = m.id,
                    sessionId = m.sessionId,
                    role = if (m.sender == "user") MessageRole.USER else MessageRole.ASSISTANT,
                    text = m.content,
                    modelId = m.modelUsed,
                    timestamp = 2010L,
                    imageBase64 = null,
                    latencyMs = null,
                    sourcesJson = "[]",
                    extraJson = AppTypeConverters.extraToJson(chatMsg)
                )
            )
        }

        // 3. Assert: Verify all 3 sessions exist
        val allSessions = dao.getAllSessions().first()
        assertEquals("Total sessions must be 3 (1 guest + 2 cloud)", 3, allSessions.size)

        // Verify guest session is intact
        val retrievedGuest = allSessions.find { it.id == guestSessionId }
        assertNotNull("Guest session must be preserved", retrievedGuest)
        assertEquals("Guest Conversation", retrievedGuest?.title)

        // Verify guest messages are intact
        val guestMessages = dao.getMessagesSnapshot(guestSessionId)
        assertEquals(1, guestMessages.size)
        assertEquals("Hello from Guest", guestMessages[0].text)

        // Verify cloud session A messages
        val cloudMessagesA = dao.getMessagesSnapshot("cloud_session_A")
        assertEquals(2, cloudMessagesA.size)
        assertEquals("Explain superposition", cloudMessagesA[0].text)
        assertEquals(MessageRole.USER, cloudMessagesA[0].role)
        assertEquals("Quantum superposition is...", cloudMessagesA[1].text)
        assertEquals(MessageRole.ASSISTANT, cloudMessagesA[1].role)

        // 4. Test idempotency: re-syncing the same cloud session should NOT duplicate
        dao.insertSession(
            ChatSessionEntity(
                id = cloudSession1.id,
                title = "Updated Title",
                selectedModelId = cloudSession1.model,
                searchGroundingEnabled = false,
                createdAt = 2000L
            )
        )
        val afterResyncSessions = dao.getAllSessions().first()
        assertEquals("Idempotent sync: session count remains 3", 3, afterResyncSessions.size)
        assertEquals("Updated Title", afterResyncSessions.find { it.id == cloudSession1.id }?.title)
    }
}
