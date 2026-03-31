package com.ytmusic.pro.spotify

import android.text.TextUtils
import java.util.Locale

class SpotifyLibraryFilter {

    fun apply(source: SpotifyLibraryData?, query: String?): SpotifyLibraryData {
        if (source == null || !source.hasData()) {
            return SpotifyLibraryData.empty()
        }

        val normalizedQuery = normalize(query)
        if (TextUtils.isEmpty(normalizedQuery)) {
            return source
        }

        return SpotifyLibraryData(
            source.source,
            source.importedAt,
            source.summary,
            ArrayList(source.unsupportedNotes),
            filterItems(source.playlists, normalizedQuery),
            filterItems(source.savedTracks, normalizedQuery),
            filterItems(source.followedArtists, normalizedQuery),
        )
    }

    fun hasActiveQuery(query: String?): Boolean {
        return !TextUtils.isEmpty(normalize(query))
    }

    private fun filterItems(source: List<SpotifyLibraryItem>, query: String): List<SpotifyLibraryItem> {
        val filtered = mutableListOf<SpotifyLibraryItem>()
        for (item in source) {
            if (item.matchesQuery(query)) {
                filtered.add(item)
            }
        }
        return filtered
    }

    private fun normalize(query: String?): String {
        return query?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    }
}
