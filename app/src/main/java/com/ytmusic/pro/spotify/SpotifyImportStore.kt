package com.ytmusic.pro.spotify

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class SpotifyImportStore(context: Context) {

    private val importFile = File(context.applicationContext.filesDir, FILE_NAME)

    @Throws(Exception::class)
    fun save(importData: JSONObject) {
        OutputStreamWriter(FileOutputStream(importFile, false), StandardCharsets.UTF_8).buffered().use { writer ->
            writer.write(importData.toString(2))
        }
    }

    fun clear() {
        if (importFile.exists()) {
            importFile.delete()
        }
    }

    fun load(): JSONObject? {
        if (!importFile.exists()) {
            return null
        }

        val builder = StringBuilder()
        return try {
            InputStreamReader(FileInputStream(importFile), StandardCharsets.UTF_8).buffered().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    builder.append(line)
                }
            }
            JSONObject(builder.toString())
        } catch (_: Exception) {
            null
        }
    }

    fun loadSummary(): SpotifyImportSummary {
        return SpotifyImportSummary.fromImportData(load())
    }

    private companion object {
        const val FILE_NAME = "spotify_import.json"
    }
}
