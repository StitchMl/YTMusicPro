package com.ytmusic.pro.playback

import android.content.Context
import android.content.Intent

object PlaybackActionBroadcaster {

    @JvmStatic
    fun send(context: Context, action: String) {
        val intent = createIntent(context, action)
        context.sendBroadcast(intent, PlaybackControlContract.INTERNAL_PERMISSION)
    }

    @JvmStatic
    fun sendSeek(context: Context, positionSeconds: Long) {
        val intent = createIntent(context, PlaybackControlContract.ACTION_SEEK)
        intent.putExtra(PlaybackControlContract.EXTRA_POSITION, positionSeconds.coerceAtLeast(0))
        context.sendBroadcast(intent, PlaybackControlContract.INTERNAL_PERMISSION)
    }

    private fun createIntent(context: Context, action: String): Intent {
        return Intent(PlaybackControlContract.CONTROL_BROADCAST).apply {
            `package` = context.packageName
            putExtra(PlaybackControlContract.EXTRA_ACTION, action)
        }
    }
}
