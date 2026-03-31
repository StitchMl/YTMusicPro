package com.ytmusic.pro.spotify

import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class SpotifyImportNormalizer {

    @Throws(Exception::class)
    fun buildGroupedPlaylist(array: JSONArray): JSONObject {
        val grouped = JSONObject()
        val tracks = JSONArray()
        var playlistName = "Spotify Playlist"
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.has("playlistName")) {
                playlistName = firstNonEmpty(item.optString("playlistName"), playlistName)
            } else if (item.has("playlist_name")) {
                playlistName = firstNonEmpty(item.optString("playlist_name"), playlistName)
            }

            val normalizedTrack = normalizeTrackObject(item)
            if (normalizedTrack != null) {
                tracks.put(normalizedTrack)
            }
        }
        grouped.put("name", playlistName)
        grouped.put("tracks", tracks)
        return grouped
    }

    @Throws(Exception::class)
    fun buildSyntheticPlaylist(fileName: String, trackArray: JSONArray): JSONObject {
        val playlist = JSONObject()
        playlist.put("name", sanitizeFileName(fileName))
        playlist.put("tracks", normalizeTracks(trackArray))
        return playlist
    }

    @Throws(Exception::class)
    fun normalizePlaylistObject(playlist: JSONObject?): JSONObject? {
        if (playlist == null) {
            return null
        }

        val normalized = JSONObject()
        normalized.put("id", firstNonEmpty(playlist.optString("id"), playlist.optString("uri")))
        normalized.put(
            "name",
            firstNonEmpty(playlist.optString("name"), playlist.optString("playlistName"), "Spotify Playlist"),
        )
        normalized.put("description", playlist.optString("description"))
        normalized.put(
            "spotifyUrl",
            firstNonEmpty(playlist.optString("spotifyUrl"), playlist.optString("spotify_url")),
        )
        normalized.put(
            "imageUrl",
            firstNonEmpty(playlist.optString("imageUrl"), playlist.optString("image_url")),
        )

        var tracks = playlist.optJSONArray("tracks")
        if (tracks == null) {
            tracks = playlist.optJSONArray("items")
        }
        normalized.put("tracks", normalizeTracks(tracks))
        return normalized
    }

    @Throws(Exception::class)
    fun normalizeArtistObject(source: JSONObject?): JSONObject? {
        if (source == null) {
            return null
        }

        val artist = JSONObject()
        artist.put("id", firstNonEmpty(source.optString("id"), source.optString("uri")))
        artist.put("name", firstNonEmpty(source.optString("name"), source.optString("artistName")))
        artist.put("spotifyUrl", firstNonEmpty(source.optString("spotifyUrl"), source.optString("spotify_url")))
        artist.put("imageUrl", firstNonEmpty(source.optString("imageUrl"), source.optString("image_url")))
        return if (TextUtils.isEmpty(firstNonEmpty(artist.optString("id"), artist.optString("name")))) {
            null
        } else {
            artist
        }
    }

    @Throws(Exception::class)
    fun normalizeTracks(tracks: JSONArray?): JSONArray {
        val normalizedTracks = JSONArray()
        if (tracks == null) {
            return normalizedTracks
        }

        for (i in 0 until tracks.length()) {
            val normalizedTrack = normalizeTrackObject(tracks.optJSONObject(i))
            if (normalizedTrack != null) {
                normalizedTracks.put(normalizedTrack)
            }
        }
        return normalizedTracks
    }

    @Throws(Exception::class)
    fun normalizeTrackObject(source: JSONObject?): JSONObject? {
        if (source == null) {
            return null
        }

        val trackNode = source.optJSONObject("track")
        val track = trackNode ?: source

        val name = firstNonEmpty(
            track.optString("name"),
            track.optString("trackName"),
            track.optString("track_name"),
            track.optString("master_metadata_track_name"),
            track.optString("title"),
        )
        if (TextUtils.isEmpty(name)) {
            return null
        }

        val normalized = JSONObject()
        normalized.put(
            "id",
            firstNonEmpty(track.optString("id"), track.optString("uri"), track.optString("spotify_track_uri")),
        )
        normalized.put("name", name)
        normalized.put(
            "album",
            firstNonEmpty(
                track.optString("album"),
                track.optString("albumName"),
                track.optString("album_name"),
                track.optString("master_metadata_album_album_name"),
                extractAlbumName(track.optJSONObject("album")),
            ),
        )
        normalized.put("artists", extractArtists(track))
        normalized.put(
            "spotifyUrl",
            firstNonEmpty(
                track.optString("spotifyUrl"),
                track.optString("spotify_url"),
                extractSpotifyUrl(track.optJSONObject("external_urls")),
            ),
        )
        normalized.put("uri", firstNonEmpty(track.optString("uri"), track.optString("spotify_track_uri")))
        normalized.put(
            "imageUrl",
            firstNonEmpty(
                track.optString("imageUrl"),
                track.optString("image_url"),
                extractAlbumImage(track.optJSONObject("album")),
            ),
        )

        val addedAt = firstNonEmpty(
            source.optString("addedAt"),
            source.optString("added_at"),
            track.optString("addedAt"),
            track.optString("endTime"),
            track.optString("ts"),
        )
        if (!TextUtils.isEmpty(addedAt)) {
            normalized.put("addedAt", addedAt)
        }
        return normalized
    }

    @Throws(Exception::class)
    fun buildTrackFromCsv(columns: Map<String, Int>, row: Array<String>): JSONObject? {
        val name = valueAt(row, columns, "track", "track name", "name", "title")
        if (TextUtils.isEmpty(name)) {
            return null
        }

        val track = JSONObject()
        track.put("name", name)
        track.put("album", valueAt(row, columns, "album", "album name"))
        track.put("spotifyUrl", valueAt(row, columns, "spotify url", "url"))
        track.put("uri", valueAt(row, columns, "uri", "spotify uri", "track uri"))
        track.put("imageUrl", valueAt(row, columns, "image", "image url", "cover"))
        track.put("artists", splitCsvArtists(valueAt(row, columns, "artist", "artist name", "artists")))
        return track
    }

    fun mapColumns(header: Array<String>): Map<String, Int> {
        val columns = mutableMapOf<String, Int>()
        for (i in header.indices) {
            columns[header[i].trim().lowercase(Locale.US)] = i
        }
        return columns
    }

    fun hasRecognizedTrackColumns(columns: Map<String, Int>): Boolean {
        return columns.containsKey("track") ||
            columns.containsKey("track name") ||
            columns.containsKey("name") ||
            columns.containsKey("title")
    }

    fun valueAt(row: Array<String>, columns: Map<String, Int>, vararg aliases: String): String {
        for (alias in aliases) {
            val index = columns[alias]
            if (index != null && index >= 0 && index < row.size) {
                return row[index].trim()
            }
        }
        return ""
    }

    fun buildTrackKey(track: JSONObject): String {
        val builder = StringBuilder()
        builder.append(firstNonEmpty(track.optString("uri"), track.optString("id"), track.optString("name")))
        builder.append('|')
        builder.append(track.optString("album"))
        builder.append('|')
        val artists = track.optJSONArray("artists")
        if (artists != null) {
            for (i in 0 until artists.length()) {
                builder.append(artists.optString(i)).append(',')
            }
        }
        return builder.toString()
    }

    private fun extractArtists(track: JSONObject): JSONArray {
        val artists = JSONArray()
        val artistArray = track.optJSONArray("artists")
        if (artistArray != null && artistArray.length() > 0) {
            for (i in 0 until artistArray.length()) {
                when (val item = artistArray.opt(i)) {
                    is JSONObject -> {
                        val name = item.optString("name")
                        if (!TextUtils.isEmpty(name)) {
                            artists.put(name)
                        }
                    }
                    is String -> artists.put(item)
                }
            }
        }

        if (artists.length() == 0) {
            val artistValue = firstNonEmpty(
                track.optString("artist"),
                track.optString("artistName"),
                track.optString("artist_name"),
                track.optString("master_metadata_album_artist_name"),
            )
            if (!TextUtils.isEmpty(artistValue)) {
                for (token in artistValue.split(",")) {
                    val trimmed = token.trim()
                    if (trimmed.isNotEmpty()) {
                        artists.put(trimmed)
                    }
                }
            }
        }
        return artists
    }

    private fun splitCsvArtists(value: String): JSONArray {
        val artists = JSONArray()
        if (TextUtils.isEmpty(value)) {
            return artists
        }
        for (artist in value.split(",")) {
            val trimmed = artist.trim()
            if (trimmed.isNotEmpty()) {
                artists.put(trimmed)
            }
        }
        return artists
    }

    private fun sanitizeFileName(fileName: String): String {
        var baseName = fileName
        val pathSeparatorIndex = maxOf(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'))
        if (pathSeparatorIndex >= 0 && pathSeparatorIndex + 1 < baseName.length) {
            baseName = baseName.substring(pathSeparatorIndex + 1)
        }
        val extensionIndex = baseName.lastIndexOf('.')
        return if (extensionIndex > 0) baseName.substring(0, extensionIndex) else baseName
    }

    private fun firstNonEmpty(vararg candidates: String): String {
        for (candidate in candidates) {
            if (!TextUtils.isEmpty(candidate)) {
                return candidate
            }
        }
        return ""
    }

    private fun extractSpotifyUrl(externalUrls: JSONObject?): String {
        return externalUrls?.optString("spotify").orEmpty()
    }

    private fun extractAlbumImage(album: JSONObject?): String {
        if (album == null) {
            return ""
        }
        val images = album.optJSONArray("images")
        if (images == null || images.length() == 0) {
            return ""
        }
        val image = images.optJSONObject(0)
        return image?.optString("url").orEmpty()
    }

    private fun extractAlbumName(album: JSONObject?): String {
        return album?.optString("name").orEmpty()
    }
}
