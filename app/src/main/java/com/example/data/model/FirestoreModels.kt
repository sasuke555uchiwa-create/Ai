package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val model: String = "gemini-3.5-flash",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val userId: String = "",
    val sender: String = "user", // "user", "model", "assistant"
    val content: String = "",
    val modelUsed: String? = null,
    val mediaType: String? = null,
    val mediaUrl: String? = null,
    @ServerTimestamp val timestamp: Timestamp? = null
)
