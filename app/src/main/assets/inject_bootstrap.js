(function () {
    if (window.__YTMusicProInjected) {
        return;
    }

    window.__YTMusicProInjected = true;
    console.log("YTMusic Pro Script Initialized");

    window.__YTMusicPro = {
        config: {
            checkInterval: 1000,
            adCheckInterval: 500
        },
        lastTrack: {
            title: "",
            artist: "",
            albumArtUrl: "",
            isPlaying: false,
            position: 0,
            duration: 0
        }
    };

    window.__YTMusicPro.timeStringToSeconds = function (timeStr) {
        if (!timeStr) {
            return 0;
        }

        const parts = timeStr.split(':').map(Number);
        if (parts.length === 2) {
            return parts[0] * 60 + parts[1];
        }
        if (parts.length === 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2];
        }
        return 0;
    };
})();
