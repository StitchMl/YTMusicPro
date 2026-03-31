@file:Suppress("MayBeConstant", "DEPRECATION")

package com.ytmusic.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.app.Service
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.ytmusic.pro.album.AlbumArtRepository
import com.ytmusic.pro.playback.PlaybackActionBroadcaster
import com.ytmusic.pro.playback.PlaybackControlContract
import com.ytmusic.pro.playback.PlaybackIdleController
import com.ytmusic.pro.playback.PlaybackNotificationFactory
import com.ytmusic.pro.playback.PlaybackSessionPublisher
import com.ytmusic.pro.playback.PlaybackSnapshot
import com.ytmusic.pro.playback.RecentPlaybackStore

class ForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationFactory: PlaybackNotificationFactory
    private lateinit var recentPlaybackStore: RecentPlaybackStore
    private lateinit var sessionPublisher: PlaybackSessionPublisher
    private lateinit var idleController: PlaybackIdleController
    private var currentSnapshot = PlaybackSnapshot.empty()
    private var foregroundNotificationShown = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        notificationFactory = PlaybackNotificationFactory(this)
        recentPlaybackStore = RecentPlaybackStore(this)
        currentSnapshot = recentPlaybackStore.load()

        mediaSession = MediaSessionCompat(this, "YTMusicProSession")
        sessionPublisher = PlaybackSessionPublisher(
            mediaSession,
            notificationFactory,
            AlbumArtRepository(),
            object : PlaybackSessionPublisher.Listener {
                override fun onNotificationReady(notification: Notification) {
                    publishPlaybackNotification(notification)
                }
            },
        )
        idleController = PlaybackIdleController(
            mainHandler,
            IDLE_TIMEOUT,
            object : PlaybackIdleController.Listener {
                override fun onIdleTimeout() {
                    stopPlaybackService()
                }
            },
        )

        configureMediaSession()
    }

    private fun configureMediaSession() {
        mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )
        mediaSession.setMediaButtonReceiver(notificationFactory.createMediaButtonReceiverIntent())
        mediaSession.setSessionActivity(notificationFactory.createContentIntent())
        mediaSession.setCallback(createMediaSessionCallback())
        updatePlaybackState(currentSnapshot.isPlaying, currentSnapshot.position)
        sessionPublisher.restoreMetadata(currentSnapshot)
        mediaSession.isActive = true
    }

    private fun createMediaSessionCallback(): MediaSessionCompat.Callback {
        return object : MediaSessionCompat.Callback() {
            override fun onSeekTo(pos: Long) {
                PlaybackActionBroadcaster.sendSeek(this@ForegroundService, pos / 1000)
            }

            override fun onPlay() {
                resumeCurrentOrRecent()
                handleActionIntent(PlaybackControlContract.ACTION_PLAY)
            }

            override fun onPause() {
                handleActionIntent(PlaybackControlContract.ACTION_PAUSE)
            }

            override fun onStop() {
                handleActionIntent(PlaybackControlContract.ACTION_STOP)
            }

            override fun onSkipToNext() {
                handleActionIntent(PlaybackControlContract.ACTION_NEXT)
            }

            override fun onSkipToPrevious() {
                handleActionIntent(PlaybackControlContract.ACTION_PREV)
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                resumeCurrentOrRecent()
                handleActionIntent(PlaybackControlContract.ACTION_PLAY)
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                resumeCurrentOrRecent()
                handleActionIntent(PlaybackControlContract.ACTION_PLAY)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        if (intent == null) {
            return resolveStartMode()
        }

        val action = intent.action
        if (Intent.ACTION_MEDIA_BUTTON == action) {
            return resolveStartMode()
        }

        if (action != null) {
            handleActionIntent(action)
            return resolveStartMode()
        }

        if (containsPlaybackSnapshot(intent)) {
            updateFromSnapshot(PlaybackSnapshot.fromIntent(intent))
        }
        return resolveStartMode()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlaybackService()
        super.onTaskRemoved(rootIntent)
    }

    private fun handleActionIntent(action: String) {
        if (PlaybackControlContract.ACTION_STOP == action) {
            PlaybackActionBroadcaster.send(this, action)
            stopPlaybackService()
            return
        }

        if (isPlaybackStateAction(action)) {
            currentSnapshot = currentSnapshot.withPlaybackState(PlaybackControlContract.ACTION_PLAY == action)
            idleController.update(currentSnapshot.isPlaying)
            updatePlaybackState(currentSnapshot.isPlaying, currentSnapshot.position)
            sessionPublisher.refresh(currentSnapshot)
        }

        PlaybackActionBroadcaster.send(this, action)
    }

    private fun updateFromSnapshot(snapshot: PlaybackSnapshot) {
        val previousSnapshot = currentSnapshot
        currentSnapshot = snapshot.mergeMissingMetadata(resolveMetadataFallback(snapshot, previousSnapshot))

        if (currentSnapshot.hasDisplayableMetadata()) {
            recentPlaybackStore.save(currentSnapshot)
        }

        idleController.update(currentSnapshot.isPlaying)
        updatePlaybackState(currentSnapshot.isPlaying, currentSnapshot.position)

        if (currentSnapshot.sameNotificationContentAs(previousSnapshot)) {
            return
        }

        sessionPublisher.publish(
            currentSnapshot,
            object : PlaybackSessionPublisher.SnapshotProvider {
                override fun getCurrentSnapshot(): PlaybackSnapshot = currentSnapshot
            },
        )
    }

    private fun resolveMetadataFallback(
        snapshot: PlaybackSnapshot,
        previousSnapshot: PlaybackSnapshot?,
    ): PlaybackSnapshot {
        if (snapshot.hasDisplayableMetadata()) {
            return snapshot
        }
        if (previousSnapshot != null && previousSnapshot.hasDisplayableMetadata()) {
            return previousSnapshot
        }
        val recentSnapshot = recentPlaybackStore.load()
        return if (recentSnapshot.hasDisplayableMetadata()) recentSnapshot else snapshot
    }

    private fun updatePlaybackState(isPlaying: Boolean, position: Long) {
        val clampedPosition = position.coerceAtLeast(0)
        val playbackSpeed = if (isPlaying) 1.0f else 0.0f
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    clampedPosition,
                    playbackSpeed,
                )
                .setActions(SUPPORTED_ACTIONS)
                .build(),
        )
    }

    private fun isPlaybackStateAction(action: String): Boolean {
        return PlaybackControlContract.ACTION_PLAY == action || PlaybackControlContract.ACTION_PAUSE == action
    }

    private fun resumeCurrentOrRecent() {
        if (currentSnapshot.hasDisplayableMetadata()) {
            return
        }

        val recentSnapshot = recentPlaybackStore.load()
        if (!recentSnapshot.hasDisplayableMetadata()) {
            return
        }

        currentSnapshot = recentSnapshot
        sessionPublisher.restoreMetadata(currentSnapshot)
        sessionPublisher.refresh(currentSnapshot)
    }

    private fun containsPlaybackSnapshot(intent: Intent): Boolean {
        return intent.hasExtra(PlaybackControlContract.EXTRA_IS_PLAYING) ||
            intent.hasExtra(PlaybackControlContract.EXTRA_TITLE) ||
            intent.hasExtra(PlaybackControlContract.EXTRA_ARTIST) ||
            intent.hasExtra(PlaybackControlContract.EXTRA_ALBUM_ART)
    }

    private fun stopPlaybackService() {
        idleController.cancel()
        mediaSession.isActive = false
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        foregroundNotificationShown = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishPlaybackNotification(notification: Notification) {
        startOrUpdateForeground(notification)
    }

    private fun startOrUpdateForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
        foregroundNotificationShown = true
    }

    private fun resolveStartMode(): Int {
        return if (currentSnapshot.isPlaying) START_STICKY else START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                PlaybackNotificationFactory.CHANNEL_ID,
                "YTMusic Pro Playback",
                NotificationManager.IMPORTANCE_LOW,
            )
            serviceChannel.description = "Playback controls and now playing information"
            serviceChannel.setShowBadge(false)
            serviceChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        idleController.cancel()
        mediaSession.isActive = false
        if (foregroundNotificationShown) {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundNotificationShown = false
        }
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val NOTIFICATION_ID = 888
        const val IDLE_TIMEOUT = 5L * 60 * 1000
        val SUPPORTED_ACTIONS: Long =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
    }
}
