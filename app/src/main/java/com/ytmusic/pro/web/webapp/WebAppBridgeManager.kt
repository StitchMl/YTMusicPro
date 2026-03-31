package com.ytmusic.pro.web.webapp

import android.webkit.WebView
import com.ytmusic.pro.web.WebSecurityPolicy

class WebAppBridgeManager(
    private val webView: WebView,
    private val bridge: WebAppBridge,
) {

    private var attached = false

    fun updateForUrl(url: String?) {
        if (WebSecurityPolicy.isMusicUrl(url)) {
            attach()
        } else {
            detach()
        }
    }

    fun detach() {
        if (!attached) {
            return
        }
        webView.removeJavascriptInterface(BRIDGE_NAME)
        attached = false
    }

    private fun attach() {
        if (attached) {
            return
        }
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        attached = true
    }

    private companion object {
        const val BRIDGE_NAME = "YTMusicPro"
    }
}
