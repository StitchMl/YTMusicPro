package com.ytmusic.pro.spotify

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@Suppress("ConstantValue", "NonStrictComparisonCanBeEquality")
class SpotifyApiHttpClient {

    @Throws(IOException::class, JSONException::class)
    fun getJson(url: String, accessToken: String): JSONObject {
        return executeJsonRequest(url, "GET", accessToken, null, false)
    }

    @Throws(IOException::class, JSONException::class)
    fun postForm(url: String, requestBody: String): JSONObject {
        return executeJsonRequest(url, "POST", null, requestBody, true)
    }

    @Throws(IOException::class, JSONException::class)
    private fun executeJsonRequest(
        url: String,
        method: String,
        accessToken: String?,
        requestBody: String?,
        isTokenRequest: Boolean,
    ): JSONObject {
        var lastException: IOException? = null

        for (attempt in 0 until MAX_RETRIES) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "YTMusicPro/2.0")

                if (!TextUtils.isEmpty(accessToken)) {
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                }

                if (!TextUtils.isEmpty(requestBody)) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.outputStream.use { outputStream ->
                        outputStream.write(requestBody!!.toByteArray(StandardCharsets.UTF_8))
                    }
                }

                val responseCode = connection.responseCode
                val body = readStream(
                    if (responseCode in 200..299) connection.inputStream else connection.errorStream,
                )

                if (responseCode in 200..299) {
                    return JSONObject(body)
                }

                if (shouldRetry(responseCode, isTokenRequest) && attempt < MAX_RETRIES - 1) {
                    sleepBeforeRetry(connection, responseCode, attempt)
                    continue
                }

                throw IOException(extractApiError(body, responseCode))
            } catch (e: IOException) {
                lastException = e
                if (attempt >= MAX_RETRIES - 1) {
                    throw e
                }
                sleepQuietly((attempt + 1L) * DEFAULT_RETRY_DELAY_MS)
            } finally {
                connection?.disconnect()
            }
        }

        throw lastException ?: IOException("Unknown Spotify API error")
    }

    private fun shouldRetry(responseCode: Int, isTokenRequest: Boolean): Boolean {
        if (responseCode == 429) {
            return true
        }
        if (isTokenRequest) {
            return responseCode in 500..599
        }
        return responseCode == 500 || responseCode == 502 || responseCode == 503 || responseCode == 504
    }

    private fun sleepBeforeRetry(connection: HttpURLConnection, responseCode: Int, attempt: Int) {
        if (responseCode == 429) {
            val retryAfter = connection.getHeaderField("Retry-After")
            if (!TextUtils.isEmpty(retryAfter)) {
                try {
                    sleepQuietly(kotlin.math.max(1, retryAfter!!.toInt()) * 1000L)
                    return
                } catch (_: NumberFormatException) {
                    // Fall back to incremental delay below.
                }
            }
        }
        sleepQuietly((attempt + 1L) * DEFAULT_RETRY_DELAY_MS)
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @Throws(IOException::class)
    private fun readStream(inputStream: InputStream?): String {
        if (inputStream == null) {
            return ""
        }

        val builder = StringBuilder()
        InputStreamReader(inputStream, StandardCharsets.UTF_8).buffered().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                builder.append(line)
            }
        }
        return builder.toString()
    }

    private fun extractApiError(responseBody: String, responseCode: Int): String {
        if (!TextUtils.isEmpty(responseBody)) {
            try {
                val jsonObject = JSONObject(responseBody)
                if (jsonObject.has("error_description")) {
                    return jsonObject.optString("error_description")
                }
                val error = jsonObject.optJSONObject("error")
                if (error != null && error.has("message")) {
                    return error.optString("message")
                }
                if (jsonObject.has("error")) {
                    return jsonObject.optString("error")
                }
            } catch (_: Exception) {
                // Fall back to generic message.
            }
        }

        return if (responseCode == 429) {
            "Spotify rate limit reached. Retry in a moment."
        } else {
            "Spotify API error ($responseCode)"
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val DEFAULT_RETRY_DELAY_MS = 1200
    }
}
