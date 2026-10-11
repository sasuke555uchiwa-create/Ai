package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.FirestoreChatMessage
import com.example.data.model.FirestoreChatSession
import com.example.data.model.FirestoreUserProfile
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
}
