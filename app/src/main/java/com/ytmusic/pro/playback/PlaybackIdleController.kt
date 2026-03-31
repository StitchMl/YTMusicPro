package com.ytmusic.pro.playback

import android.os.Handler

class PlaybackIdleController(
    private val handler: Handler,
    private val timeoutMs: Long,
    private val listener: Listener,
) {

    interface Listener {
        fun onIdleTimeout()
    }

    private var idleRunnable: Runnable? = null

    fun update(isPlaying: Boolean) {
        cancel()
        if (isPlaying) {
            return
        }

        idleRunnable = Runnable(listener::onIdleTimeout)
        handler.postDelayed(idleRunnable!!, timeoutMs)
    }

    fun cancel() {
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
    }
}
