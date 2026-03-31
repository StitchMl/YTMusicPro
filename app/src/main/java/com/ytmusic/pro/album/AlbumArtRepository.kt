package com.ytmusic.pro.album

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.text.TextUtils

class AlbumArtRepository {

    interface Callback {
        fun onLoaded(url: String?, bitmap: Bitmap?)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null

    @Synchronized
    fun isCached(url: String?): Boolean {
        return !TextUtils.isEmpty(url) && TextUtils.equals(url, cachedUrl)
    }

    @Synchronized
    fun getCached(url: String?): Bitmap? {
        if (!TextUtils.equals(url, cachedUrl)) {
            return null
        }
        return cachedBitmap
    }

    fun load(url: String?, callback: Callback) {
        if (TextUtils.isEmpty(url) || isCached(url)) {
            val cached = getCached(url)
            mainHandler.post { callback.onLoaded(url, cached) }
            return
        }

        Thread(
            {
                val downloadedBitmap = AlbumArtDownloader.download(url)
                synchronized(this) {
                    cachedUrl = url
                    cachedBitmap = downloadedBitmap
                }
                mainHandler.post { callback.onLoaded(url, downloadedBitmap) }
            },
            "album-art-loader",
        ).start()
    }
}
