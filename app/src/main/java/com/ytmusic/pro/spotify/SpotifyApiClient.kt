package com.ytmusic.pro.spotify

import android.net.Uri
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

@Suppress("DataFlowIssue")
class SpotifyApiClient {

    private val httpClient = SpotifyApiHttpClient()
    private val importMapper = SpotifyApiImportMapper()

    @Throws(IOException::class, JSONException::class)
    fun exchangeCodeForToken(code: String, verifier: String, authConfig: SpotifyAuthConfig): String {
        val response = httpClient.postForm(TOKEN_URL, buildTokenRequestBody(code, verifier, authConfig))
        val accessToken = response.optString("access_token")
        if (TextUtils.isEmpty(accessToken)) {
            throw IOException("Spotify token response missing access_token")
        }
        return accessToken
    }

    @Throws(IOException::class, JSONException::class)
    fun importLibrary(accessToken: String): JSONObject {
        return importMapper.buildImportResult(
            fetchPlaylists(accessToken),
            fetchSavedTracks(accessToken),
            fetchFollowedArtists(accessToken),
        )
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchPlaylists(accessToken: String): JSONArray {
        val playlists = JSONArray()
        var offset = 0
        var nextUrl: String?

        do {
            nextUrl = "$API_BASE_URL/me/playlists?limit=$PLAYLIST_PAGE_SIZE&offset=$offset"
            val page = getJson(nextUrl, accessToken)
            val items = page.optJSONArray("items")
            if (items == null || items.length() == 0) {
                break
            }

            for (i in 0 until items.length()) {
                val playlist = items.optJSONObject(i) ?: continue
                playlists.put(
                    importMapper.buildPlaylistImport(
                        playlist,
                        fetchPlaylistTracks(playlist.optString("id"), accessToken),
                    ),
                )
            }

            offset += items.length()
            nextUrl = page.optString("next").takeIf { it.isNotEmpty() }
        } while (!TextUtils.isEmpty(nextUrl))

        return playlists
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchPlaylistTracks(playlistId: String?, accessToken: String): JSONArray {
        val tracks = JSONArray()
        if (TextUtils.isEmpty(playlistId)) {
            return tracks
        }

        var offset = 0
        var nextUrl: String?

        do {
            nextUrl = "$API_BASE_URL/playlists/${Uri.encode(playlistId)}/tracks?limit=$TRACK_PAGE_SIZE&offset=$offset"
            val page = getJson(nextUrl, accessToken)
            val items = page.optJSONArray("items")
            if (items == null || items.length() == 0) {
                break
            }

            for (i in 0 until items.length()) {
                val playlistItem = items.optJSONObject(i) ?: continue
                val importedTrack = importMapper.buildPlaylistTrackImport(playlistItem)
                if (importedTrack != null) {
                    tracks.put(importedTrack)
                }
            }

            offset += items.length()
            nextUrl = page.optString("next").takeIf { it.isNotEmpty() }
        } while (!TextUtils.isEmpty(nextUrl))

        return tracks
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchSavedTracks(accessToken: String): JSONArray {
        val tracks = JSONArray()
        var offset = 0
        var nextUrl: String?

        do {
            nextUrl = "$API_BASE_URL/me/tracks?limit=$TRACK_PAGE_SIZE&offset=$offset"
            val page = getJson(nextUrl, accessToken)
            val items = page.optJSONArray("items")
            if (items == null || items.length() == 0) {
                break
            }

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val importedTrack = importMapper.buildSavedTrackImport(item)
                if (importedTrack != null) {
                    tracks.put(importedTrack)
                }
            }

            offset += items.length()
            nextUrl = page.optString("next").takeIf { it.isNotEmpty() }
        } while (!TextUtils.isEmpty(nextUrl))

        return tracks
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchFollowedArtists(accessToken: String): JSONArray {
        val artists = JSONArray()
        var after: String? = null

        while (true) {
            val urlBuilder = StringBuilder(API_BASE_URL).append("/me/following?type=artist&limit=50")
            if (!TextUtils.isEmpty(after)) {
                urlBuilder.append("&after=").append(Uri.encode(after))
            }

            val page = getJson(urlBuilder.toString(), accessToken)
            val artistsObject = page.optJSONObject("artists") ?: break

            val items = artistsObject.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val artist = items.optJSONObject(i) ?: continue
                    artists.put(importMapper.buildArtistImport(artist))
                }
            }

            val cursors = artistsObject.optJSONObject("cursors")
            after = cursors?.optString("after")?.takeIf { it.isNotEmpty() }
            if (TextUtils.isEmpty(after)) {
                break
            }
        }

        return artists
    }

    @Throws(IOException::class, JSONException::class)
    private fun getJson(url: String, accessToken: String): JSONObject {
        return httpClient.getJson(url, accessToken)
    }

    private fun buildTokenRequestBody(code: String, verifier: String, authConfig: SpotifyAuthConfig): String {
        return "client_id=${Uri.encode(authConfig.clientId)}" +
            "&grant_type=authorization_code" +
            "&code=${Uri.encode(code)}" +
            "&redirect_uri=${Uri.encode(authConfig.redirectUri)}" +
            "&code_verifier=${Uri.encode(verifier)}"
    }

    private companion object {
        const val API_BASE_URL = "https://api.spotify.com/v1"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val PLAYLIST_PAGE_SIZE = 50
        const val TRACK_PAGE_SIZE = 50
    }
}
