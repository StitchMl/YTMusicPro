(function () {
    const state = window.__YTMusicPro;
    if (!state) {
        return;
    }

    function blockAds() {
        const adSelectors = [
            'ytm-promoted-sparkles-web-renderer',
            'ytm-companion-ad-renderer',
            '.video-ads',
            '.ytp-ad-module',
            'ytm-promoted-video-renderer'
        ];

        adSelectors.forEach(selector => {
            document.querySelectorAll(selector).forEach(element => element.remove());
        });

        const video = document.querySelector('video');
        if (!video) {
            return;
        }

        const adShowing = document.querySelector('.ad-showing, .ad-interrupting');
        if (adShowing) {
            video.currentTime = video.duration || 999;
            console.log("YTMusic Pro: Ad Skipped");
        }
    }

    blockAds();
    setInterval(blockAds, state.config.adCheckInterval);
})();
