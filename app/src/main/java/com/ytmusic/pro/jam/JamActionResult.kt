package com.ytmusic.pro.jam

import org.json.JSONObject

data class JamActionResult(
    val success: Boolean,
    val message: String,
    val state: JamSessionState,
) {

    fun toJson(): JSONObject {
        return JSONObject()
            .put("success", success)
            .put("message", message)
            .put("state", state.toJson())
    }
}
