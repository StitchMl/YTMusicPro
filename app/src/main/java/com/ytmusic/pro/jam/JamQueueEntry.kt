package com.ytmusic.pro.jam

import org.json.JSONObject
import java.util.UUID

data class JamQueueEntry(
    val id: String,
    val mediaUrl: String,
    val submittedBy: String,
    val label: String?,
    val thumbnailUrl: String?,
    val createdAtEpochMillis: Long,
) {

    fun displayTitle(): String {
        return label?.trim().orEmpty().ifEmpty { mediaUrl }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("mediaUrl", mediaUrl)
            .put("submittedBy", submittedBy)
            .put("label", label ?: "")
            .put("thumbnailUrl", thumbnailUrl ?: "")
            .put("displayTitle", displayTitle())
            .put("createdAtEpochMillis", createdAtEpochMillis)
    }

    companion object {
        @JvmStatic
        fun create(
            mediaUrl: String,
            submittedBy: String?,
            label: String?,
            thumbnailUrl: String?,
        ): JamQueueEntry {
            return JamQueueEntry(
                id = UUID.randomUUID().toString(),
                mediaUrl = mediaUrl,
                submittedBy = submittedBy?.trim().orEmpty().ifEmpty { "Guest" },
                label = label?.trim().orEmpty().ifEmpty { null },
                thumbnailUrl = thumbnailUrl?.trim().orEmpty().ifEmpty { null },
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }
}
