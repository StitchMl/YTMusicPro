package com.ytmusic.pro.web.webview

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * Custom WebView to maintain playback when visibility changes (Minimized/Locked)
 */
class YTMusicWebview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Essential for background playback:
        // We override the default behavior which normally pauses resources when hidden.
        // We only call super if we actually want to stop (which is rarely in a music app context).
        if (visibility != GONE && visibility != INVISIBLE) {
            super.onWindowVisibilityChanged(visibility)
        }
        // By skipping super.onWindowVisibilityChanged when invisible,
        // the WebView continues to execute JS and play media.
    }
}
