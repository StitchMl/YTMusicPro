package com.ytmusic.pro.jam

import org.json.JSONObject

data class JamSearchResult(
    val videoId: String,
    val title: String,
    val channel: String?,
    val durationText: String?,
    val thumbnailUrl: String?,
    val mediaUrl: String,
) {

    fun toJson(): JSONObject {
        return JSONObject()
            .put("videoId", videoId)
            .put("title", title)
            .put("channel", channel ?: "")
            .put("durationText", durationText ?: "")
            .put("thumbnailUrl", thumbnailUrl ?: "")
            .put("mediaUrl", mediaUrl)
    }
}
