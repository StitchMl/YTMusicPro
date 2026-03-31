package com.ytmusic.pro.spotify

import android.text.TextUtils
import org.json.JSONArray

class SpotifyLibraryItemMapper {

    fun parsePlaylists(playlistsArray: JSONArray?): List<SpotifyLibraryItem> {
        val items = mutableListOf<SpotifyLibraryItem>()
        if (playlistsArray == null) {
            return items
        }

        for (i in 0 until playlistsArray.length()) {
            val playlist = playlistsArray.optJSONObject(i) ?: continue
            val tracks = playlist.optJSONArray("tracks")
            val subtitle = if (tracks != null) "${tracks.length()} brani" else ""
            val meta = firstNonEmpty(
                playlist.optString("description"),
                if (playlist.optBoolean("collaborative")) "Collaborativa" else "",
            )

            items.add(
                SpotifyLibraryItem(
                    firstNonEmpty(playlist.optString("name"), "Playlist"),
                    subtitle,
                    meta,
                    playlist.optString("spotifyUrl"),
                ),
            )
        }
        return items
    }

    fun parseTracks(tracksArray: JSONArray?): List<SpotifyLibraryItem> {
        val items = mutableListOf<SpotifyLibraryItem>()
        if (tracksArray == null) {
            return items
        }

        for (i in 0 until tracksArray.length()) {
            val track = tracksArray.optJSONObject(i) ?: continue
            items.add(
                SpotifyLibraryItem(
                    firstNonEmpty(track.optString("name"), "Brano"),
                    joinArray(track.optJSONArray("artists")),
                    track.optString("album"),
                    track.optString("spotifyUrl"),
                ),
            )
        }
        return items
    }

    fun parseArtists(artistsArray: JSONArray?): List<SpotifyLibraryItem> {
        val items = mutableListOf<SpotifyLibraryItem>()
        if (artistsArray == null) {
            return items
        }

        for (i in 0 until artistsArray.length()) {
            val artist = artistsArray.optJSONObject(i) ?: continue
            items.add(
                SpotifyLibraryItem(
                    firstNonEmpty(artist.optString("name"), "Artista"),
                    "Artista seguito",
                    "",
                    artist.optString("spotifyUrl"),
                ),
            )
        }
        return items
    }

    fun parseUnsupported(unsupportedArray: JSONArray?): List<String> {
        val notes = mutableListOf<String>()
        if (unsupportedArray == null) {
            return notes
        }

        for (i in 0 until unsupportedArray.length()) {
            val note = unsupportedArray.optString(i)
            if (!TextUtils.isEmpty(note)) {
                notes.add(note)
            }
        }
        return notes
    }

    private fun joinArray(array: JSONArray?): String {
        if (array == null || array.length() == 0) {
            return ""
        }

        val builder = StringBuilder()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (TextUtils.isEmpty(value)) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append(", ")
            }
            builder.append(value)
        }
        return builder.toString()
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
