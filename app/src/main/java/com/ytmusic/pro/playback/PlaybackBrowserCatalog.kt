package com.ytmusic.pro.playback

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import com.ytmusic.pro.web.WebSecurityPolicy

class PlaybackBrowserCatalog {

    fun createRoot(rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot {
        if (rootHints != null &&
            rootHints.getBoolean(android.service.media.MediaBrowserService.BrowserRoot.EXTRA_RECENT)
        ) {
            val extras = Bundle().apply {
                putBoolean(android.service.media.MediaBrowserService.BrowserRoot.EXTRA_RECENT, true)
            }
            return MediaBrowserServiceCompat.BrowserRoot(RECENTS_ROOT_ID, extras)
        }
        return MediaBrowserServiceCompat.BrowserRoot(ROOT_ID, null)
    }

    fun createChildren(
        parentId: String,
        currentSnapshot: PlaybackSnapshot,
        recentSnapshot: PlaybackSnapshot,
    ): List<MediaBrowserCompat.MediaItem> {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (parentId != ROOT_ID && parentId != RECENTS_ROOT_ID) {
            return items
        }

        val browseSnapshot = if (currentSnapshot.hasDisplayableMetadata()) currentSnapshot else recentSnapshot
        if (browseSnapshot.hasDisplayableMetadata()) {
            items.add(buildRecentMediaItem(browseSnapshot))
        }
        return items
    }

    fun isRecentMediaId(mediaId: String?): Boolean = mediaId == RECENT_MEDIA_ID

    private fun buildRecentMediaItem(snapshot: PlaybackSnapshot): MediaBrowserCompat.MediaItem {
        val descriptionBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(RECENT_MEDIA_ID)
            .setTitle(snapshot.title ?: "YTMusic Pro")
            .setSubtitle(snapshot.artist ?: "Music")
            .setMediaUri(WebSecurityPolicy.MUSIC_URL.toUri())

        if (!snapshot.albumArtUrl.isNullOrEmpty()) {
            descriptionBuilder.setIconUri(snapshot.albumArtUrl.toUri())
        }

        return MediaBrowserCompat.MediaItem(
            descriptionBuilder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )
    }

    private companion object {
        const val ROOT_ID = "ytmusicpro.root"
        const val RECENTS_ROOT_ID = "ytmusicpro.root.recents"
        const val RECENT_MEDIA_ID = "ytmusicpro.media.recent"
    }
}
