package com.ytmusic.pro.jam

import android.content.Context
import com.ytmusic.pro.AssetFileLoader
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class JamHttpServer(
    context: Context,
    port: Int,
    private val controller: Controller,
) : NanoHTTPD(port) {

    interface Controller {
        fun currentState(): JamSessionState

        fun searchTracks(query: String?): List<JamSearchResult>

        fun enqueueTrack(
            rawUrl: String?,
            requestedBy: String?,
            label: String?,
            thumbnailUrl: String?,
        ): JamActionResult

        fun playTrack(requestedBy: String?): JamActionResult

        fun pauseTrack(requestedBy: String?): JamActionResult

        fun skipTrack(requestedBy: String?): JamActionResult

        fun stopTrack(requestedBy: String?): JamActionResult
    }

    private val appContext = context.applicationContext

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") -> {
                    textResponse(
                        content = AssetFileLoader.loadText(appContext, "jam_guest.html"),
                        mimeType = "text/html; charset=utf-8",
                    )
                }
                session.method == Method.GET && session.uri == "/jam.js" -> {
                    textResponse(
                        content = AssetFileLoader.loadText(appContext, "jam_guest.js"),
                        mimeType = "application/javascript; charset=utf-8",
                    )
                }
                session.method == Method.GET && session.uri == "/api/state" -> {
                    jsonResponse(controller.currentState().toJson())
                }
                session.method == Method.GET && session.uri == "/api/search" -> {
                    val query = session.parameters["q"]?.firstOrNull()
                    val resultsJson = org.json.JSONArray()
                    for (result in controller.searchTracks(query)) {
                        resultsJson.put(result.toJson())
                    }
                    jsonResponse(
                        JSONObject()
                            .put("success", true)
                            .put("results", resultsJson),
                    )
                }
                session.method == Method.POST && session.uri == "/api/queue" -> {
                    val body = readJsonBody(session)
                    jsonResponse(
                        controller.enqueueTrack(
                            rawUrl = body.optString("mediaUrl"),
                            requestedBy = body.optString("submittedBy"),
                            label = body.optString("label"),
                            thumbnailUrl = body.optString("thumbnailUrl"),
                        ).toJson(),
                    )
                }
                session.method == Method.POST && session.uri == "/api/play" -> {
                    val body = readJsonBody(session)
                    jsonResponse(controller.playTrack(body.optString("submittedBy")).toJson())
                }
                session.method == Method.POST && session.uri == "/api/pause" -> {
                    val body = readJsonBody(session)
                    jsonResponse(controller.pauseTrack(body.optString("submittedBy")).toJson())
                }
                session.method == Method.POST && session.uri == "/api/skip" -> {
                    val body = readJsonBody(session)
                    jsonResponse(controller.skipTrack(body.optString("submittedBy")).toJson())
                }
                session.method == Method.POST && session.uri == "/api/stop" -> {
                    val body = readJsonBody(session)
                    jsonResponse(controller.stopTrack(body.optString("submittedBy")).toJson())
                }
                else -> textResponse(
                    content = "Not found",
                    mimeType = "text/plain; charset=utf-8",
                    status = Response.Status.NOT_FOUND,
                )
            }
        } catch (error: Exception) {
            jsonResponse(
                JSONObject()
                    .put("success", false)
                    .put("message", error.message ?: "Unexpected error"),
                status = Response.Status.INTERNAL_ERROR,
            )
        }
    }

    private fun readJsonBody(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val rawBody = files["postData"].orEmpty()
        return if (rawBody.isBlank()) JSONObject() else JSONObject(rawBody)
    }

    private fun jsonResponse(
        jsonObject: JSONObject,
        status: Response.IStatus = Response.Status.OK,
    ): Response {
        return textResponse(
            content = jsonObject.toString(),
            mimeType = "application/json; charset=utf-8",
            status = status,
        )
    }

    private fun textResponse(
        content: String,
        mimeType: String,
        status: Response.IStatus = Response.Status.OK,
    ): Response {
        val response = newFixedLengthResponse(status, mimeType, content)
        response.addHeader("Cache-Control", "no-store")
        return response
    }
}
