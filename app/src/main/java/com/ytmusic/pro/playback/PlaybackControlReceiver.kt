package com.ytmusic.pro.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackControlReceiver(
    private val commandExecutor: PlaybackCommandExecutor,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        commandExecutor.execute(
            intent.getStringExtra(PlaybackControlContract.EXTRA_ACTION),
            intent.getLongExtra(PlaybackControlContract.EXTRA_POSITION, 0),
        )
    }
}
