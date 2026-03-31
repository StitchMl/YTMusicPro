package com.ytmusic.pro.spotify

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpotifyLibraryExportManager(context: Context) {

    interface Callback {
        fun onExportCompleted(fileName: String)

        fun onExportFailed(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val appContext = context.applicationContext
    private val importStore = SpotifyImportStore(appContext)

    fun buildDefaultFileName(): String {
        val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        return "spotify-library-${format.format(Date())}.json"
    }

    fun exportTo(destinationUri: Uri, callback: Callback) {
        Thread(
            {
                try {
                    val importData = importStore.load()
                        ?: throw IllegalStateException("nessuna libreria importata da esportare")

                    appContext.contentResolver.openOutputStream(destinationUri, "rwt").use { outputStream ->
                        if (outputStream == null) {
                            throw IllegalStateException("impossibile aprire il file di destinazione")
                        }
                        outputStream.write(importData.toString(2).toByteArray(StandardCharsets.UTF_8))
                    }

                    val fileName = destinationUri.lastPathSegment ?: "spotify-library.json"
                    mainHandler.post { callback.onExportCompleted(fileName) }
                } catch (e: Exception) {
                    mainHandler.post {
                        callback.onExportFailed(e.message ?: "errore di esportazione")
                    }
                }
            },
            "spotify-library-export",
        ).start()
    }

    fun clearLibrary() {
        importStore.clear()
    }
}
