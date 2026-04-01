package com.ytmusic.pro

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ytmusic.pro.jam.JamSessionManager
import com.ytmusic.pro.jam.JamSessionState
import com.ytmusic.pro.network.NetworkStateMonitor
import com.ytmusic.pro.playback.PlaybackCommandExecutor
import com.ytmusic.pro.playback.PlaybackControlContract
import com.ytmusic.pro.playback.PlaybackControlReceiver
import com.ytmusic.pro.playback.PlaybackSnapshot
import com.ytmusic.pro.web.webapp.QueueLayoutSnapshot
import com.ytmusic.pro.web.webapp.WebAppBridge
import com.ytmusic.pro.web.webview.MainWebViewCoordinator
import com.ytmusic.pro.web.webview.QueueGestureOverlayView
import com.ytmusic.pro.web.webview.YTMusicWebview

class MainActivity : AppCompatActivity() {

    private lateinit var webView: YTMusicWebview
    private lateinit var queueGestureOverlayView: QueueGestureOverlayView
    private lateinit var playbackCommandExecutor: PlaybackCommandExecutor
    private lateinit var jamSessionManager: JamSessionManager
    private var mediaReceiver: PlaybackControlReceiver? = null
    private var networkStateMonitor: NetworkStateMonitor? = null
    private var webViewCoordinator: MainWebViewCoordinator? = null
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private val webBridgeListener =
        object : WebAppBridge.Listener {
            override fun onPlaybackSnapshotChanged(snapshot: PlaybackSnapshot) {
                jamSessionManager.onPlaybackSnapshot(snapshot)
            }

            override fun onPlaybackEnded() {
                jamSessionManager.onPlaybackEnded()
            }

            override fun onOpenJamControlsRequested() {
                runOnUiThread { handleJamEntryPoint() }
            }

            override fun onQueueLayoutChanged(snapshot: QueueLayoutSnapshot) {
                runOnUiThread { webViewCoordinator?.updateQueueLayout(snapshot) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        queueGestureOverlayView = findViewById(R.id.queue_gesture_overlay)
        val progressView: View = findViewById(R.id.progress_loader)
        playbackCommandExecutor = PlaybackCommandExecutor(webView)
        jamSessionManager = JamSessionManager(applicationContext, playbackCommandExecutor)

        initializeResultLaunchers()
        initializeWebViewCoordinator(progressView)
        requestNotificationPermission()
        configureBackNavigation()
        registerMediaReceiver()
        startNetworkMonitor()
    }

    private fun initializeResultLaunchers() {
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted != true) {
                Toast.makeText(
                    this,
                    "Notification permission denied. Media controls may not work.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun initializeWebViewCoordinator(progressView: View) {
        webViewCoordinator = MainWebViewCoordinator(this, webBridgeListener).also {
            it.initialize(webView, progressView, queueGestureOverlayView)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return
                    }

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            },
        )
    }

    private fun registerMediaReceiver() {
        mediaReceiver = PlaybackControlReceiver(playbackCommandExecutor)
        val mediaFilter = IntentFilter(PlaybackControlContract.CONTROL_BROADCAST)
        ContextCompat.registerReceiver(
            this,
            mediaReceiver,
            mediaFilter,
            PlaybackControlContract.INTERNAL_PERMISSION,
            null,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun handleJamEntryPoint() {
        Log.d("YTMusicWebView", "handleJamEntryPoint invoked")
        if (jamSessionManager.isActive()) {
            showJamDialog(jamSessionManager.currentState())
            return
        }

        val result = jamSessionManager.startSession()
        if (!result.success) {
            Toast.makeText(
                this,
                result.message ?: getString(R.string.jam_start_error),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        showJamDialog(result.state)
    }

    private fun showJamDialog(state: JamSessionState) {
        val lines = mutableListOf<String>()
        lines += getString(R.string.jam_same_wifi_hint)
        state.roomCode?.let { lines += getString(R.string.jam_status_room, it) }
        state.joinUrl?.takeIf(String::isNotBlank)?.let { lines += it }
        lines += getString(R.string.jam_status_queue, state.queue.size)

        val nowPlayingText =
            if (state.currentSnapshot.hasDisplayableMetadata()) {
                listOfNotNull(state.currentSnapshot.title, state.currentSnapshot.artist)
                    .joinToString(" - ")
            } else {
                null
            }

        lines += if (nowPlayingText.isNullOrBlank()) {
            getString(R.string.jam_status_now_playing_unknown)
        } else {
            getString(R.string.jam_status_now_playing, nowPlayingText)
        }

        state.currentJamEntry?.let {
            lines += getString(R.string.jam_status_current_host, it.displayTitle())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.jam_dialog_title)
            .setMessage(lines.joinToString("\n\n"))
            .setPositiveButton(R.string.jam_dialog_share) { _, _ ->
                shareJamLink(state.joinUrl)
            }
            .setNeutralButton(R.string.jam_dialog_stop) { _, _ ->
                jamSessionManager.stopSession()
            }
            .setNegativeButton(R.string.jam_dialog_close, null)
            .show()
    }

    private fun shareJamLink(joinUrl: String?) {
        if (joinUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.jam_start_error, Toast.LENGTH_LONG).show()
            return
        }

        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.jam_share_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.jam_share_text, joinUrl))
            }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.jam_dialog_share)))
    }

    private fun startNetworkMonitor() {
        networkStateMonitor = NetworkStateMonitor(
            this,
            object : NetworkStateMonitor.Listener {
                override fun onNetworkAvailable() {
                    runOnUiThread { webViewCoordinator?.onNetworkAvailable() }
                }
            },
        ).also { it.start() }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            jamSessionManager.stopSession()
        }
        if (isFinishing && !isChangingConfigurations && isTaskRoot) {
            stopService(Intent(this, ForegroundService::class.java))
        }
        super.onDestroy()
        mediaReceiver?.let(::unregisterReceiver)
        networkStateMonitor?.stop()
        webViewCoordinator?.detach()
    }
}
