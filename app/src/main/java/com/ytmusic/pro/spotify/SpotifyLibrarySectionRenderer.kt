package com.ytmusic.pro.spotify

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ytmusic.pro.R
import java.util.Locale

class SpotifyLibrarySectionRenderer(
    private val context: Context,
    private val listener: Listener,
) {

    interface Listener {
        fun onItemSelected(item: SpotifyLibraryItem)
    }

    private val inflater = LayoutInflater.from(context)

    fun render(
        container: LinearLayout,
        metaView: TextView,
        items: List<SpotifyLibraryItem>,
        limit: Int,
        emptyLabelRes: Int,
    ) {
        container.removeAllViews()
        if (items.isEmpty()) {
            metaView.text = context.getString(
                R.string.spotify_library_section_empty,
                context.getString(emptyLabelRes).lowercase(Locale.getDefault()),
            )
            return
        }

        metaView.text =
            if (items.size > limit) {
                context.resources.getQuantityString(
                    R.plurals.spotify_library_section_showing_limit,
                    items.size,
                    minOf(limit, items.size),
                    items.size,
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.spotify_library_section_count,
                    items.size,
                    items.size,
                )
            }

        val count = minOf(limit, items.size)
        for (i in 0 until count) {
            val item = items[i]
            val itemView = inflater.inflate(R.layout.item_spotify_library_entry, container, false)
            val titleView: TextView = itemView.findViewById(R.id.text_title)
            val subtitleView: TextView = itemView.findViewById(R.id.text_subtitle)
            val metaItemView: TextView = itemView.findViewById(R.id.text_meta)
            val actionView: TextView = itemView.findViewById(R.id.text_action)

            titleView.text = item.title
            setTextOrHide(subtitleView, item.subtitle)
            setTextOrHide(metaItemView, item.meta)

            if (!TextUtils.isEmpty(item.spotifyUrl)) {
                actionView.visibility = View.VISIBLE
                itemView.setOnClickListener { listener.onItemSelected(item) }
            } else {
                actionView.visibility = View.GONE
                itemView.setOnClickListener(null)
            }

            container.addView(itemView)
        }
    }

    private fun setTextOrHide(textView: TextView, value: String) {
        if (TextUtils.isEmpty(value)) {
            textView.visibility = View.GONE
            return
        }
        textView.visibility = View.VISIBLE
        textView.text = value
    }
}
