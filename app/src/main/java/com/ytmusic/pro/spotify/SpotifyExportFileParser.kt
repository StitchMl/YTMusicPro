package com.ytmusic.pro.spotify

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class SpotifyExportFileParser(context: Context) {

    private val payloadReader = SpotifyImportPayloadReader(context)
    private val normalizer = SpotifyImportNormalizer()

    @Throws(Exception::class)
    fun parse(uris: List<Uri>): JSONObject {
        val accumulator = SpotifyImportAccumulator()

        for (uri in uris) {
            val fileName = payloadReader.getDisplayName(uri)
            val readResult = payloadReader.read(uri, fileName)
            accumulator.addUnsupportedAll(readResult.notices)

            for (payload in readResult.payloads) {
                parsePayloadSafely(payload, accumulator)
            }
        }

        if (!accumulator.hasData()) {
            throw IllegalArgumentException("Nessun dato Spotify riconosciuto nei file selezionati.")
        }
        return accumulator.toImportData()
    }

    @Throws(Exception::class)
    private fun parsePayloadSafely(
        payload: SpotifyImportPayloadReader.ImportPayload,
        accumulator: SpotifyImportAccumulator,
    ) {
        val content = payload.content
        if (content.trim().isEmpty()) {
            return
        }

        val normalizedFileName = payload.fileName.lowercase(Locale.US)
        try {
            if (looksLikeJson(content, normalizedFileName)) {
                parseJsonContent(content, payload.fileName, normalizedFileName, accumulator)
            } else {
                parseCsvContent(content, payload.fileName, normalizedFileName, accumulator)
            }
        } catch (e: Exception) {
            accumulator.addUnsupported("File saltato: ${payload.fileName} (${buildFriendlyMessage(e)})")
        }
    }

    @Throws(Exception::class)
    private fun parseJsonContent(
        content: String,
        fileName: String,
        normalizedFileName: String,
        accumulator: SpotifyImportAccumulator,
    ) {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            parseJsonObject(JSONObject(trimmed), fileName, normalizedFileName, accumulator)
            return
        }

        parseJsonArray(JSONArray(trimmed), fileName, normalizedFileName, accumulator)
    }

    @Throws(Exception::class)
    private fun parseJsonObject(
        obj: JSONObject,
        fileName: String,
        normalizedFileName: String,
        accumulator: SpotifyImportAccumulator,
    ) {
        appendPlaylists(obj.optJSONArray("playlists"), accumulator)

        val favorites = obj.optJSONObject("favorites")
        if (favorites != null) {
            accumulator.appendTracks(favorites.optJSONArray("savedTracks"), normalizer)
            accumulator.appendArtists(favorites.optJSONArray("followedArtists"), normalizer)
        }

        accumulator.appendTracks(obj.optJSONArray("savedTracks"), normalizer)
        accumulator.appendTracks(obj.optJSONArray("likedTracks"), normalizer)
        accumulator.appendArtists(obj.optJSONArray("followedArtists"), normalizer)
        accumulator.appendArtists(obj.optJSONArray("artists"), normalizer)

        if (looksLikeSinglePlaylist(obj)) {
            accumulator.appendPlaylist(normalizer.normalizePlaylistObject(obj))
            return
        }

        handleTrackLikeArray(obj.optJSONArray("tracks"), fileName, normalizedFileName, accumulator)
        handleItemsArray(obj.optJSONArray("items"), fileName, normalizedFileName, accumulator)

        if (obj.has("unsupported")) {
            accumulator.addUnsupported("Import con fedelta limitata da $fileName")
        }
    }

    @Throws(Exception::class)
    private fun appendPlaylists(playlistsArray: JSONArray?, accumulator: SpotifyImportAccumulator) {
        if (playlistsArray == null) {
            return
        }

        for (i in 0 until playlistsArray.length()) {
            accumulator.appendPlaylist(normalizer.normalizePlaylistObject(playlistsArray.optJSONObject(i)))
        }
    }

    @Throws(Exception::class)
    private fun handleTrackLikeArray(
        trackArray: JSONArray?,
        fileName: String,
        normalizedFileName: String,
        accumulator: SpotifyImportAccumulator,
    ) {
        if (trackArray == null) {
            return
        }

        if (isSavedTracksFile(normalizedFileName)) {
            accumulator.appendTracks(trackArray, normalizer)
            return
        }

        accumulator.appendPlaylist(normalizer.buildSyntheticPlaylist(fileName, trackArray))
    }

    @Throws(Exception::class)
    private fun handleItemsArray(
        itemsArray: JSONArray?,
        fileName: String,
        normalizedFileName: String,
        accumulator: SpotifyImportAccumulator,
    ) {
        if (itemsArray == null) {
            return
        }

        if (isSavedTracksFile(normalizedFileName)) {
            accumulator.appendTracks(itemsArray, normalizer)
            return
        }

        if (normalizedFileName.contains("playlist")) {
            accumulator.appendPlaylist(normalizer.buildSyntheticPlaylist(fileName, itemsArray))
        }
    }

    @Throws(Exception::class)
    private fun parseJsonArray(
        array: JSONArray,
        fileName: String,
        normalizedFileName: String,
        accumulator: SpotifyImportAccumulator,
    ) {
        if (array.length() == 0) {
            return
        }

        val firstObject = array.optJSONObject(0) ?: return

        if (firstObject.has("playlistName") || firstObject.has("playlist_name")) {
            accumulator.appendPlaylist(normalizer.buildGroupedPlaylist(array))
            return
        }

        if (isSavedTracksFile(normalizedFileName)) {
            accumulator.appendTracks(array, normalizer)
            return
        }

        if (normalizedFileName.contains("streaming")) {
            accumulator.appendPlaylist(
                normalizer.buildSyntheticPlaylist("Cronologia di ascolto - $fileName", array),
            )
            accumulator.addUnsupported("Cronologia di ascolto importata come playlist sintetica da $fileName")
            return
        }

        if (normalizedFileName.contains("playlist")) {
            accumulator.appendPlaylist(normalizer.buildSyntheticPlaylist(fileName, array))
            return
        }

        accumulator.appendTracks(array, normalizer)
    }

    @Throws(Exception::class)
    private fun parseCsvContent(
        content: String,
        fileName: String,
        normalizedFileName: String,
        accumulator: SpotifyImportAccumulator,
    ) {
        val rows = parseCsvRows(content)
        if (rows.size < 2) {
            throw IllegalArgumentException("CSV senza righe dati in $fileName")
        }

        val columns = normalizer.mapColumns(rows[0])
        if (!normalizer.hasRecognizedTrackColumns(columns)) {
            throw IllegalArgumentException("Formato CSV non riconosciuto in $fileName")
        }

        val tracks = JSONArray()
        for (i in 1 until rows.size) {
            val track = normalizer.buildTrackFromCsv(columns, rows[i])
            if (track != null) {
                tracks.put(track)
            }
        }

        if (tracks.length() == 0) {
            throw IllegalArgumentException("CSV senza tracce importabili in $fileName")
        }

        if (isSavedTracksFile(normalizedFileName)) {
            accumulator.appendTracks(tracks, normalizer)
        } else {
            accumulator.appendPlaylist(normalizer.buildSyntheticPlaylist(fileName, tracks))
        }
        accumulator.addUnsupported("Import CSV da $fileName: alcuni metadati Spotify potrebbero mancare.")
    }

    private fun parseCsvRows(content: String): List<Array<String>> {
        val rows = mutableListOf<Array<String>>()
        val cell = StringBuilder()
        var currentRow = mutableListOf<String>()
        var inQuotes = false

        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '"') {
                if (inQuotes && i + 1 < content.length && content[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                currentRow.add(cell.toString())
                cell.setLength(0)
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                    i++
                }
                currentRow.add(cell.toString())
                cell.setLength(0)
                rows.add(currentRow.toTypedArray())
                currentRow = mutableListOf()
            } else {
                cell.append(c)
            }
            i++
        }

        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            rows.add(currentRow.toTypedArray())
        }
        return rows
    }

    private fun looksLikeJson(content: String, normalizedFileName: String): Boolean {
        val trimmed = content.trim()
        return normalizedFileName.endsWith(".json") || trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun looksLikeSinglePlaylist(obj: JSONObject): Boolean {
        return obj.has("name") && (obj.has("tracks") || obj.has("items")) && !obj.has("playlists")
    }

    private fun isSavedTracksFile(normalizedFileName: String): Boolean {
        return normalizedFileName.contains("liked") ||
            normalizedFileName.contains("library") ||
            normalizedFileName.contains("saved")
    }

    private fun buildFriendlyMessage(e: Exception): String {
        val message = e.message
        return if (message != null && message.trim().isNotEmpty()) message else "errore non specificato"
    }
}
