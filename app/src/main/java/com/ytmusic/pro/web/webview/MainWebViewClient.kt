package com.ytmusic.pro.web.webview

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.ytmusic.pro.network.NetworkUtils
import com.ytmusic.pro.web.WebSecurityPolicy
import java.io.ByteArrayInputStream

class MainWebViewClient(
    private val activity: Activity,
    private val progressView: View,
    private val listener: Listener,
) : WebViewClient() {

    interface Listener {
        fun onPageContextChanged(url: String?)

        fun onMusicPageReady()

        fun onJamCommandRequested()

        fun onOfflinePageRequired()
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        progressView.visibility = View.VISIBLE
        listener.onPageContextChanged(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        progressView.visibility = View.GONE
        if (WebSecurityPolicy.isMusicUrl(url)) {
            listener.onMusicPageReady()
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            handleMainFrameFailure()
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        progressView.visibility = View.GONE
        handler.cancel()
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (WebSecurityPolicy.shouldBlockRequest(url)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.startsWith(JAM_COMMAND_URL, ignoreCase = true)) {
            Log.d("YTMusicWebView", "Intercepted jam command URL: $url")
            listener.onJamCommandRequested()
            return true
        }
        if (WebSecurityPolicy.isAllowedInWebView(url)) {
            return false
        }

        return try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun handleMainFrameFailure() {
        progressView.visibility = View.GONE
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            listener.onOfflinePageRequired()
        }
    }

    private companion object {
        const val JAM_COMMAND_URL = "ytmusicpro://jam"
    }
}
