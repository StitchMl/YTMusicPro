package com.ytmusic.pro.spotify

import android.text.TextUtils
import java.util.Locale

data class SpotifyLibraryItem(
    val title: String,
    val subtitle: String,
    val meta: String,
    val spotifyUrl: String,
) {

    fun matchesQuery(query: String?): Boolean {
        if (TextUtils.isEmpty(query)) {
            return true
        }

        return buildSearchableText().contains(query!!.lowercase(Locale.getDefault()))
    }

    private fun buildSearchableText(): String {
        val builder = StringBuilder()
        append(builder, title)
        append(builder, subtitle)
        append(builder, meta)
        return builder.toString().lowercase(Locale.getDefault())
    }

    private fun append(builder: StringBuilder, value: String) {
        if (TextUtils.isEmpty(value)) {
            return
        }
        if (builder.isNotEmpty()) {
            builder.append('\n')
        }
        builder.append(value)
    }
}
