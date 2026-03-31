package com.ytmusic.pro.playback

import android.webkit.WebView

class PlaybackCommandExecutor(
    private val webView: WebView,
) {

    fun execute(action: String?, positionSeconds: Long) {
        when (action) {
            PlaybackControlContract.ACTION_PLAY -> evaluate("document.querySelector('video')?.play()")
            PlaybackControlContract.ACTION_PAUSE -> evaluate("document.querySelector('video')?.pause()")
            PlaybackControlContract.ACTION_NEXT -> evaluate("document.querySelector('.next-button')?.click()")
            PlaybackControlContract.ACTION_PREV -> evaluate("document.querySelector('.previous-button')?.click()")
            PlaybackControlContract.ACTION_SEEK -> {
                evaluate(
                    "const video = document.querySelector('video'); if (video) { video.currentTime = " +
                        positionSeconds.coerceAtLeast(0) +
                        "; }",
                )
            }
        }
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript(script, null)
    }
}
