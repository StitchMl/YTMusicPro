(function () {
    const state = window.__YTMusicPro;
    if (!state || window.__YTMusicProMetadataInjected) {
        return;
    }
    window.__YTMusicProMetadataInjected = true;

    let boundMediaElement = null;
    let rafHandle = 0;

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

    function readMediaSessionMetadata() {
        const mediaSession = navigator.mediaSession;
        const metadata = mediaSession ? mediaSession.metadata : null;
        if (!metadata) {
            return null;
        }

        const artwork = Array.isArray(metadata.artwork) && metadata.artwork.length > 0
            ? metadata.artwork[metadata.artwork.length - 1]
            : null;

        return {
            title: (metadata.title || "").trim(),
            artist: (metadata.artist || "").trim(),
            albumArtUrl: normalizeArtUrl(artwork && artwork.src ? artwork.src : "")
        };
    }

    function readDomMetadata() {
        const title = readText([
            "ytmusic-player-bar .title",
            "ytmusic-player-bar #title",
            "#layout ytmusic-player-bar .title",
            ".middle-controls .title"
        ]);
        const artist = readText([
            "ytmusic-player-bar .byline",
            "ytmusic-player-bar .subtitle",
            "#layout ytmusic-player-bar .byline",
            ".middle-controls .byline"
        ]);

        const artSelectors = [
            "ytmusic-player-bar img#img",
            "ytmusic-player-bar #thumbnail img",
            "ytmusic-player-bar img",
            "yt-img-shadow#thumbnail img"
        ];

        let albumArtUrl = "";
        for (const selector of artSelectors) {
            const element = document.querySelector(selector);
            const src = element ? (element.currentSrc || element.src || "") : "";
            if (src) {
                albumArtUrl = normalizeArtUrl(src);
                break;
            }
        }

        return { title, artist, albumArtUrl };
    }

    function getMediaElement() {
        return document.querySelector("video, audio");
    }

    function scheduleUpdate() {
        if (rafHandle) {
            return;
        }
        rafHandle = window.requestAnimationFrame(function () {
            rafHandle = 0;
            updateMetadata();
        });
    }

    function bindMediaElementListeners() {
        const mediaElement = getMediaElement();
        if (!mediaElement || mediaElement === boundMediaElement) {
            return;
        }

        boundMediaElement = mediaElement;
        [
            "play",
            "pause",
            "playing",
            "timeupdate",
            "loadedmetadata",
            "durationchange",
            "ended",
            "emptied",
            "seeking",
            "seeked"
        ].forEach(eventName => mediaElement.addEventListener(eventName, scheduleUpdate));
        mediaElement.addEventListener("ended", function () {
            if (window.YTMusicPro && typeof window.YTMusicPro.onPlaybackEnded === "function") {
                try {
                    window.YTMusicPro.onPlaybackEnded();
                } catch (error) {
                    console.error("YTMusic Pro: onPlaybackEnded failed", error);
                }
            }
        });
    }

    function updateMetadata() {
        bindMediaElementListeners();

        const mediaSessionMetadata = readMediaSessionMetadata();
        const domMetadata = readDomMetadata();
        const mediaElement = boundMediaElement || getMediaElement();
        const curTimeText = readText([".time-info .current-time", "#progress-bar .current-time"]);
        const totalTimeText = readText([".time-info .duration", "#progress-bar .duration"]);

        const title = mediaSessionMetadata && mediaSessionMetadata.title
            ? mediaSessionMetadata.title
            : domMetadata.title;
        const artist = mediaSessionMetadata && mediaSessionMetadata.artist
            ? mediaSessionMetadata.artist
            : domMetadata.artist;
        const albumArtUrl = mediaSessionMetadata && mediaSessionMetadata.albumArtUrl
            ? mediaSessionMetadata.albumArtUrl
            : domMetadata.albumArtUrl;

        if (!title && !artist) {
            return;
        }

        const isPlaying = mediaElement ? !mediaElement.paused : true;
        const position = mediaElement && Number.isFinite(mediaElement.currentTime)
            ? Math.floor(mediaElement.currentTime)
            : state.timeStringToSeconds(curTimeText);
        const duration = mediaElement && Number.isFinite(mediaElement.duration)
            ? Math.floor(mediaElement.duration)
            : state.timeStringToSeconds(totalTimeText);

        if (
            title !== state.lastTrack.title ||
            artist !== state.lastTrack.artist ||
            albumArtUrl !== state.lastTrack.albumArtUrl ||
            isPlaying !== state.lastTrack.isPlaying ||
            Math.abs(position - state.lastTrack.position) >= 1 ||
            Math.abs(duration - state.lastTrack.duration) >= 1
        ) {
            state.lastTrack = { title, artist, albumArtUrl, isPlaying, position, duration };
            if (window.YTMusicPro) {
                try {
                    window.YTMusicPro.updateNotification(
                        title,
                        artist,
                        albumArtUrl,
                        isPlaying,
                        position * 1000,
                        duration * 1000
                    );
                } catch (error) {
                    console.error("YTMusic Pro: updateNotification failed", error);
                }
            }
        }
    }

    [
        "yt-navigate-finish",
        "pageshow",
        "popstate",
        "visibilitychange"
    ].forEach(function (eventName) {
        window.addEventListener(eventName, scheduleUpdate, true);
        document.addEventListener(eventName, scheduleUpdate, true);
    });

    updateMetadata();
    setInterval(updateMetadata, state.config.checkInterval);
})();
