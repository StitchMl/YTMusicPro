package com.ytmusic.pro.web.webapp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.ytmusic.pro.ForegroundService
import com.ytmusic.pro.playback.PlaybackControlContract

class WebAppBridge(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun showToast(message: String?) {
        mainHandler.post { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun updateNotification(
        title: String?,
        artist: String?,
        albumArtUrl: String?,
        isPlaying: Boolean,
        position: Long,
        duration: Long,
    ) {
        val serviceIntent = Intent(appContext, ForegroundService::class.java).apply {
            putExtra(PlaybackControlContract.EXTRA_TITLE, title)
            putExtra(PlaybackControlContract.EXTRA_ARTIST, artist)
            putExtra(PlaybackControlContract.EXTRA_ALBUM_ART, albumArtUrl)
            putExtra(PlaybackControlContract.EXTRA_IS_PLAYING, isPlaying)
            putExtra(PlaybackControlContract.EXTRA_POSITION, position)
            putExtra(PlaybackControlContract.EXTRA_DURATION, duration)
        }

        val hasDisplayableMetadata = !TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist)
        mainHandler.post {
            if (hasDisplayableMetadata) {
                ContextCompat.startForegroundService(appContext, serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
        }
    }
}
