package com.ytmusic.pro.spotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ytmusic.pro.R
import androidx.core.net.toUri

class SpotifyLibraryActivity : AppCompatActivity() {

    private lateinit var exportManager: SpotifyLibraryExportManager
    private lateinit var libraryReader: SpotifyLibraryReader
    private lateinit var libraryFilter: SpotifyLibraryFilter
    private lateinit var uiController: SpotifyLibraryUiController
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private var libraryData = SpotifyLibraryData.empty()
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spotify_library)

        exportManager = SpotifyLibraryExportManager(this)
        libraryReader = SpotifyLibraryReader(this)
        libraryFilter = SpotifyLibraryFilter()
        uiController = SpotifyLibraryUiController(
            this,
            object : SpotifyLibraryUiController.Listener {
                override fun onSearchQueryChanged(nextQuery: String) {
                    handleSearchQueryChanged(nextQuery)
                }

                override fun onExportRequested() {
                    startExport()
                }

                override fun onClearRequested() {
                    confirmClearLibrary()
                }

                override fun onLibraryItemSelected(item: SpotifyLibraryItem) {
                    openUrl(item.spotifyUrl)
                }
            },
        )

        initializeResultLauncher()

        if (savedInstanceState != null) {
            currentQuery = savedInstanceState.getString(SpotifyLibraryUiController.STATE_QUERY, "")
        }
        uiController.restoreQuery(currentQuery)
        renderLibrary()
    }

    override fun onResume() {
        super.onResume()
        renderLibrary()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SpotifyLibraryUiController.STATE_QUERY, currentQuery)
    }

    private fun initializeResultLauncher() {
        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
            ::handleExportDestination,
        )
    }

    private fun handleSearchQueryChanged(nextQuery: String) {
        if (TextUtils.equals(currentQuery, nextQuery)) {
            return
        }

        currentQuery = nextQuery
        renderFilteredLibrary()
    }

    private fun renderLibrary() {
        libraryData = libraryReader.load()
        renderFilteredLibrary()
    }

    private fun renderFilteredLibrary() {
        val filteredData = libraryFilter.apply(libraryData, currentQuery)
        uiController.render(libraryData, filteredData, libraryFilter.hasActiveQuery(currentQuery), currentQuery)
    }

    private fun startExport() {
        if (!libraryData.hasData()) {
            Toast.makeText(this, R.string.spotify_library_empty, Toast.LENGTH_SHORT).show()
            return
        }

        exportLauncher.launch(exportManager.buildDefaultFileName())
    }

    private fun handleExportDestination(uri: Uri?) {
        if (uri == null) {
            return
        }

        exportManager.exportTo(
            uri,
            object : SpotifyLibraryExportManager.Callback {
                override fun onExportCompleted(fileName: String) {
                    Toast.makeText(
                        this@SpotifyLibraryActivity,
                        getString(R.string.spotify_library_export_success, fileName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                override fun onExportFailed(message: String) {
                    Toast.makeText(
                        this@SpotifyLibraryActivity,
                        getString(R.string.spotify_library_export_failure, message),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    private fun confirmClearLibrary() {
        if (!libraryData.hasData()) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.spotify_library_clear_title)
            .setMessage(R.string.spotify_library_clear_message)
            .setPositiveButton(R.string.spotify_library_clear_action) { _, _ ->
                exportManager.clearLibrary()
                currentQuery = ""
                uiController.clearQuery()
                Toast.makeText(
                    this@SpotifyLibraryActivity,
                    R.string.spotify_library_clear_success,
                    Toast.LENGTH_SHORT,
                ).show()
                renderLibrary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            // No-op if no browser is available.
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (uiController.handleHomeMenu(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
