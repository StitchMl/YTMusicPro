package com.ytmusic.pro.web

import androidx.core.net.toUri
import java.util.Locale

object WebSecurityPolicy {

    const val MUSIC_URL = "https://music.youtube.com/"

    private val ALLOWED_HOSTS = setOf(
        "music.youtube.com",
        "accounts.google.com",
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "google.com",
        "www.google.com",
    )

    private val BLOCKED_AD_HOST_SNIPPETS = setOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
    )

    @JvmStatic
    fun isAllowedInWebView(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return matchesAllowedHost(host)
    }

    @JvmStatic
    fun isMusicUrl(url: String?): Boolean {
        val host = hostOf(url)
        return host != null && (host == "music.youtube.com" || host.endsWith(".music.youtube.com"))
    }

    @JvmStatic
    fun shouldBlockRequest(url: String?): Boolean {
        if (url == null) {
            return false
        }
        val normalizedUrl = url.lowercase(Locale.US)
        for (snippet in BLOCKED_AD_HOST_SNIPPETS) {
            if (normalizedUrl.contains(snippet)) {
                return true
            }
        }
        return false
    }

    private fun hostOf(url: String?): String? {
        return try {
            url?.toUri()?.host?.lowercase(Locale.US)
        } catch (_: Exception) {
            null
        }
    }

    private fun matchesAllowedHost(host: String): Boolean {
        for (allowedHost in ALLOWED_HOSTS) {
            if (host == allowedHost || host.endsWith(".$allowedHost")) {
                return true
            }
        }
        return false
    }
}
