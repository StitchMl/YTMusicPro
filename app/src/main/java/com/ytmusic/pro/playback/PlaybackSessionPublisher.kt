package com.ytmusic.pro.playback

import android.app.Notification
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import com.ytmusic.pro.album.AlbumArtRepository

class PlaybackSessionPublisher(
    private val mediaSession: MediaSessionCompat,
    private val notificationFactory: PlaybackNotificationFactory,
    private val albumArtRepository: AlbumArtRepository,
    private val listener: Listener,
) {

    interface Listener {
        fun onNotificationReady(notification: Notification)
    }

    interface SnapshotProvider {
        fun getCurrentSnapshot(): PlaybackSnapshot
    }

    fun restoreMetadata(snapshot: PlaybackSnapshot) {
        if (!snapshot.hasDisplayableMetadata()) {
            return
        }
        mediaSession.setMetadata(buildMetadata(snapshot, albumArtRepository.getCached(snapshot.albumArtUrl)))
    }

    fun publish(snapshot: PlaybackSnapshot, snapshotProvider: SnapshotProvider) {
        if (!snapshot.hasDisplayableMetadata()) {
            return
        }

        val cachedAlbumArt = albumArtRepository.getCached(snapshot.albumArtUrl)
        apply(snapshot, cachedAlbumArt)

        if (TextUtils.isEmpty(snapshot.albumArtUrl) || albumArtRepository.isCached(snapshot.albumArtUrl)) {
            return
        }

        albumArtRepository.load(
            snapshot.albumArtUrl,
            object : AlbumArtRepository.Callback {
                override fun onLoaded(url: String?, bitmap: Bitmap?) {
                    val currentSnapshot = snapshotProvider.getCurrentSnapshot()
                    if (TextUtils.equals(currentSnapshot.albumArtUrl, url)) {
                        apply(currentSnapshot, bitmap)
                    }
                }
            },
        )
    }

    fun refresh(snapshot: PlaybackSnapshot) {
        if (!snapshot.hasDisplayableMetadata()) {
            return
        }

        listener.onNotificationReady(
            notificationFactory.build(
                mediaSession,
                snapshot,
                albumArtRepository.getCached(snapshot.albumArtUrl),
            ),
        )
    }

    private fun apply(snapshot: PlaybackSnapshot, albumArt: Bitmap?) {
        mediaSession.setMetadata(buildMetadata(snapshot, albumArt))
        listener.onNotificationReady(notificationFactory.build(mediaSession, snapshot, albumArt))
    }

    private fun buildMetadata(snapshot: PlaybackSnapshot, albumArt: Bitmap?): MediaMetadataCompat {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, snapshot.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, snapshot.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, snapshot.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.duration)

        if (!TextUtils.isEmpty(snapshot.albumArtUrl)) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, snapshot.albumArtUrl)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, snapshot.albumArtUrl)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, snapshot.albumArtUrl)
        }
        if (albumArt != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, albumArt)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArt)
        }
        return metadataBuilder.build()
    }
}
