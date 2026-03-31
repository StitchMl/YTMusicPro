package com.ytmusic.pro.spotify

import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SpotifyApiImportMapper {

    @Throws(JSONException::class)
    fun buildImportResult(
        playlists: JSONArray,
        savedTracks: JSONArray,
        followedArtists: JSONArray,
    ): JSONObject {
        val result = JSONObject()
        result.put("source", "spotify")
        result.put("importedAt", System.currentTimeMillis())
        result.put("playlists", playlists)

        val favorites = JSONObject()
        favorites.put("savedTracks", savedTracks)
        favorites.put("followedArtists", followedArtists)
        result.put("favorites", favorites)

        val unsupported = JSONArray()
        unsupported.put("Spotify app settings are not exposed by Spotify's official Web API.")
        result.put("unsupported", unsupported)
        return result
    }

    @Throws(JSONException::class)
    fun buildPlaylistImport(playlist: JSONObject, tracks: JSONArray): JSONObject {
        val importedPlaylist = JSONObject()
        importedPlaylist.put("id", playlist.optString("id"))
        importedPlaylist.put("name", playlist.optString("name"))
        importedPlaylist.put("description", playlist.optString("description"))
        importedPlaylist.put("public", playlist.optBoolean("public"))
        importedPlaylist.put("collaborative", playlist.optBoolean("collaborative"))
        importedPlaylist.put("spotifyUrl", extractSpotifyUrl(playlist.optJSONObject("external_urls")))
        importedPlaylist.put("imageUrl", extractFirstImageUrl(playlist.optJSONArray("images")))
        importedPlaylist.put("tracks", tracks)
        return importedPlaylist
    }

    @Throws(JSONException::class)
    fun buildPlaylistTrackImport(playlistItem: JSONObject): JSONObject? {
        val track = playlistItem.optJSONObject("track") ?: return null
        val importedTrack = buildTrackImport(track)
        importedTrack.put("addedAt", playlistItem.optString("added_at"))
        return importedTrack
    }

    @Throws(JSONException::class)
    fun buildSavedTrackImport(item: JSONObject): JSONObject? {
        val track = item.optJSONObject("track") ?: return null
        val importedTrack = buildTrackImport(track)
        importedTrack.put("addedAt", item.optString("added_at"))
        return importedTrack
    }

    @Throws(JSONException::class)
    fun buildArtistImport(artist: JSONObject): JSONObject {
        val importedArtist = JSONObject()
        importedArtist.put("id", artist.optString("id"))
        importedArtist.put("name", artist.optString("name"))
        importedArtist.put("spotifyUrl", extractSpotifyUrl(artist.optJSONObject("external_urls")))
        importedArtist.put("imageUrl", extractFirstImageUrl(artist.optJSONArray("images")))
        return importedArtist
    }

    @Throws(JSONException::class)
    private fun buildTrackImport(track: JSONObject): JSONObject {
        val importedTrack = JSONObject()
        importedTrack.put("id", track.optString("id"))
        importedTrack.put("name", track.optString("name"))
        importedTrack.put("album", extractAlbumName(track.optJSONObject("album")))
        importedTrack.put("artists", extractArtistNames(track.optJSONArray("artists")))
        importedTrack.put("spotifyUrl", extractSpotifyUrl(track.optJSONObject("external_urls")))
        importedTrack.put("uri", track.optString("uri"))
        importedTrack.put("imageUrl", extractAlbumImage(track.optJSONObject("album")))
        return importedTrack
    }

    private fun extractSpotifyUrl(externalUrls: JSONObject?): String {
        return externalUrls?.optString("spotify").orEmpty()
    }

    private fun extractFirstImageUrl(images: JSONArray?): String {
        if (images == null || images.length() == 0) {
            return ""
        }
        val image = images.optJSONObject(0)
        return image?.optString("url").orEmpty()
    }

    private fun extractAlbumImage(album: JSONObject?): String {
        if (album == null) {
            return ""
        }
        return extractFirstImageUrl(album.optJSONArray("images"))
    }

    private fun extractAlbumName(album: JSONObject?): String {
        return album?.optString("name").orEmpty()
    }

    private fun extractArtistNames(artists: JSONArray?): JSONArray {
        val names = JSONArray()
        if (artists == null) {
            return names
        }

        for (i in 0 until artists.length()) {
            val artist = artists.optJSONObject(i) ?: continue
            val name = artist.optString("name")
            if (!TextUtils.isEmpty(name)) {
                names.put(name)
            }
        }
        return names
    }
}
