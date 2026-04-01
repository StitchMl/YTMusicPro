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
            PlaybackControlContract.ACTION_NEXT -> evaluate(
                buildPlayerControlScript(
                    selectors = NEXT_CONTROL_SELECTORS,
                    labelPatterns = NEXT_CONTROL_LABEL_PATTERNS,
                    playerApiMethod = "nextVideo",
                    mediaFallbackScript =
                        """
                        const media = findMediaElement();
                        if (media && Number.isFinite(media.duration) && media.duration > 0) {
                            try {
                                media.currentTime = Math.max(0, media.duration - 0.25);
                            } catch (error) {}
                            try {
                                media.play().catch(function () {});
                            } catch (error) {}
                            return true;
                        }
                        """.trimIndent(),
                ),
            )
            PlaybackControlContract.ACTION_PREV -> evaluate(
                buildPlayerControlScript(
                    selectors = PREVIOUS_CONTROL_SELECTORS,
                    labelPatterns = PREVIOUS_CONTROL_LABEL_PATTERNS,
                    playerApiMethod = "previousVideo",
                ),
            )
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

    private fun buildPlayerControlScript(
        selectors: List<String>,
        labelPatterns: List<String>,
        playerApiMethod: String? = null,
        mediaFallbackScript: String = "",
    ): String {
        val selectorsJs = selectors.joinToString(", ") { "\"${it.escapeForJavascript()}\"" }
        val labelPatternsJs = labelPatterns.joinToString(", ") { "\"${it.escapeForJavascript()}\"" }
        val playerApiBlock =
            playerApiMethod?.let { method ->
                """
                const playerBar = document.querySelector("ytmusic-player-bar");
                const apiCandidates = [
                    playerBar && playerBar.playerApi_,
                    playerBar && playerBar.playerApi,
                    playerBar && playerBar.__data && playerBar.__data.playerApi_,
                    window.playerApi,
                    window.ytmusic && window.ytmusic.playerApi
                ];
                for (const api of apiCandidates) {
                    if (!api || typeof api.${method} !== "function") {
                        continue;
                    }
                    try {
                        api.${method}();
                        return true;
                    } catch (error) {}
                }
                """.trimIndent()
            }.orEmpty()

        val normalizedMediaFallbackScript = mediaFallbackScript.trim()

        return """
            (function() {
                try {
                    const selectors = [$selectorsJs];
                    const labelPatterns = [$labelPatternsJs];
                    const clickableSelector = [
                        "button",
                        "a",
                        "[role=\"button\"]",
                        "tp-yt-paper-icon-button",
                        "yt-icon-button",
                        "tp-yt-paper-button",
                        "ytmusic-button-renderer",
                        "yt-button-shape"
                    ].join(", ");
                    const labelSearchSelector = [
                        "button",
                        "a",
                        "[role=\"button\"]",
                        "tp-yt-paper-icon-button",
                        "yt-icon-button",
                        "tp-yt-paper-button",
                        "ytmusic-button-renderer",
                        "yt-button-shape",
                        "[aria-label]",
                        "[title]"
                    ].join(", ");

                    function normalize(value) {
                        return String(value || "")
                            .toLowerCase()
                            .normalize("NFD")
                            .replace(/[\u0300-\u036f]/g, "")
                            .replace(/[\u0027\u2019]/g, "")
                            .replace(/\s+/g, " ")
                            .trim();
                    }

                    function getRoots() {
                        const roots = [document];
                        const pending = [document.documentElement];

                        while (pending.length > 0) {
                            const node = pending.shift();
                            if (!node || !node.querySelectorAll) {
                                continue;
                            }

                            node.querySelectorAll("*").forEach(function (element) {
                                if (element.shadowRoot) {
                                    roots.push(element.shadowRoot);
                                    pending.push(element.shadowRoot);
                                }
                            });
                        }

                        return roots;
                    }

                    function findClickableAncestor(element) {
                        if (!element) {
                            return null;
                        }
                        return element.closest ? (element.closest(clickableSelector) || element) : element;
                    }

                    function isVisible(element) {
                        if (!element || !element.getBoundingClientRect) {
                            return false;
                        }
                        const rect = element.getBoundingClientRect();
                        return rect.width > 0 && rect.height > 0;
                    }

                    function isDisabled(element) {
                        if (!element || !element.getAttribute) {
                            return false;
                        }
                        return element.disabled === true ||
                            element.getAttribute("disabled") !== null ||
                            element.getAttribute("aria-disabled") === "true";
                    }

                    function matchesLabel(element) {
                        if (!element) {
                            return false;
                        }

                        const labels = [
                            normalize(element.getAttribute && element.getAttribute("aria-label")),
                            normalize(element.getAttribute && element.getAttribute("title")),
                            normalize(element.innerText),
                            normalize(element.textContent)
                        ];

                        return labelPatterns.some(function (pattern) {
                            return labels.some(function (value) {
                                return value && (value === pattern || value.indexOf(pattern) >= 0);
                            });
                        });
                    }

                    function isLikelyPlayerControl(element) {
                        if (!element) {
                            return false;
                        }

                        const playerContainerSelector = [
                            "ytmusic-player-bar",
                            "ytmusic-player",
                            "#player",
                            "#layout",
                            "[class*=\"player\"]",
                            "[class*=\"controls\"]"
                        ].join(", ");

                        let current = element;
                        while (current) {
                            if (current.matches && current.matches(playerContainerSelector)) {
                                return true;
                            }
                            if (current.parentElement) {
                                current = current.parentElement;
                                continue;
                            }
                            const root = current.getRootNode ? current.getRootNode() : null;
                            current = root && root.host ? root.host : null;
                        }

                        return false;
                    }

                    function clickElement(element) {
                        if (!element) {
                            return false;
                        }

                        try {
                            element.scrollIntoView({ block: "nearest", inline: "nearest" });
                        } catch (error) {}
                        try {
                            element.focus({ preventScroll: true });
                        } catch (error) {}

                        if (element.getBoundingClientRect) {
                            const rect = element.getBoundingClientRect();
                            const clientX = rect.left + Math.max(1, Math.min(rect.width / 2, Math.max(rect.width - 1, 1)));
                            const clientY = rect.top + Math.max(1, Math.min(rect.height / 2, Math.max(rect.height - 1, 1)));
                            const pointerEventType = window.PointerEvent || MouseEvent;
                            [
                                ["pointerdown", pointerEventType],
                                ["mousedown", MouseEvent],
                                ["pointerup", pointerEventType],
                                ["mouseup", MouseEvent],
                                ["click", MouseEvent]
                            ].forEach(function (entry) {
                                const eventName = entry[0];
                                const EventType = entry[1];
                                try {
                                    element.dispatchEvent(new EventType(eventName, {
                                        bubbles: true,
                                        cancelable: true,
                                        composed: true,
                                        clientX: clientX,
                                        clientY: clientY,
                                        button: 0,
                                        buttons: 1
                                    }));
                                } catch (error) {}
                            });
                        }

                        try {
                            if (typeof element.click === "function") {
                                element.click();
                                return true;
                            }
                        } catch (error) {}

                        return true;
                    }

                    function findCandidateBySelectors(root) {
                        for (const selector of selectors) {
                            let matches = [];
                            try {
                                matches = root.querySelectorAll(selector);
                            } catch (error) {
                                continue;
                            }
                            for (const element of matches) {
                                const candidate = findClickableAncestor(element);
                                if (candidate && isVisible(candidate) && !isDisabled(candidate)) {
                                    return candidate;
                                }
                            }
                        }
                        return null;
                    }

                    function findCandidateByLabels(root) {
                        const matches = root.querySelectorAll(labelSearchSelector);
                        for (const element of matches) {
                            const candidate = findClickableAncestor(element);
                            if (!candidate || !isVisible(candidate) || isDisabled(candidate)) {
                                continue;
                            }
                            if (!isLikelyPlayerControl(candidate)) {
                                continue;
                            }
                            if (matchesLabel(candidate)) {
                                return candidate;
                            }
                        }
                        return null;
                    }

                    function findMediaElement() {
                        for (const root of getRoots()) {
                            if (!root || !root.querySelector) {
                                continue;
                            }
                            const media = root.querySelector("video, audio");
                            if (media) {
                                return media;
                            }
                        }
                        return null;
                    }

                    const roots = getRoots();
                    for (const root of roots) {
                        const candidate = findCandidateBySelectors(root);
                        if (candidate) {
                            return clickElement(candidate);
                        }
                    }

                    for (const root of roots) {
                        const candidate = findCandidateByLabels(root);
                        if (candidate) {
                            return clickElement(candidate);
                        }
                    }

                    $playerApiBlock
                    $normalizedMediaFallbackScript
                    return false;
                } catch (error) {
                    console.warn("YTMusic Pro: player control action failed", error);
                    return false;
                }
            })();
        """.trimIndent()
    }

    private fun String.escapeForJavascript(): String {
        return replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
    }

    private companion object {
        val NEXT_CONTROL_SELECTORS = listOf(
            "ytmusic-player-bar .next-button",
            ".next-button",
            "button.next-button",
            "tp-yt-paper-icon-button.next-button",
            "yt-icon-button.next-button",
            "ytmusic-player-bar [aria-label*='Next' i]",
            "ytmusic-player-bar [aria-label*='Avanti' i]",
            "ytmusic-player-bar [aria-label*='Successivo' i]",
            "ytmusic-player-bar [title*='Next' i]",
            "ytmusic-player-bar [title*='Avanti' i]",
            "ytmusic-player-bar [title*='Successivo' i]",
            "[data-id='next']",
        )

        val NEXT_CONTROL_LABEL_PATTERNS = listOf(
            "next",
            "next song",
            "skip",
            "skip song",
            "avanti",
            "successivo",
            "brano successivo",
            "salta",
            "salta brano",
        )

        val PREVIOUS_CONTROL_SELECTORS = listOf(
            "ytmusic-player-bar .previous-button",
            ".previous-button",
            "button.previous-button",
            "tp-yt-paper-icon-button.previous-button",
            "yt-icon-button.previous-button",
            "ytmusic-player-bar [aria-label*='Previous' i]",
            "ytmusic-player-bar [aria-label*='Precedente' i]",
            "ytmusic-player-bar [title*='Previous' i]",
            "ytmusic-player-bar [title*='Precedente' i]",
            "[data-id='previous']",
        )

        val PREVIOUS_CONTROL_LABEL_PATTERNS = listOf(
            "previous",
            "previous song",
            "precedente",
            "brano precedente",
            "torna indietro",
        )

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
