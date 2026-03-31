package com.ytmusic.pro.playback

object PlaybackControlContract {
    const val INTERNAL_PERMISSION = "com.ytmusic.pro.permission.INTERNAL_CONTROL"
    const val CONTROL_BROADCAST = "com.ytmusic.pro.PLAYBACK_CONTROL"

    const val ACTION_PLAY = "com.ytmusic.pro.ACTION_PLAY"
    const val ACTION_PAUSE = "com.ytmusic.pro.ACTION_PAUSE"
    const val ACTION_NEXT = "com.ytmusic.pro.ACTION_NEXT"
    const val ACTION_PREV = "com.ytmusic.pro.ACTION_PREV"
    const val ACTION_SEEK = "com.ytmusic.pro.ACTION_SEEK"
    const val ACTION_STOP = "com.ytmusic.pro.ACTION_STOP"

    const val EXTRA_ACTION = "action"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_ALBUM_ART = "albumArt"
    const val EXTRA_IS_PLAYING = "isPlaying"
    const val EXTRA_POSITION = "position"
    const val EXTRA_DURATION = "duration"
}
