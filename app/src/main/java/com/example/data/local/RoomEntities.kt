package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.MessageRole
import com.example.data.model.WebSource
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val selectedModelId: String,
    val searchGroundingEnabled: Boolean,
    val createdAt: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val modelId: String?,
    val timestamp: Long,
    val imageBase64: String?,
    val latencyMs: Long?,
    val sourcesJson: String
)

@Entity(tableName = "arena_records")
data class ArenaRecordEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val category: String,
    val modelAId: String,
    val modelBId: String,
    val responseA: String,
    val responseB: String,
    val latencyMsA: Long,
    val latencyMsB: Long,
    val ratingA: Int,
    val ratingB: Int,
    val winner: String?,
    val timestamp: Long
)

@Entity(tableName = "saved_images")
data class SavedImageEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val style: String,
    val aspectRatio: String,
    val imageBase64: String,
    val timestamp: Long
)

class AppTypeConverters {
    @TypeConverter
    fun fromRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toRole(name: String): MessageRole = try {
        MessageRole.valueOf(name)
    } catch (e: Exception) {
        MessageRole.USER
    }

    companion object {
        fun sourcesToJson(sources: List<WebSource>): String {
            val arr = JSONArray()
            sources.forEach { s ->
                val obj = JSONObject()
                obj.put("title", s.title)
                obj.put("url", s.url)
                arr.put(obj)
            }
            return arr.toString()
        }

        fun jsonToSources(json: String?): List<WebSource> {
            if (json.isNullOrEmpty()) return emptyList()
            val list = mutableListOf<WebSource>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(WebSource(
                        title = obj.optString("title", "Source"),
                        url = obj.optString("url", "")
                    ))
                }
            } catch (e: Exception) {
                // Return fallback empty
            }
            return list
        }
    }
}
