@file:Suppress("unused")

package com.ytmusic.pro.web.webview

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ytmusic.pro.InjectionScriptLoader
import com.ytmusic.pro.OfflinePageController
import com.ytmusic.pro.R
import com.ytmusic.pro.web.WebSecurityPolicy
import com.ytmusic.pro.web.webapp.QueueLayoutSnapshot
import com.ytmusic.pro.web.webapp.WebAppBridge
import com.ytmusic.pro.web.webapp.WebAppBridgeManager

class MainWebViewCoordinator(
    private val activity: AppCompatActivity,
    private val bridgeListener: WebAppBridge.Listener? = null,
) {

    private var offlinePageController: OfflinePageController? = null
    private var scriptInjector: WebViewScriptInjector? = null
    private var webAppBridgeManager: WebAppBridgeManager? = null
    private var webView: YTMusicWebview? = null
    private var queueGestureOverlayView: QueueGestureOverlayView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(
        webView: YTMusicWebview,
        progressView: View,
        queueGestureOverlayView: QueueGestureOverlayView,
    ) {
        this.webView = webView
        this.queueGestureOverlayView = queueGestureOverlayView
        if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView.setBackgroundColor(ContextCompat.getColor(activity, R.color.background_dark))
        configureRendererPriority(webView)
        configureInputSupport(webView)
        queueGestureOverlayView.visibility = View.GONE
        queueGestureOverlayView.isEnabled = false
        queueGestureOverlayView.isClickable = false
        queueGestureOverlayView.listener = null
        offlinePageController = OfflinePageController(activity)
        scriptInjector = WebViewScriptInjector(webView, InjectionScriptLoader.load(activity))
        val webAppBridge = WebAppBridge(activity, bridgeListener)
        webAppBridgeManager = WebAppBridgeManager(webView, webAppBridge)
        webAppBridgeManager?.updateForUrl(WebSecurityPolicy.MUSIC_URL)

        configureWebSettings(webView.settings)
        configureCookies()

        webView.webViewClient =
            MainWebViewClient(
                activity,
                progressView,
                object : MainWebViewClient.Listener {
                    override fun onPageContextChanged(url: String?) {
                        webAppBridgeManager?.updateForUrl(url)
                    }

                    override fun onMusicPageReady() {
                        offlinePageController?.onMusicPageReady()
                        webAppBridgeManager?.updateForUrl(this@MainWebViewCoordinator.webView?.url)
                        scriptInjector?.inject()
                    }

                    override fun onJamCommandRequested() {
                        bridgeListener?.onOpenJamControlsRequested()
                    }

                    override fun onOfflinePageRequired() {
                        val currentWebView = this@MainWebViewCoordinator.webView ?: return
                        offlinePageController?.showOfflinePage(currentWebView)
                    }
                },
            )
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onJsBeforeUnload(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult,
                ): Boolean {
                    Log.d("YTMusicWebView", "Suppressing beforeunload dialog for ${url ?: "unknown"}")
                    result.confirm()
                    return true
                }

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
        queueGestureOverlayView?.visibility = View.GONE
        queueGestureOverlayView?.listener = null
        webAppBridgeManager?.detach()
    }

    fun updateQueueLayout(snapshot: QueueLayoutSnapshot) {
        queueGestureOverlayView?.visibility = View.GONE
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
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.textZoom = 100
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
    }

    private fun configureInputSupport(webView: WebView) {
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)
        webView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP,
                -> {
                    if (!view.hasFocus()) {
                        view.requestFocus()
                    }
                    view.requestFocusFromTouch()
                }
            }
            false
        }
    }

}
