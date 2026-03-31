package com.ytmusic.pro.web.webview

import android.webkit.WebView

class WebViewScriptInjector(
    private val webView: WebView,
    private val script: String,
) {

    fun inject() {
        if (script.isNotEmpty()) {
            webView.evaluateJavascript(script, null)
        } else {
            webView.evaluateJavascript("console.error('YTMusic Pro: Injection script not found');", null)
        }
    }
}
