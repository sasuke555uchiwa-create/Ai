package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.AppDao
import com.example.data.local.AppTypeConverters
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.ChatMessage
import com.example.data.model.FirestoreChatMessage
import com.example.data.model.FirestoreChatSession
import com.example.data.model.FirestoreUserProfile
import com.example.data.model.MessageRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // Save or update user profile document
    suspend fun saveUserProfile(profile: FirestoreUserProfile) {
        val docRef = db.collection("users").document(profile.uid)
        try {
            val payload = mapOf(
                "uid" to profile.uid,
                "email" to profile.email,
                "displayName" to profile.displayName,
                "photoUrl" to profile.photoUrl,
                "createdAt" to (profile.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Observe user's chat sessions
    fun observeSessions(userId: String): Flow<List<FirestoreChatSession>> {
        return db.collection("users").document(userId)
            .collection("sessions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirestoreChatSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    // Create or update a chat session
    suspend fun saveSession(userId: String, session: FirestoreChatSession) {
        val docRef = db.collection("users").document(userId)
            .collection("sessions").document(session.id)
        try {
            val payload = mapOf(
                "id" to session.id,
                "userId" to userId,
                "title" to session.title,
                "model" to session.model,
                "createdAt" to (session.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Save message inside a session
    suspend fun saveMessage(userId: String, sessionId: String, message: FirestoreChatMessage) {
        val docRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages").document(message.id)
        try {
            val payload = mapOf(
                "id" to message.id,
                "sessionId" to sessionId,
                "userId" to userId,
                "sender" to message.sender,
                "content" to message.content,
                "modelUsed" to message.modelUsed,
                "mediaType" to message.mediaType,
                "mediaUrl" to message.mediaUrl,
                "timestamp" to (message.timestamp ?: FieldValue.serverTimestamp())
            ).filterValues { it != null }

            docRef.set(payload).await()

            // Update parent session's updatedAt timestamp
            val sessionRef = db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
            sessionRef.update("updatedAt", FieldValue.serverTimestamp()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Observe messages in a session
    fun observeMessages(userId: String, sessionId: String): Flow<List<FirestoreChatMessage>> {
        return db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirestoreChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    // Delete session and its messages
    suspend fun deleteSession(userId: String, sessionId: String) {
        val sessionRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
        try {
            // Delete messages subcollection documents
            val messagesSnapshot = sessionRef.collection("messages").get().await()
            for (msgDoc in messagesSnapshot.documents) {
                msgDoc.reference.delete().await()
            }
            sessionRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionRef.path)
            throw e
        }
    }

    // One-shot fetch user's chat sessions
    suspend fun getSessions(userId: String): List<FirestoreChatSession> {
        val colRef = db.collection("users").document(userId).collection("sessions")
        return try {
            val snapshot = colRef.orderBy("updatedAt", Query.Direction.DESCENDING).get().await()
            snapshot.toObjects(FirestoreChatSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, colRef.path)
            emptyList()
        }
    }

    // One-shot fetch messages in a session
    suspend fun getMessages(userId: String, sessionId: String): List<FirestoreChatMessage> {
        val colRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages")
        return try {
            val snapshot = colRef.orderBy("timestamp", Query.Direction.ASCENDING).get().await()
            snapshot.toObjects(FirestoreChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, colRef.path)
            emptyList()
        }
    }

    // Restore authenticated user's cloud history and merge into Room
    suspend fun syncUserHistoryToRoom(userId: String, dao: AppDao): Result<Int> {
        return try {
            val cloudSessions = getSessions(userId)
            var totalMessagesSynced = 0

            for (session in cloudSessions) {
                if (session.id.isBlank()) continue

                val createdAt = session.createdAt?.toDate()?.time
                    ?: session.updatedAt?.toDate()?.time
                    ?: System.currentTimeMillis()

                val sessionEntity = ChatSessionEntity(
                    id = session.id,
                    title = session.title.ifBlank { "Chat" },
                    selectedModelId = session.model.ifBlank { "gemini-3.5-flash" },
                    searchGroundingEnabled = false,
                    createdAt = createdAt
                )
                dao.insertSession(sessionEntity)

                val cloudMessages = getMessages(userId, session.id)
                for (msg in cloudMessages) {
                    if (msg.id.isBlank()) continue
                    val msgTimestamp = msg.timestamp?.toDate()?.time ?: System.currentTimeMillis()
                    val role = if (msg.sender.equals("user", ignoreCase = true)) MessageRole.USER else MessageRole.ASSISTANT

                    val chatMsg = ChatMessage(
                        id = msg.id,
                        role = role,
                        text = msg.content,
                        modelId = msg.modelUsed,
                        timestamp = msgTimestamp,
                        imageBase64 = if (msg.mediaType?.startsWith("image") == true) msg.mediaUrl else null
                    )

                    val extraJson = AppTypeConverters.extraToJson(chatMsg)
                    val msgEntity = ChatMessageEntity(
                        id = msg.id,
                        sessionId = msg.sessionId.ifBlank { session.id },
                        role = role,
                        text = msg.content,
                        modelId = msg.modelUsed,
                        timestamp = msgTimestamp,
                        imageBase64 = if (msg.mediaType?.startsWith("image") == true) msg.mediaUrl else null,
                        latencyMs = null,
                        sourcesJson = "[]",
                        extraJson = extraJson
                    )
                    dao.insertMessage(msgEntity)
                    totalMessagesSynced++
                }
            }
            Result.success(cloudSessions.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreChatRepo", "Failed to sync user history to Room: ${e.message}", e)
            Result.failure(e)
        }
    }
}
