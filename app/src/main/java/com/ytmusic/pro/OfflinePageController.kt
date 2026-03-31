package com.ytmusic.pro

import android.content.Context
import android.webkit.WebView
import com.ytmusic.pro.network.NetworkUtils
import com.ytmusic.pro.web.WebSecurityPolicy

class OfflinePageController(context: Context) {

    private val appContext = context.applicationContext
    private var showingOfflinePage = false

    fun loadInitialPage(webView: WebView) {
        if (NetworkUtils.isNetworkAvailable(appContext)) {
            loadMusicPage(webView)
        } else {
            showOfflinePage(webView)
        }
    }

    fun onNetworkAvailable(webView: WebView) {
        if (showingOfflinePage && !WebSecurityPolicy.isMusicUrl(webView.url)) {
            loadMusicPage(webView)
        }
    }

    fun onMusicPageReady() {
        showingOfflinePage = false
    }

    fun showOfflinePage(webView: WebView) {
        val errorHtml = AssetFileLoader.loadText(appContext, "error.html")
        showingOfflinePage = true
        if (errorHtml.isNotEmpty()) {
            webView.loadDataWithBaseURL("file:///android_asset/", errorHtml, "text/html", "UTF-8", null)
            return
        }
        webView.loadData(FALLBACK_HTML, "text/html", "UTF-8")
    }

    private fun loadMusicPage(webView: WebView) {
        showingOfflinePage = false
        webView.loadUrl(WebSecurityPolicy.MUSIC_URL)
    }

    private companion object {
        const val FALLBACK_HTML =
            "<html><body style='background:#000;color:#fff;text-align:center;padding:50px;'>" +
                "<h2>Connection Error</h2><p>Please check your internet connection.</p></body></html>"
    }
}
