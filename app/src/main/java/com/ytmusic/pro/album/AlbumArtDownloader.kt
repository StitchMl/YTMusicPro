package com.ytmusic.pro.album

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object AlbumArtDownloader {

    @JvmStatic
    fun download(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) {
            return null
        }

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
            )
            connection.doInput = true
            connection.connect()
            connection.inputStream.use(BitmapFactory::decodeStream)
        } catch (e: Exception) {
            Log.e("YTMusicPro", "Error downloading bitmap: ${e.message}")
            null
        }
    }
}
