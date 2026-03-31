(function () {
    if (!window.__YTMusicPro) {
        return;
    }

    function enhanceUI() {
        if (document.getElementById('ytmusic-pro-style')) {
            return;
        }

        const style = document.createElement('style');
        style.id = 'ytmusic-pro-style';
        style.innerHTML = `
            ytmusic-mealbar-promo-renderer,
            ytmusic-upsell-dialog-renderer {
                display: none !important;
            }

            ytmusic-player-bar {
                background: rgba(0, 0, 0, 0.7) !important;
                backdrop-filter: blur(15px) !important;
                border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
            }
        `;
        document.head.appendChild(style);
    }

    setTimeout(enhanceUI, 3000);
})();
