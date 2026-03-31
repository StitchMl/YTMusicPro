package com.ytmusic.pro.playback

import android.content.Intent
import android.text.TextUtils

data class PlaybackSnapshot(
    val title: String?,
    val artist: String?,
    val albumArtUrl: String?,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
) {

    fun hasDisplayableMetadata(): Boolean {
        return !TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist)
    }

    fun withPlaybackState(playing: Boolean): PlaybackSnapshot {
        return copy(isPlaying = playing)
    }

    fun mergeMissingMetadata(fallback: PlaybackSnapshot?): PlaybackSnapshot {
        if (fallback == null) {
            return this
        }

        return PlaybackSnapshot(
            title = if (TextUtils.isEmpty(title)) fallback.title else title,
            artist = if (TextUtils.isEmpty(artist)) fallback.artist else artist,
            albumArtUrl = if (TextUtils.isEmpty(albumArtUrl)) fallback.albumArtUrl else albumArtUrl,
            isPlaying = isPlaying,
            position = if (position > 0) position else fallback.position,
            duration = if (duration > 0) duration else fallback.duration,
        )
    }

    fun sameNotificationContentAs(other: PlaybackSnapshot?): Boolean {
        return other != null &&
            isPlaying == other.isPlaying &&
            duration == other.duration &&
            TextUtils.equals(title, other.title) &&
            TextUtils.equals(artist, other.artist) &&
            TextUtils.equals(albumArtUrl, other.albumArtUrl)
    }

    companion object {
        @JvmStatic
        fun fromIntent(intent: Intent): PlaybackSnapshot {
            return PlaybackSnapshot(
                title = intent.getStringExtra(PlaybackControlContract.EXTRA_TITLE),
                artist = intent.getStringExtra(PlaybackControlContract.EXTRA_ARTIST),
                albumArtUrl = intent.getStringExtra(PlaybackControlContract.EXTRA_ALBUM_ART),
                isPlaying = intent.getBooleanExtra(PlaybackControlContract.EXTRA_IS_PLAYING, true),
                position = intent.getLongExtra(PlaybackControlContract.EXTRA_POSITION, 0),
                duration = intent.getLongExtra(PlaybackControlContract.EXTRA_DURATION, 0),
            )
        }

        @JvmStatic
        fun create(
            title: String?,
            artist: String?,
            albumArtUrl: String?,
            isPlaying: Boolean,
            position: Long,
            duration: Long,
        ): PlaybackSnapshot {
            return PlaybackSnapshot(title, artist, albumArtUrl, isPlaying, position, duration)
        }

        @JvmStatic
        fun empty(): PlaybackSnapshot = PlaybackSnapshot(null, null, null, false, 0, 0)
    }
}
