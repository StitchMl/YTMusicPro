package com.ytmusic.pro.spotify

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextUtils
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

@Suppress("CharsetObjectCanBeUsed")
class SpotifyImportPayloadReader(context: Context) {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    fun getDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        return cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back below.
        }

        return uri.lastPathSegment ?: "spotify_import"
    }

    @Throws(Exception::class)
    fun read(uri: Uri, fileName: String): ReadResult {
        val payloads = mutableListOf<ImportPayload>()
        val notices = mutableListOf<String>()
        val normalizedFileName = fileName.lowercase(Locale.US)
        if (!normalizedFileName.endsWith(".zip")) {
            payloads.add(ImportPayload(fileName, readText(uri)))
            return ReadResult(payloads, notices)
        }

        contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) {
                throw IllegalArgumentException("Impossibile leggere $fileName")
            }

            ZipInputStream(inputStream, StandardCharsets.UTF_8).use { zipInputStream ->
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    if (entry.isDirectory) {
                        zipInputStream.closeEntry()
                        continue
                    }

                    val entryName = entry.name
                    if (!isSupportedImportEntry(entryName)) {
                        zipInputStream.closeEntry()
                        continue
                    }

                    try {
                        payloads.add(ImportPayload(entryName, readZipEntryText(zipInputStream, entryName)))
                    } catch (e: IllegalArgumentException) {
                        notices.add("Voce ZIP saltata: $entryName (${buildFriendlyMessage(e)})")
                    } finally {
                        zipInputStream.closeEntry()
                    }
                }
            }
        }

        if (payloads.isEmpty()) {
            notices.add("Nessun JSON o CSV importabile trovato dentro $fileName.")
        }
        return ReadResult(payloads, notices)
    }

    private fun isSupportedImportEntry(entryName: String): Boolean {
        val normalizedEntryName = entryName.lowercase(Locale.US)
        return normalizedEntryName.endsWith(".json") ||
            normalizedEntryName.endsWith(".csv") ||
            normalizedEntryName.endsWith(".txt")
    }

    @Throws(Exception::class)
    private fun readText(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("impossibile leggere il file selezionato")

        inputStream.use { safeInputStream ->
            InputStreamReader(safeInputStream, StandardCharsets.UTF_8).buffered().use { reader ->
                return readAllText(reader)
            }
        }
    }

    @Throws(Exception::class)
    private fun readZipEntryText(zipInputStream: ZipInputStream, entryName: String): String {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalBytes = 0
        while (true) {
            val read = zipInputStream.read(buffer)
            if (read == -1) {
                break
            }
            totalBytes += read
            if (totalBytes > MAX_ZIP_ENTRY_BYTES) {
                throw IllegalArgumentException("voce troppo grande: $entryName")
            }
            outputStream.write(buffer, 0, read)
        }
        return outputStream.toString(StandardCharsets.UTF_8.name())
    }

    @Throws(Exception::class)
    private fun readAllText(reader: BufferedReader): String {
        val builder = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            builder.append(line).append('\n')
        }
        return builder.toString()
    }

    private fun buildFriendlyMessage(e: Exception): String {
        val message = e.message
        return if (TextUtils.isEmpty(message)) "errore non specificato" else message!!
    }

    data class ReadResult(
        val payloads: List<ImportPayload>,
        val notices: List<String>,
    )

    data class ImportPayload(
        val fileName: String,
        val content: String,
    )

    private companion object {
        const val MAX_ZIP_ENTRY_BYTES = 20 * 1024 * 1024
    }
}
