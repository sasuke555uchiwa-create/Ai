package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomSearchTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(app)
    }

    @Test
    fun testSearchSessions_byTitle() = runBlocking {
        val dao = db.appDao()
        val session1 = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "Quantum Mechanics Discussion",
            selectedModelId = "gemini-3.5-flash",
            createdAt = System.currentTimeMillis()
        )
        val session2 = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "Italian Pasta Recipes",
            selectedModelId = "gemini-3.5-flash",
            createdAt = System.currentTimeMillis() - 1000
        )

        dao.insertSession(session1)
        dao.insertSession(session2)

        val quantumResults = dao.searchSessions("Quantum").first()
        assertEquals(1, quantumResults.size)
        assertEquals(session1.id, quantumResults[0].id)

        val pastaResults = dao.searchSessions("Pasta").first()
        assertEquals(1, pastaResults.size)
        assertEquals(session2.id, pastaResults[0].id)

        val nonMatching = dao.searchSessions("UnrelatedKeywordXYZ").first()
        assertTrue(nonMatching.isEmpty())
    }

    @Test
    fun testSearchMessages_byTextContent() = runBlocking {
        val dao = db.appDao()
        val sId = UUID.randomUUID().toString()
        val session = ChatSessionEntity(
            id = sId,
            title = "Astronomy Chat",
            selectedModelId = "gemini-3.5-flash",
            createdAt = System.currentTimeMillis()
        )
        dao.insertSession(session)

        val msg1 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sId,
            role = "USER",
            text = "Tell me about the James Webb Space Telescope discoveries in deep space.",
            timestamp = System.currentTimeMillis()
        )
        val msg2 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sId,
            role = "ASSISTANT",
            text = "JWST observed early galaxies formed just 300 million years after the Big Bang.",
            timestamp = System.currentTimeMillis() + 100
        )
        dao.insertMessage(msg1)
        dao.insertMessage(msg2)

        val searchRes = dao.searchMessagesSnapshot("galaxies")
        assertEquals(1, searchRes.size)
        assertEquals(msg2.id, searchRes[0].id)
        assertTrue(searchRes[0].text.contains("galaxies"))

        val jwstRes = dao.searchMessagesSnapshot("James Webb")
        assertEquals(1, jwstRes.size)
        assertEquals(msg1.id, jwstRes[0].id)
    }
}
