package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Sessions
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    // Messages
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesSnapshot(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    // Arena Records
    @Query("SELECT * FROM arena_records ORDER BY timestamp DESC")
    fun getAllArenaRecords(): Flow<List<ArenaRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArenaRecord(record: ArenaRecordEntity)

    @Query("DELETE FROM arena_records WHERE id = :id")
    suspend fun deleteArenaRecord(id: String)

    @Query("DELETE FROM arena_records")
    suspend fun clearAllArenaRecords()

    // Saved Images
    @Query("SELECT * FROM saved_images ORDER BY timestamp DESC")
    fun getAllSavedImages(): Flow<List<SavedImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedImage(image: SavedImageEntity)

    @Query("DELETE FROM saved_images WHERE id = :id")
    suspend fun deleteSavedImage(id: String)

    // Search Queries
    @Query("SELECT * FROM chat_sessions WHERE title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchSessions(query: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMessagesSnapshot(query: String): List<ChatMessageEntity>
}
