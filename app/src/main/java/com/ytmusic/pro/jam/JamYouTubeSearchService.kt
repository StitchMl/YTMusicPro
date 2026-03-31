package com.ytmusic.pro.jam

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JamYouTubeSearchService {

    fun search(query: String?, limit: Int = DEFAULT_LIMIT): List<JamSearchResult> {
        val normalizedQuery = query?.trim().orEmpty()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        val html = fetchResultsPage(normalizedQuery)
        val initialData = extractInitialData(html) ?: return emptyList()
        val root = JSONObject(initialData)

        val renderers = mutableListOf<JSONObject>()
        collectVideoRenderers(root, renderers, limit)

        val results = LinkedHashMap<String, JamSearchResult>()
        for (renderer in renderers) {
            val videoId = renderer.optString("videoId").trim()
            val title = extractText(renderer.optJSONObject("title")).orEmpty().trim()
            if (videoId.isEmpty() || title.isEmpty() || results.containsKey(videoId)) {
                continue
            }

            val mediaUrl = JamTrackUrlNormalizer.normalize(videoId) ?: continue
            val searchResult = JamSearchResult(
                videoId = videoId,
                title = title,
                channel = extractText(
                    renderer.optJSONObject("ownerText")
                        ?: renderer.optJSONObject("longBylineText")
                        ?: renderer.optJSONObject("shortBylineText"),
                ),
                durationText = extractText(renderer.optJSONObject("lengthText")),
                thumbnailUrl = extractThumbnailUrl(renderer.optJSONObject("thumbnail")),
                mediaUrl = mediaUrl,
            )
            results[videoId] = searchResult
            if (results.size >= limit) {
                break
            }
        }
        return results.values.toList()
    }

    private fun fetchResultsPage(query: String): String {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val connection = (URL("$SEARCH_ENDPOINT$encodedQuery").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
        }

        return connection.inputStream.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line)
                    }
                }
            }
        }
    }

    private fun extractInitialData(html: String): String? {
        for (pattern in INITIAL_DATA_PATTERNS) {
            val startIndex = html.indexOf(pattern.prefix)
            if (startIndex < 0) {
                continue
            }

            val contentStart = startIndex + pattern.prefix.length
            val endIndex = html.indexOf(pattern.suffix, contentStart)
            if (endIndex > contentStart) {
                return html.substring(contentStart, endIndex)
            }
        }
        return null
    }

    private fun collectVideoRenderers(
        node: Any?,
        sink: MutableList<JSONObject>,
        limit: Int,
    ) {
        if (node == null || sink.size >= limit) {
            return
        }

        when (node) {
            is JSONObject -> {
                val videoRenderer = node.optJSONObject("videoRenderer")
                if (videoRenderer != null) {
                    sink += videoRenderer
                    if (sink.size >= limit) {
                        return
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectVideoRenderers(node.opt(keys.next()), sink, limit)
                    if (sink.size >= limit) {
                        return
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectVideoRenderers(node.opt(index), sink, limit)
                    if (sink.size >= limit) {
                        return
                    }
                }
            }
        }
    }

    private fun extractText(node: JSONObject?): String? {
        if (node == null) {
            return null
        }

        val simpleText = node.optString("simpleText").trim()
        if (simpleText.isNotEmpty()) {
            return simpleText
        }

        val runs = node.optJSONArray("runs") ?: return null
        val builder = StringBuilder()
        for (index in 0 until runs.length()) {
            val text = runs.optJSONObject(index)?.optString("text").orEmpty()
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) {
                    builder.append(' ')
                }
                builder.append(text.trim())
            }
        }
        return builder.toString().ifEmpty { null }
    }

    private fun extractThumbnailUrl(thumbnail: JSONObject?): String? {
        val thumbnails = thumbnail?.optJSONArray("thumbnails") ?: return null
        val candidate = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url").orEmpty()
        return when {
            candidate.isBlank() -> null
            candidate.startsWith("//") -> "https:$candidate"
            else -> candidate
        }
    }

    private companion object {
        const val SEARCH_ENDPOINT = "https://www.youtube.com/results?search_query="
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        const val TIMEOUT_MS = 15000
        const val DEFAULT_LIMIT = 12
        const val MIN_QUERY_LENGTH = 2
        val INITIAL_DATA_PATTERNS = listOf(
            InitialDataPattern(
                prefix = "var ytInitialData = ",
                suffix = ";</script>",
            ),
            InitialDataPattern(
                prefix = "window[\"ytInitialData\"] = ",
                suffix = ";</script>",
            ),
        )
    }

    private data class InitialDataPattern(
        val prefix: String,
        val suffix: String,
    )
}
