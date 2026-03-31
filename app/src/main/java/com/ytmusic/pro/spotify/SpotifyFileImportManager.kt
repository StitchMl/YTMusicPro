package com.ytmusic.pro.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

class SpotifyFileImportManager(context: Context) {

    interface Callback {
        fun onImportCompleted(summary: SpotifyImportSummary)

        fun onImportFailed(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val importStore = SpotifyImportStore(appContext)
    private val parser = SpotifyExportFileParser(appContext)

    fun createOpenDocumentIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/json",
                    "application/zip",
                    "application/x-zip-compressed",
                    "text/csv",
                    "text/comma-separated-values",
                    "application/vnd.ms-excel",
                    "text/plain",
                ),
            )
        }
    }

    fun importSelection(data: Intent?, callback: Callback) {
        val uris = extractUris(data)
        if (uris.isEmpty()) {
            callback.onImportFailed("nessun file selezionato")
            return
        }

        takePermissions(uris, data?.flags ?: 0)

        Thread(
            {
                try {
                    val importData = parser.parse(uris)
                    importStore.save(importData)
                    val summary = SpotifyImportSummary.fromImportData(importData)
                    mainHandler.post { callback.onImportCompleted(summary) }
                } catch (e: Exception) {
                    mainHandler.post { callback.onImportFailed(e.message ?: "errore di import") }
                }
            },
            "spotify-file-import",
        ).start()
    }

    private fun extractUris(data: Intent?): List<Uri> {
        val uris = mutableListOf<Uri>()
        if (data == null) {
            return uris
        }

        data.data?.let(uris::add)

        val clipData = data.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                if (uri != null && !uris.contains(uri)) {
                    uris.add(uri)
                }
            }
        }
        return uris
    }

    private fun takePermissions(uris: List<Uri>, flags: Int) {
        var persistFlags =
            flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (persistFlags == 0) {
            persistFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        for (uri in uris) {
            try {
                appContext.contentResolver.takePersistableUriPermission(uri, persistFlags)
            } catch (_: Exception) {
                // Some providers do not support persistable permissions.
            }
        }
    }
}
