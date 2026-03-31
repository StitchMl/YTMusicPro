@file:Suppress("DEPRECATION")

package com.ytmusic.pro.playback

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import com.ytmusic.pro.MainActivity
import com.ytmusic.pro.web.WebSecurityPolicy

class PlaybackBrowserService : MediaBrowserServiceCompat() {

    private lateinit var browserCatalog: PlaybackBrowserCatalog
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var recentPlaybackStore: RecentPlaybackStore

    override fun onCreate() {
        super.onCreate()
        browserCatalog = PlaybackBrowserCatalog()
        recentPlaybackStore = RecentPlaybackStore(this)
        mediaSession =
            MediaSessionCompat(this, "YTMusicProBrowserSession").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
                )
                setCallback(createSessionCallback())
                setMetadata(buildMetadata(recentPlaybackStore.load()))
                isActive = false
            }
        setSessionToken(mediaSession.sessionToken)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
        return browserCatalog.createRoot(rootHints)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val recentSnapshot = recentPlaybackStore.load()
        mediaSession.setMetadata(buildMetadata(recentSnapshot))
        result.sendResult(
            browserCatalog.createChildren(parentId, recentSnapshot, recentSnapshot).toMutableList(),
        )
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val recentSnapshot = recentPlaybackStore.load()
        result.sendResult(
            browserCatalog.createChildren("ytmusicpro.root.recents", recentSnapshot, recentSnapshot).toMutableList(),
        )
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    private fun createSessionCallback(): MediaSessionCompat.Callback {
        return object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                launchPlayerUi()
                PlaybackActionBroadcaster.send(this@PlaybackBrowserService, PlaybackControlContract.ACTION_PLAY)
            }

            override fun onPause() {
                PlaybackActionBroadcaster.send(this@PlaybackBrowserService, PlaybackControlContract.ACTION_PAUSE)
            }

            override fun onSkipToNext() {
                PlaybackActionBroadcaster.send(this@PlaybackBrowserService, PlaybackControlContract.ACTION_NEXT)
            }

            override fun onSkipToPrevious() {
                PlaybackActionBroadcaster.send(this@PlaybackBrowserService, PlaybackControlContract.ACTION_PREV)
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                if (browserCatalog.isRecentMediaId(mediaId)) {
                    launchPlayerUi()
                }
                PlaybackActionBroadcaster.send(this@PlaybackBrowserService, PlaybackControlContract.ACTION_PLAY)
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                launchPlayerUi()
                PlaybackActionBroadcaster.send(this@PlaybackBrowserService, PlaybackControlContract.ACTION_PLAY)
            }
        }
    }

    private fun launchPlayerUi() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = WebSecurityPolicy.MUSIC_URL.toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        startActivity(intent)
    }

    private fun buildMetadata(snapshot: PlaybackSnapshot): MediaMetadataCompat {
        val description =
            MediaDescriptionCompat.Builder()
                .setMediaId("ytmusicpro.media.recent")
                .setTitle(snapshot.title ?: "YTMusic Pro")
                .setSubtitle(snapshot.artist ?: "Music")
                .setMediaUri(WebSecurityPolicy.MUSIC_URL.toUri())
                .apply {
                    if (!snapshot.albumArtUrl.isNullOrEmpty()) {
                        setIconUri(snapshot.albumArtUrl.toUri())
                    }
                }
                .build()

        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, description.mediaId)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.title ?: "YTMusic Pro")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.artist ?: "Music")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, snapshot.title ?: "YTMusic Pro")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, snapshot.artist ?: "Music")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.duration)
            .apply {
                if (!snapshot.albumArtUrl.isNullOrEmpty()) {
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, snapshot.albumArtUrl)
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, snapshot.albumArtUrl)
                }
            }
            .build()
    }
}
