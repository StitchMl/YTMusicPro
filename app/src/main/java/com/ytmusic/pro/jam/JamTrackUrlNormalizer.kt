package com.ytmusic.pro.jam

import android.net.Uri
import java.util.Locale

object JamTrackUrlNormalizer {

    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    @JvmStatic
    fun normalize(rawInput: String?): String? {
        val input = rawInput?.trim().orEmpty()
        if (input.isEmpty()) {
            return null
        }

        if (videoIdPattern.matches(input)) {
            return buildMusicUrl(input)
        }

        val candidate = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }

        val uri = try {
            Uri.parse(candidate)
        } catch (_: Exception) {
            null
        } ?: return null

        val host = uri.host?.lowercase(Locale.US) ?: return null
        val videoId =
            when {
                host == "youtu.be" || host.endsWith(".youtu.be") -> uri.lastPathSegment
                host == "youtube.com" || host.endsWith(".youtube.com") ||
                    host == "music.youtube.com" || host.endsWith(".music.youtube.com") -> {
                    uri.getQueryParameter("v") ?: shortsVideoId(uri)
                }
                else -> null
            }?.trim()

        if (videoId.isNullOrEmpty() || !videoIdPattern.matches(videoId)) {
            return null
        }
        return buildMusicUrl(videoId)
    }

    private fun shortsVideoId(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.size >= 2 && segments[0] == "shorts") {
            segments[1]
        } else {
            null
        }
    }

    private fun buildMusicUrl(videoId: String): String {
        return "https://music.youtube.com/watch?v=${Uri.encode(videoId)}"
    }
}
