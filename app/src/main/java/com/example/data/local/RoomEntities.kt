package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.ChatMessage
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
    val sourcesJson: String,
    val extraJson: String = "{}"
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
                    list.add(
                        WebSource(
                            title = obj.optString("title", "Source"),
                            url = obj.optString("url", "")
                        )
                    )
                }
            } catch (e: Exception) {
                // Return fallback empty
            }
            return list
        }

        fun extraToJson(msg: ChatMessage): String {
            val obj = JSONObject()
            msg.toolType?.let { obj.put("toolType", it) }
            msg.generatedImageBase64?.let { obj.put("generatedImageBase64", it) }
            msg.imageAspectRatio?.let { obj.put("imageAspectRatio", it) }
            msg.videoStatus?.let { obj.put("videoStatus", it) }
            msg.videoOperationId?.let { obj.put("videoOperationId", it) }
            msg.attachedFileName?.let { obj.put("attachedFileName", it) }
            msg.comparisonModelA?.let { obj.put("comparisonModelA", it) }
            msg.comparisonModelB?.let { obj.put("comparisonModelB", it) }
            msg.comparisonResponseA?.let { obj.put("comparisonResponseA", it) }
            msg.comparisonResponseB?.let { obj.put("comparisonResponseB", it) }
            msg.latencyA?.let { obj.put("latencyA", it) }
            msg.latencyB?.let { obj.put("latencyB", it) }
            msg.winner?.let { obj.put("winner", it) }
            if (msg.ratingA > 0) obj.put("ratingA", msg.ratingA)
            if (msg.ratingB > 0) obj.put("ratingB", msg.ratingB)
            return obj.toString()
        }

        fun parseExtraJson(entity: ChatMessageEntity): ChatMessage {
            val obj = try {
                if (entity.extraJson.isNotEmpty()) JSONObject(entity.extraJson) else JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }
            return ChatMessage(
                id = entity.id,
                role = entity.role,
                text = entity.text,
                modelId = entity.modelId,
                timestamp = entity.timestamp,
                imageBase64 = entity.imageBase64,
                latencyMs = entity.latencyMs,
                sources = jsonToSources(entity.sourcesJson),
                toolType = if (obj.has("toolType")) obj.getString("toolType") else null,
                generatedImageBase64 = if (obj.has("generatedImageBase64")) obj.getString("generatedImageBase64") else null,
                imageAspectRatio = if (obj.has("imageAspectRatio")) obj.getString("imageAspectRatio") else null,
                videoStatus = if (obj.has("videoStatus")) obj.getString("videoStatus") else null,
                videoOperationId = if (obj.has("videoOperationId")) obj.getString("videoOperationId") else null,
                attachedFileName = if (obj.has("attachedFileName")) obj.getString("attachedFileName") else null,
                comparisonModelA = if (obj.has("comparisonModelA")) obj.getString("comparisonModelA") else null,
                comparisonModelB = if (obj.has("comparisonModelB")) obj.getString("comparisonModelB") else null,
                comparisonResponseA = if (obj.has("comparisonResponseA")) obj.getString("comparisonResponseA") else null,
                comparisonResponseB = if (obj.has("comparisonResponseB")) obj.getString("comparisonResponseB") else null,
                latencyA = if (obj.has("latencyA")) obj.getLong("latencyA") else null,
                latencyB = if (obj.has("latencyB")) obj.getLong("latencyB") else null,
                winner = if (obj.has("winner")) obj.getString("winner") else null,
                ratingA = obj.optInt("ratingA", 0),
                ratingB = obj.optInt("ratingB", 0)
            )
        }
    }
}
