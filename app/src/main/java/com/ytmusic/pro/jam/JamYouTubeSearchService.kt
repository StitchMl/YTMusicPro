package com.ytmusic.pro.jam

import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class JamYouTubeSearchService {

    fun search(query: String?, limit: Int = DEFAULT_LIMIT): List<JamSearchResult> {
        val normalizedQuery = query?.trim().orEmpty()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        return runCatching {
            val renderers = mutableListOf<JSONObject>()
            collectTrackRenderers(fetchResults(normalizedQuery), renderers, limit)

            val results = LinkedHashMap<String, JamSearchResult>()
            for (renderer in renderers) {
                val videoId = findFirstVideoId(renderer)
                val title = extractTitle(renderer)
                if (videoId.isEmpty() || title.isEmpty() || results.containsKey(videoId)) {
                    continue
                }

                val mediaUrl = JamTrackUrlNormalizer.normalize(videoId) ?: continue
                val metadata = extractMetadata(renderer)
                results[videoId] = JamSearchResult(
                    videoId = videoId,
                    title = title,
                    channel = metadata.channel,
                    durationText = metadata.durationText,
                    thumbnailUrl = extractThumbnailUrl(renderer),
                    mediaUrl = mediaUrl,
                )
                if (results.size >= limit) {
                    break
                }
            }
            results.values.toList()
        }.getOrElse { emptyList() }
    }

    private fun fetchResults(query: String): JSONObject {
        val payload = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", CLIENT_NAME)
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", LANGUAGE)
                        .put("gl", COUNTRY),
                ),
            )
            .put("query", query)
            .put("params", SONG_SEARCH_PARAMS)

        val connection = (URL("$SEARCH_ENDPOINT$API_KEY").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "$LANGUAGE-$COUNTRY,$LANGUAGE;q=0.9,en-US;q=0.8,en;q=0.7")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Origin", ORIGIN)
            setRequestProperty("Referer", "$ORIGIN/")
        }

        buildCookieHeader().takeIf(String::isNotBlank)?.let { cookieHeader ->
            connection.setRequestProperty("Cookie", cookieHeader)
        }

        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val responseStream = runCatching { connection.inputStream }.getOrElse { connection.errorStream }
            ?: return JSONObject()
        val responseText = responseStream.use(::readText)
        return runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
    }

    private fun readText(inputStream: InputStream): String {
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            buildString {
                while (true) {
                    val line = reader.readLine() ?: break
                    append(line)
                }
            }
        }
    }

    private fun collectTrackRenderers(
        node: Any?,
        sink: MutableList<JSONObject>,
        limit: Int,
    ) {
        if (node == null || sink.size >= limit) {
            return
        }

        when (node) {
            is JSONObject -> {
                val itemRenderer = node.optJSONObject("musicResponsiveListItemRenderer")
                if (itemRenderer != null && looksLikeTrack(itemRenderer)) {
                    sink += itemRenderer
                    if (sink.size >= limit) {
                        return
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    collectTrackRenderers(node.opt(keys.next()), sink, limit)
                    if (sink.size >= limit) {
                        return
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectTrackRenderers(node.opt(index), sink, limit)
                    if (sink.size >= limit) {
                        return
                    }
                }
            }
        }
    }

    private fun looksLikeTrack(renderer: JSONObject): Boolean {
        return findFirstVideoId(renderer).isNotEmpty() && extractTitle(renderer).isNotEmpty()
    }

    private fun findFirstVideoId(node: Any?): String {
        return when (node) {
            is JSONObject -> {
                val directVideoId = node.optString("videoId").trim()
                if (VIDEO_ID_PATTERN.matches(directVideoId)) {
                    return directVideoId
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val nestedVideoId = findFirstVideoId(node.opt(keys.next()))
                    if (nestedVideoId.isNotEmpty()) {
                        return nestedVideoId
                    }
                }
                ""
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    val nestedVideoId = findFirstVideoId(node.opt(index))
                    if (nestedVideoId.isNotEmpty()) {
                        return nestedVideoId
                    }
                }
                ""
            }
            else -> ""
        }
    }

    private fun extractTitle(renderer: JSONObject): String {
        val flexColumns = renderer.optJSONArray("flexColumns") ?: return ""
        val titleText = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        return extractExactText(titleText).trim()
    }

    private fun extractMetadata(renderer: JSONObject): SearchMetadata {
        val flexColumns = renderer.optJSONArray("flexColumns") ?: return SearchMetadata()
        val metaText = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val chunks = extractExactText(metaText)
            .split(BULLET_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)

        return SearchMetadata(
            channel = chunks.firstOrNull { !DURATION_PATTERN.matches(it) },
            durationText = chunks.lastOrNull { DURATION_PATTERN.matches(it) },
        )
    }

    private fun extractExactText(node: JSONObject?): String {
        if (node == null) {
            return ""
        }

        val simpleText = node.optString("simpleText").trim()
        if (simpleText.isNotEmpty()) {
            return simpleText
        }

        val runs = node.optJSONArray("runs") ?: return ""
        val builder = StringBuilder()
        for (index in 0 until runs.length()) {
            builder.append(runs.optJSONObject(index)?.optString("text").orEmpty())
        }
        return builder.toString()
    }

    private fun extractThumbnailUrl(renderer: JSONObject): String? {
        val thumbnail = renderer.optJSONObject("thumbnail")
        val thumbnails = thumbnail
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: thumbnail?.optJSONArray("thumbnails")
            ?: return null
        val candidate = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url").orEmpty()
        return when {
            candidate.isBlank() -> null
            candidate.startsWith("//") -> "https:$candidate"
            else -> candidate
        }
    }

    private fun buildCookieHeader(): String {
        val cookies = linkedSetOf(CONSENT_COOKIE)
        runCatching {
            CookieManager.getInstance().getCookie(ORIGIN).orEmpty()
        }.getOrNull()?.takeIf(String::isNotBlank)?.let(cookies::add)
        return cookies.joinToString("; ")
    }

    private companion object {
        const val SEARCH_ENDPOINT =
            "https://music.youtube.com/youtubei/v1/search?prettyPrint=false&key="
        const val ORIGIN = "https://music.youtube.com"
        const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_VERSION = "1.20260327.10.00"
        const val LANGUAGE = "it"
        const val COUNTRY = "IT"
        const val SONG_SEARCH_PARAMS = "EgWKAQIIAWoSEAkQAxAFEAQQEBAKEA4QFRAR"
        const val CONSENT_COOKIE = "CONSENT=YES+cb.20210328-17-p0.it+FX+111"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        const val TIMEOUT_MS = 15000
        const val DEFAULT_LIMIT = 12
        const val MIN_QUERY_LENGTH = 2
        const val BULLET_SEPARATOR = "•"
        val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        val DURATION_PATTERN = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")
    }

    private data class SearchMetadata(
        val channel: String? = null,
        val durationText: String? = null,
    )
}
