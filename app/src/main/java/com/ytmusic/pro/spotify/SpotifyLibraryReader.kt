package com.ytmusic.pro.spotify

import android.content.Context

class SpotifyLibraryReader(context: Context) {

    private val importStore = SpotifyImportStore(context)
    private val itemMapper = SpotifyLibraryItemMapper()

    fun load(): SpotifyLibraryData {
        val importData = importStore.load() ?: return SpotifyLibraryData.empty()
        val favorites = importData.optJSONObject("favorites")
        val savedTracks = favorites?.optJSONArray("savedTracks")
        val followedArtists = favorites?.optJSONArray("followedArtists")

        return SpotifyLibraryData(
            importData.optString("source"),
            importData.optLong("importedAt", 0),
            SpotifyImportSummary.fromImportData(importData),
            itemMapper.parseUnsupported(importData.optJSONArray("unsupported")),
            itemMapper.parsePlaylists(importData.optJSONArray("playlists")),
            itemMapper.parseTracks(savedTracks),
            itemMapper.parseArtists(followedArtists),
        )
    }
}
