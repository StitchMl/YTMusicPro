package com.ytmusic.pro.spotify

import org.json.JSONObject
import java.text.DateFormat
import java.util.Locale

data class SpotifyImportSummary(
    val playlistCount: Int,
    val playlistTrackCount: Int,
    val savedTrackCount: Int,
    val followedArtistCount: Int,
    val importedAt: Long,
    val samplePlaylistNames: List<String>,
) {

    fun hasData(): Boolean {
        return playlistCount > 0 || savedTrackCount > 0 || followedArtistCount > 0
    }

    fun toDisplayText(): String {
        val builder = StringBuilder()
        builder.append(String.format(Locale.getDefault(), "Playlist importate: %d", playlistCount))
        builder.append('\n')
        builder.append(String.format(Locale.getDefault(), "Brani nelle playlist: %d", playlistTrackCount))
        builder.append('\n')
        builder.append(String.format(Locale.getDefault(), "Brani salvati: %d", savedTrackCount))
        builder.append('\n')
        builder.append(String.format(Locale.getDefault(), "Artisti seguiti: %d", followedArtistCount))

        if (samplePlaylistNames.isNotEmpty()) {
            builder.append("\n\nPlaylist esempio:\n")
            for (name in samplePlaylistNames) {
                builder.append("- ").append(name).append('\n')
            }
        }

        builder.append("\nSetting Spotify: non importabili via API ufficiale.")
        if (importedAt > 0) {
            builder.append("\nUltimo import: ")
                .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(importedAt))
        }
        return builder.toString().trim()
    }

    companion object {
        @JvmStatic
        fun empty(): SpotifyImportSummary = SpotifyImportSummary(0, 0, 0, 0, 0, emptyList())

        @JvmStatic
        fun fromImportData(importData: JSONObject?): SpotifyImportSummary {
            if (importData == null) {
                return empty()
            }

            val playlists = importData.optJSONArray("playlists")
            val favorites = importData.optJSONObject("favorites")
            val savedTracks = favorites?.optJSONArray("savedTracks")
            val followedArtists = favorites?.optJSONArray("followedArtists")

            var playlistTrackCount = 0
            val sampleNames = mutableListOf<String>()
            if (playlists != null) {
                for (i in 0 until playlists.length()) {
                    val playlist = playlists.optJSONObject(i) ?: continue
                    val tracks = playlist.optJSONArray("tracks")
                    playlistTrackCount += tracks?.length() ?: 0
                    val playlistName = playlist.optString("name")
                    if (sampleNames.size < 5 && playlistName.isNotEmpty()) {
                        sampleNames.add(playlistName)
                    }
                }
            }

            return SpotifyImportSummary(
                playlists?.length() ?: 0,
                playlistTrackCount,
                savedTracks?.length() ?: 0,
                followedArtists?.length() ?: 0,
                importData.optLong("importedAt", 0),
                sampleNames,
            )
        }
    }
}
