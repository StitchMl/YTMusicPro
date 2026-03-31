package com.ytmusic.pro.web.webview

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ytmusic.pro.InjectionScriptLoader
import com.ytmusic.pro.OfflinePageController
import com.ytmusic.pro.R
import com.ytmusic.pro.playback.PlaybackMetadataPoller
import com.ytmusic.pro.web.webapp.WebAppBridge
import com.ytmusic.pro.web.webapp.WebAppBridgeManager

class MainWebViewCoordinator(
    private val activity: AppCompatActivity,
    private val playbackListener: PlaybackMetadataPoller.Listener? = null,
    private val bridgeListener: WebAppBridge.Listener? = null,
) {

    private var offlinePageController: OfflinePageController? = null
    private var scriptInjector: WebViewScriptInjector? = null
    private var webAppBridgeManager: WebAppBridgeManager? = null
    private var playbackMetadataPoller: PlaybackMetadataPoller? = null
    private var webView: YTMusicWebview? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(webView: YTMusicWebview, progressView: View) {
        this.webView = webView
        webView.setBackgroundColor(ContextCompat.getColor(activity, R.color.background_dark))
        configureRendererPriority(webView)
        offlinePageController = OfflinePageController(activity)
        scriptInjector = WebViewScriptInjector(webView, InjectionScriptLoader.load(activity))
        val webAppBridge = WebAppBridge(activity, bridgeListener)
        webAppBridgeManager = WebAppBridgeManager(webView, webAppBridge)
        playbackMetadataPoller = PlaybackMetadataPoller(webView, webAppBridge, playbackListener)

        configureWebSettings(webView.settings)
        configureCookies()

        webView.webViewClient = MainWebViewClient(
            activity,
            progressView,
            object : MainWebViewClient.Listener {
                override fun onPageContextChanged(url: String?) {
                    webAppBridgeManager?.updateForUrl(url)
                    playbackMetadataPoller?.setEnabled(com.ytmusic.pro.web.WebSecurityPolicy.isMusicUrl(url))
                }

                override fun onMusicPageReady() {
                    offlinePageController?.onMusicPageReady()
                    webAppBridgeManager?.updateForUrl(this@MainWebViewCoordinator.webView?.url)
                    playbackMetadataPoller?.setEnabled(true)
                    scriptInjector?.inject()
                }

                override fun onOfflinePageRequired() {
                    val currentWebView = this@MainWebViewCoordinator.webView ?: return
                    offlinePageController?.showOfflinePage(currentWebView)
                }
            },
        )
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(
                        "YTMusicWebView",
                        "${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                            "@${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                    )
                    return super.onConsoleMessage(consoleMessage)
                }
            }

        offlinePageController?.loadInitialPage(webView)
    }

    fun onNetworkAvailable() {
        val currentWebView = webView ?: return
        offlinePageController?.onNetworkAvailable(currentWebView)
    }

    fun detach() {
        webAppBridgeManager?.detach()
        playbackMetadataPoller?.stop()
    }

    private fun configureCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        webView?.let { cookieManager.setAcceptThirdPartyCookies(it, true) }
    }

    private fun configureRendererPriority(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/116.0.0.0 Mobile Safari/537.36"
    }
}
