package com.ytmusic.pro.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.ytmusic.pro.ForegroundService
import com.ytmusic.pro.MainActivity
import com.ytmusic.pro.R

class PlaybackNotificationFactory(
    private val context: Context,
) {

    fun build(
        mediaSession: MediaSessionCompat,
        snapshot: PlaybackSnapshot,
        albumArt: Bitmap?,
    ): Notification {
        val playPauseAction =
            if (snapshot.isPlaying) {
                createMediaAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    PlaybackControlContract.ACTION_PAUSE,
                )
            } else {
                createMediaAction(
                    android.R.drawable.ic_media_play,
                    "Play",
                    PlaybackControlContract.ACTION_PLAY,
                )
            }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(snapshot.title ?: "YTMusic Pro")
            .setContentText(snapshot.artist ?: if (snapshot.isPlaying) "Playing..." else "Paused")
            .setLargeIcon(albumArt)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(createContentIntent())
            .setDeleteIntent(getServiceActionPendingIntent(PlaybackControlContract.ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(snapshot.isPlaying)
            .addAction(
                createMediaAction(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    PlaybackControlContract.ACTION_PREV,
                ),
            )
            .addAction(playPauseAction)
            .addAction(
                createMediaAction(
                    android.R.drawable.ic_media_next,
                    "Next",
                    PlaybackControlContract.ACTION_NEXT,
                ),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    fun createMediaButtonReceiverIntent(): PendingIntent? {
        return MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_PLAY_PAUSE,
        )
    }

    fun createContentIntent(): PendingIntent {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createMediaAction(
        iconRes: Int,
        title: String,
        serviceAction: String,
    ): NotificationCompat.Action {
        val pendingIntent = getServiceActionPendingIntent(serviceAction)
        return NotificationCompat.Action(iconRes, title, pendingIntent)
    }

    private fun getServiceActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, ForegroundService::class.java).apply {
            this.action = action
        }
        val requestCode = requestCodeForAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }

    private fun requestCodeForAction(action: String): Int {
        return when (action) {
            PlaybackControlContract.ACTION_PLAY -> 1
            PlaybackControlContract.ACTION_PAUSE -> 2
            PlaybackControlContract.ACTION_NEXT -> 3
            PlaybackControlContract.ACTION_PREV -> 4
            else -> 0
        }
    }

    companion object {
        const val CHANNEL_ID = "ytmusicpro_playback_v3"
    }
}
