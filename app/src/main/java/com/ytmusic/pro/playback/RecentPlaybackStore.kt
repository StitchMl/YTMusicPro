package com.ytmusic.pro.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class RecentPlaybackStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: PlaybackSnapshot) {
        if (!snapshot.hasDisplayableMetadata()) {
            return
        }

        preferences.edit {
            putString(KEY_TITLE, snapshot.title)
                .putString(KEY_ARTIST, snapshot.artist)
                .putString(KEY_ALBUM_ART_URL, snapshot.albumArtUrl)
                .putLong(KEY_DURATION, snapshot.duration)
        }
    }

    fun load(): PlaybackSnapshot {
        return PlaybackSnapshot.create(
            preferences.getString(KEY_TITLE, null),
            preferences.getString(KEY_ARTIST, null),
            preferences.getString(KEY_ALBUM_ART_URL, null),
            false,
            0,
            preferences.getLong(KEY_DURATION, 0),
        )
    }

    private companion object {
        const val PREFS_NAME = "recent_playback"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM_ART_URL = "albumArtUrl"
        const val KEY_DURATION = "duration"
    }
}
