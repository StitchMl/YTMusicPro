package com.ytmusic.pro

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ytmusic.pro.network.NetworkStateMonitor
import com.ytmusic.pro.playback.PlaybackCommandExecutor
import com.ytmusic.pro.playback.PlaybackControlContract
import com.ytmusic.pro.playback.PlaybackControlReceiver
import com.ytmusic.pro.web.webview.MainWebViewCoordinator
import com.ytmusic.pro.web.webview.YTMusicWebview

class MainActivity : AppCompatActivity() {

    private lateinit var webView: YTMusicWebview
    private var mediaReceiver: PlaybackControlReceiver? = null
    private var networkStateMonitor: NetworkStateMonitor? = null
    private var webViewCoordinator: MainWebViewCoordinator? = null
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        val progressView: View = findViewById(R.id.progress_loader)

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
        webViewCoordinator = MainWebViewCoordinator(this).also {
            it.initialize(webView, progressView)
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
        mediaReceiver = PlaybackControlReceiver(PlaybackCommandExecutor(webView))
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
        if (isFinishing && !isChangingConfigurations && isTaskRoot) {
            stopService(Intent(this, ForegroundService::class.java))
        }
        super.onDestroy()
        mediaReceiver?.let(::unregisterReceiver)
        networkStateMonitor?.stop()
        webViewCoordinator?.detach()
    }
}
