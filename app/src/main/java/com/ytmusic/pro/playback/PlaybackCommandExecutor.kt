package com.ytmusic.pro.playback

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

class PlaybackCommandExecutor(
    private val webView: WebView,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(action: String?, positionSeconds: Long) {
        when (action) {
            PlaybackControlContract.ACTION_PLAY -> evaluate("document.querySelector('video, audio')?.play()")
            PlaybackControlContract.ACTION_PAUSE -> evaluate("document.querySelector('video, audio')?.pause()")
            PlaybackControlContract.ACTION_NEXT -> evaluate("document.querySelector('.next-button')?.click()")
            PlaybackControlContract.ACTION_PREV -> evaluate("document.querySelector('.previous-button')?.click()")
            PlaybackControlContract.ACTION_STOP -> stopPlayback()
            PlaybackControlContract.ACTION_SEEK -> {
                evaluate(
                    "const video = document.querySelector('video'); if (video) { video.currentTime = " +
                        positionSeconds.coerceAtLeast(0) +
                        "; }",
                )
            }
        }
    }

    fun playMediaUrl(url: String) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) {
            return
        }

        webView.post {
            webView.loadUrl(normalizedUrl)
            mainHandler.postDelayed({ evaluate(PLAYBACK_RESUME_SCRIPT) }, 1500L)
            mainHandler.postDelayed({ evaluate(PLAYBACK_RESUME_SCRIPT) }, 3500L)
        }
    }

    fun stopPlayback() {
        evaluate(STOP_PLAYBACK_SCRIPT)
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript(script, null)
    }

    private companion object {
        const val PLAYBACK_RESUME_SCRIPT =
            """
            (function() {
                const media = document.querySelector('video, audio');
                if (media) {
                    media.play().catch(function() {});
                }
                const selectors = [
                    "ytmusic-player-bar button[aria-label*='Play']",
                    "ytmusic-player-bar button[aria-label*='Riproduci']",
                    "ytmusic-player-bar .play-pause-button[aria-label*='Play']",
                    "ytmusic-player-bar .play-pause-button[aria-label*='Riproduci']",
                    ".play-pause-button[aria-label*='Play']",
                    ".play-pause-button[aria-label*='Riproduci']"
                ];
                for (const selector of selectors) {
                    const button = document.querySelector(selector);
                    if (button) {
                        button.click();
                        break;
                    }
                }
            })();
            """

        const val STOP_PLAYBACK_SCRIPT =
            """
            (function() {
                const media = document.querySelector('video, audio');
                if (media) {
                    try { media.pause(); } catch (error) {}
                    try { media.currentTime = 0; } catch (error) {}
                }
                const selectors = [
                    "ytmusic-player-bar button[aria-label*='Pause']",
                    "ytmusic-player-bar button[aria-label*='Pausa']",
                    "ytmusic-player-bar .play-pause-button[aria-label*='Pause']",
                    "ytmusic-player-bar .play-pause-button[aria-label*='Pausa']",
                    ".play-pause-button[aria-label*='Pause']",
                    ".play-pause-button[aria-label*='Pausa']"
                ];
                for (const selector of selectors) {
                    const button = document.querySelector(selector);
                    if (button) {
                        button.click();
                        break;
                    }
                }
            })();
            """
    }
}
