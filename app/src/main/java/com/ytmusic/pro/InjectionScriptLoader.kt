package com.ytmusic.pro

import android.content.Context

object InjectionScriptLoader {

    private val SCRIPT_FILES = arrayOf(
        "inject_bootstrap.js",
        "inject_ads.js",
        "inject_metadata.js",
        "inject_ui.js",
    )

    @JvmStatic
    fun load(context: Context): String {
        val combinedScript = AssetFileLoader.loadJoinedText(context, *SCRIPT_FILES)
        if (combinedScript.isNotEmpty()) {
            return combinedScript
        }
        return AssetFileLoader.loadText(context, "inject.js")
    }
}
