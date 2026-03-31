package com.ytmusic.pro.jam

import com.ytmusic.pro.playback.PlaybackSnapshot
import org.json.JSONArray
import org.json.JSONObject

data class JamSessionState(
    val active: Boolean,
    val roomCode: String?,
    val joinUrl: String?,
    val currentSnapshot: PlaybackSnapshot,
    val currentJamEntry: JamQueueEntry?,
    val queue: List<JamQueueEntry>,
) {

    fun toJson(): JSONObject {
        val queueJson = JSONArray()
        for (entry in queue) {
            queueJson.put(entry.toJson())
        }

        return JSONObject()
            .put("active", active)
            .put("roomCode", roomCode ?: "")
            .put("joinUrl", joinUrl ?: "")
            .put("nowPlaying", snapshotToJson(currentSnapshot))
            .put("currentJamEntry", currentJamEntry?.toJson() ?: JSONObject.NULL)
            .put("queue", queueJson)
    }

    private fun snapshotToJson(snapshot: PlaybackSnapshot): JSONObject {
        return JSONObject()
            .put("title", snapshot.title ?: "")
            .put("artist", snapshot.artist ?: "")
            .put("albumArtUrl", snapshot.albumArtUrl ?: "")
            .put("isPlaying", snapshot.isPlaying)
            .put("position", snapshot.position)
            .put("duration", snapshot.duration)
    }

    companion object {
        @JvmStatic
        fun inactive(snapshot: PlaybackSnapshot = PlaybackSnapshot.empty()): JamSessionState {
            return JamSessionState(
                active = false,
                roomCode = null,
                joinUrl = null,
                currentSnapshot = snapshot,
                currentJamEntry = null,
                queue = emptyList(),
            )
        }
    }
}
