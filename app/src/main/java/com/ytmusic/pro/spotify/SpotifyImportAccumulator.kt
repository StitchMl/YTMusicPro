package com.ytmusic.pro.spotify

import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject

class SpotifyImportAccumulator {

    private val playlists = JSONArray()
    private val savedTracks = JSONArray()
    private val followedArtists = JSONArray()
    private val unsupported = JSONArray()
    private val trackKeys = mutableSetOf<String>()
    private val artistKeys = mutableSetOf<String>()
    private val playlistKeys = mutableSetOf<String>()

    fun appendPlaylist(playlist: JSONObject?) {
        if (playlist == null) {
            return
        }

        val key = firstNonEmpty(playlist.optString("id"), playlist.optString("name"))
        if (TextUtils.isEmpty(key) || playlistKeys.contains(key)) {
            return
        }

        playlistKeys.add(key)
        playlists.put(playlist)
    }

    @Throws(Exception::class)
    fun appendTracks(source: JSONArray?, normalizer: SpotifyImportNormalizer) {
        if (source == null) {
            return
        }

        for (i in 0 until source.length()) {
            val normalizedTrack = normalizer.normalizeTrackObject(source.optJSONObject(i)) ?: continue
            val key = normalizer.buildTrackKey(normalizedTrack)
            if (trackKeys.contains(key)) {
                continue
            }

            trackKeys.add(key)
            savedTracks.put(normalizedTrack)
        }
    }

    @Throws(Exception::class)
    fun appendArtists(source: JSONArray?, normalizer: SpotifyImportNormalizer) {
        if (source == null) {
            return
        }

        for (i in 0 until source.length()) {
            val artist = normalizer.normalizeArtistObject(source.optJSONObject(i)) ?: continue
            val key = firstNonEmpty(artist.optString("id"), artist.optString("name"))
            if (TextUtils.isEmpty(key) || artistKeys.contains(key)) {
                continue
            }

            artistKeys.add(key)
            followedArtists.put(artist)
        }
    }

    fun addUnsupported(message: String?) {
        if (!TextUtils.isEmpty(message)) {
            unsupported.put(message)
        }
    }

    fun addUnsupportedAll(messages: List<String>?) {
        if (messages == null) {
            return
        }

        for (message in messages) {
            addUnsupported(message)
        }
    }

    fun hasData(): Boolean {
        return playlists.length() > 0 || savedTracks.length() > 0 || followedArtists.length() > 0
    }

    @Throws(Exception::class)
    fun toImportData(): JSONObject {
        val favorites = JSONObject()
        favorites.put("savedTracks", savedTracks)
        favorites.put("followedArtists", followedArtists)

        val result = JSONObject()
        result.put("source", "spotify_file_import")
        result.put("importedAt", System.currentTimeMillis())
        result.put("playlists", playlists)
        result.put("favorites", favorites)
        result.put("unsupported", unsupported)
        return result
    }

    private fun firstNonEmpty(vararg values: String): String {
        for (value in values) {
            if (!TextUtils.isEmpty(value)) {
                return value
            }
        }
        return ""
    }
}
