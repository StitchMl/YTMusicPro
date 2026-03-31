package com.ytmusic.pro.spotify

data class SpotifyLibraryData(
    val source: String,
    val importedAt: Long,
    val summary: SpotifyImportSummary,
    val unsupportedNotes: List<String>,
    val playlists: List<SpotifyLibraryItem>,
    val savedTracks: List<SpotifyLibraryItem>,
    val followedArtists: List<SpotifyLibraryItem>,
) {

    fun hasData(): Boolean {
        return playlists.isNotEmpty() || savedTracks.isNotEmpty() || followedArtists.isNotEmpty()
    }

    fun getVisibleItemCount(): Int {
        return playlists.size + savedTracks.size + followedArtists.size
    }

    companion object {
        @JvmStatic
        fun empty(): SpotifyLibraryData {
            return SpotifyLibraryData(
                "",
                0,
                SpotifyImportSummary.empty(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }
}
