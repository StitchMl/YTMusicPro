package com.ytmusic.pro.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.ytmusic.pro.web.webapp.WebAppBridge
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs

class PlaybackMetadataPoller(
    private val webView: WebView,
    private val bridge: WebAppBridge,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = Runnable(::poll)

    private var enabled = false
    private var lastSnapshot: PlaybackSnapshot? = null

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            return
        }

        this.enabled = enabled
        handler.removeCallbacks(pollRunnable)

        if (enabled) {
            handler.post(pollRunnable)
        } else {
            lastSnapshot = null
        }
    }

    fun stop() {
        enabled = false
        handler.removeCallbacks(pollRunnable)
        lastSnapshot = null
    }

    private fun poll() {
        if (!enabled) {
            return
        }

        webView.evaluateJavascript(METADATA_SCRIPT) { rawResult ->
            try {
                val snapshot = parseSnapshot(rawResult)
                if (snapshot != null && shouldPublish(snapshot)) {
                    lastSnapshot = snapshot
                    Log.d(
                        TAG,
                        "Publishing metadata: ${snapshot.title} / ${snapshot.artist} / playing=${snapshot.isPlaying}"
                    )
                    bridge.updateNotification(
                        snapshot.title,
                        snapshot.artist,
                        snapshot.albumArtUrl,
                        snapshot.isPlaying,
                        snapshot.position,
                        snapshot.duration,
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "Failed to read playback metadata", error)
            } finally {
                scheduleNext()
            }
        }
    }

    private fun scheduleNext() {
        if (!enabled) {
            return
        }
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun shouldPublish(snapshot: PlaybackSnapshot): Boolean {
        if (!snapshot.hasDisplayableMetadata()) {
            return false
        }

        val previousSnapshot = lastSnapshot ?: return true
        if (!snapshot.sameNotificationContentAs(previousSnapshot)) {
            return true
        }

        return abs(snapshot.position - previousSnapshot.position) >= POSITION_UPDATE_INTERVAL_MS
    }

    private fun parseSnapshot(rawResult: String?): PlaybackSnapshot? {
        if (rawResult.isNullOrBlank() || rawResult == "null") {
            return null
        }

        val decoded = JSONTokener(rawResult).nextValue() as? String ?: return null
        val json = JSONObject(decoded)

        val title = json.optString("title").trim().ifEmpty { null }
        val artist = json.optString("artist").trim().ifEmpty { null }
        val albumArtUrl = json.optString("albumArtUrl").trim().ifEmpty { null }
        val isPlaying = json.optBoolean("isPlaying", false)
        val position = json.optLong("position", 0L).coerceAtLeast(0L)
        val duration = json.optLong("duration", 0L).coerceAtLeast(0L)

        return PlaybackSnapshot.create(title, artist, albumArtUrl, isPlaying, position, duration)
    }

    private companion object {
        const val TAG = "PlaybackMetadataPoller"
        const val POLL_INTERVAL_MS = 1500L
        const val POSITION_UPDATE_INTERVAL_MS = 5000L
        val METADATA_SCRIPT =
            """
            (function() {
                try {
                    function readText(selectors) {
                        for (const selector of selectors) {
                            const element = document.querySelector(selector);
                            const text = element && element.textContent ? element.textContent.trim() : "";
                            if (text) {
                                return text;
                            }
                        }
                        return "";
                    }

                    function normalizeArtUrl(url) {
                        if (!url) {
                            return "";
                        }
                        if (url.startsWith("//")) {
                            return "https:" + url;
                        }
                        return url;
                    }

                    const mediaSession = navigator.mediaSession ? navigator.mediaSession.metadata : null;
                    const artwork = mediaSession && Array.isArray(mediaSession.artwork) && mediaSession.artwork.length > 0
                        ? mediaSession.artwork[mediaSession.artwork.length - 1]
                        : null;

                    const mediaElement = document.querySelector("video, audio");
                    let albumArtUrl = mediaSession && mediaSession.artwork ? normalizeArtUrl(artwork && artwork.src ? artwork.src : "") : "";
                    if (!albumArtUrl) {
                        const artSelectors = [
                            "ytmusic-player-bar img#img",
                            "ytmusic-player-bar #thumbnail img",
                            "ytmusic-player-bar img",
                            "yt-img-shadow#thumbnail img"
                        ];
                        for (const selector of artSelectors) {
                            const element = document.querySelector(selector);
                            const src = element ? (element.currentSrc || element.src || "") : "";
                            if (src) {
                                albumArtUrl = normalizeArtUrl(src);
                                break;
                            }
                        }
                    }

                    return JSON.stringify({
                        title: (mediaSession && mediaSession.title ? mediaSession.title : readText([
                            "ytmusic-player-bar .title",
                            "ytmusic-player-bar #title",
                            "#layout ytmusic-player-bar .title",
                            ".middle-controls .title"
                        ])).trim(),
                        artist: (mediaSession && mediaSession.artist ? mediaSession.artist : readText([
                            "ytmusic-player-bar .byline",
                            "ytmusic-player-bar .subtitle",
                            "#layout ytmusic-player-bar .byline",
                            ".middle-controls .byline"
                        ])).trim(),
                        albumArtUrl: albumArtUrl,
                        isPlaying: mediaElement ? !mediaElement.paused : false,
                        position: mediaElement && Number.isFinite(mediaElement.currentTime)
                            ? Math.floor(mediaElement.currentTime * 1000)
                            : 0,
                        duration: mediaElement && Number.isFinite(mediaElement.duration)
                            ? Math.floor(mediaElement.duration * 1000)
                            : 0
                    });
                } catch (error) {
                    return JSON.stringify({ error: String(error) });
                }
            })();
            """.trimIndent()
    }
}
