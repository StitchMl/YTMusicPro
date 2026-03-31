package com.ytmusic.pro.spotify

import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ytmusic.pro.R
import java.text.DateFormat

class SpotifyLibraryUiController(
    private val activity: AppCompatActivity,
    private val listener: Listener,
) {

    interface Listener {
        fun onSearchQueryChanged(nextQuery: String)

        fun onExportRequested()

        fun onClearRequested()

        fun onLibraryItemSelected(item: SpotifyLibraryItem)
    }

    private val sectionRenderer = SpotifyLibrarySectionRenderer(
        activity,
        object : SpotifyLibrarySectionRenderer.Listener {
            override fun onItemSelected(item: SpotifyLibraryItem) {
                listener.onLibraryItemSelected(item)
            }
        },
    )

    private val contentLibrary: View = activity.findViewById(R.id.content_library)
    private val sourceView: TextView = activity.findViewById(R.id.text_source)
    private val importedAtView: TextView = activity.findViewById(R.id.text_imported_at)
    private val summaryView: TextView = activity.findViewById(R.id.text_summary)
    private val emptyView: TextView = activity.findViewById(R.id.text_empty)
    private val unsupportedView: TextView = activity.findViewById(R.id.text_unsupported)
    private val filterStatusView: TextView = activity.findViewById(R.id.text_filter_status)
    private val playlistsContainer: LinearLayout = activity.findViewById(R.id.container_playlists)
    private val savedTracksContainer: LinearLayout = activity.findViewById(R.id.container_saved_tracks)
    private val followedArtistsContainer: LinearLayout = activity.findViewById(R.id.container_followed_artists)
    private val playlistsMetaView: TextView = activity.findViewById(R.id.text_playlists_meta)
    private val savedTracksMetaView: TextView = activity.findViewById(R.id.text_saved_tracks_meta)
    private val followedArtistsMetaView: TextView = activity.findViewById(R.id.text_followed_artists_meta)
    private val exportButton: MaterialButton = activity.findViewById(R.id.button_export_library)
    private val clearButton: MaterialButton = activity.findViewById(R.id.button_clear_library)
    private val searchInput: TextInputEditText = activity.findViewById(R.id.input_search)

    init {
        bindActions()
        configureActionBar()
    }

    fun restoreQuery(query: String?) {
        if (TextUtils.isEmpty(query)) {
            return
        }

        searchInput.setText(query)
        searchInput.setSelection(query!!.length)
    }

    fun clearQuery() {
        searchInput.setText("")
    }

    fun render(
        libraryData: SpotifyLibraryData,
        filteredData: SpotifyLibraryData,
        hasActiveQuery: Boolean,
        currentQuery: String,
    ) {
        if (!libraryData.hasData()) {
            renderEmptyState()
            return
        }

        emptyView.visibility = View.GONE
        contentLibrary.visibility = View.VISIBLE
        updateActionButtons(true)

        sourceView.text = formatSource(libraryData.source)
        importedAtView.text = formatImportedAt(libraryData.importedAt)
        summaryView.text = libraryData.summary.toDisplayText()
        unsupportedView.text = joinNotes(libraryData.unsupportedNotes)
        unsupportedView.visibility = if (libraryData.unsupportedNotes.isEmpty()) View.GONE else View.VISIBLE

        updateFilterStatus(filteredData, hasActiveQuery, currentQuery)
        renderSections(filteredData)
    }

    fun handleHomeMenu(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            activity.finish()
            return true
        }
        return false
    }

    private fun configureActionBar() {
        activity.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.spotify_library_title)
        }
    }

    private fun bindActions() {
        exportButton.setOnClickListener { listener.onExportRequested() }
        clearButton.setOnClickListener { listener.onClearRequested() }
        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    listener.onSearchQueryChanged(s?.toString().orEmpty())
                }
            },
        )
    }

    private fun renderEmptyState() {
        emptyView.setText(R.string.spotify_library_empty)
        emptyView.visibility = View.VISIBLE
        contentLibrary.visibility = View.GONE
        unsupportedView.visibility = View.GONE
        filterStatusView.visibility = View.GONE
        updateActionButtons(false)
    }

    private fun renderSections(filteredData: SpotifyLibraryData) {
        sectionRenderer.render(
            playlistsContainer,
            playlistsMetaView,
            filteredData.playlists,
            PLAYLIST_LIMIT,
            R.string.spotify_library_playlists,
        )
        sectionRenderer.render(
            savedTracksContainer,
            savedTracksMetaView,
            filteredData.savedTracks,
            TRACK_LIMIT,
            R.string.spotify_library_saved_tracks,
        )
        sectionRenderer.render(
            followedArtistsContainer,
            followedArtistsMetaView,
            filteredData.followedArtists,
            ARTIST_LIMIT,
            R.string.spotify_library_followed_artists,
        )
    }

    private fun updateFilterStatus(
        filteredData: SpotifyLibraryData,
        hasActiveQuery: Boolean,
        currentQuery: String,
    ) {
        if (!hasActiveQuery) {
            filterStatusView.visibility = View.GONE
            emptyView.visibility = View.GONE
            return
        }

        if (filteredData.hasData()) {
            filterStatusView.text = activity.getString(
                R.string.spotify_library_filter_results,
                currentQuery,
                filteredData.getVisibleItemCount(),
            )
            emptyView.visibility = View.GONE
        } else {
            val emptyResult = activity.getString(R.string.spotify_library_filter_empty_results, currentQuery)
            filterStatusView.text = emptyResult
            emptyView.text = emptyResult
            emptyView.visibility = View.VISIBLE
        }
        filterStatusView.visibility = View.VISIBLE
    }

    private fun updateActionButtons(enabled: Boolean) {
        exportButton.isEnabled = enabled
        clearButton.isEnabled = enabled
        searchInput.isEnabled = enabled
        val alpha = if (enabled) 1.0f else 0.5f
        exportButton.alpha = alpha
        clearButton.alpha = alpha
        searchInput.alpha = alpha
    }

    private fun formatSource(source: String): String {
        if (TextUtils.isEmpty(source)) {
            return activity.getString(R.string.spotify_library_source_unknown)
        }
        return activity.getString(R.string.spotify_library_source, source.replace('_', ' '))
    }

    private fun formatImportedAt(importedAt: Long): String {
        if (importedAt <= 0) {
            return activity.getString(R.string.spotify_library_imported_unknown)
        }
        return activity.getString(
            R.string.spotify_library_imported_at,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(importedAt),
        )
    }

    private fun joinNotes(notes: List<String>): String {
        if (notes.isEmpty()) {
            return ""
        }

        val builder = StringBuilder(activity.getString(R.string.spotify_library_notes_header))
        for (note in notes) {
            builder.append("\n- ").append(note)
        }
        return builder.toString()
    }

    companion object {
        const val STATE_QUERY = "spotify_library_query"
        const val PLAYLIST_LIMIT = 30
        const val TRACK_LIMIT = 60
        const val ARTIST_LIMIT = 40
    }
}
