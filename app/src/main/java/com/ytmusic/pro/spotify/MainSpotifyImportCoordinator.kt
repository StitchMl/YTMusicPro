package com.ytmusic.pro.spotify

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.ytmusic.pro.R

class MainSpotifyImportCoordinator(
    private val activity: AppCompatActivity,
    private val importButton: MaterialButton,
    private val host: Host,
) {

    interface Host {
        fun launchSpotifyFileImport(intent: Intent)

        fun openSpotifyLibrary()

        fun replaceIntent(intent: Intent)
    }

    private val spotifyImportManager = SpotifyImportManager(activity)
    private val spotifyFileImportManager = SpotifyFileImportManager(activity)
    private var importInProgress = false

    fun attach() {
        if (!spotifyImportManager.isConfigured()) {
            importButton.setText(R.string.spotify_import_button_file)
        }
        importButton.setOnClickListener { showImportDialog() }
        updateImportButtonState(false)
    }

    fun handleRedirectIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        val handled = spotifyImportManager.handleRedirect(
            intent,
            object : SpotifyImportManager.Callback {
                override fun onImportCompleted(summary: SpotifyImportSummary) {
                    updateImportButtonState(false)
                    Toast.makeText(activity, R.string.spotify_import_success, Toast.LENGTH_SHORT).show()
                    host.openSpotifyLibrary()
                }

                override fun onImportFailed(message: String) {
                    updateImportButtonState(false)
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.spotify_import_failure, message),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )

        if (handled) {
            val clearedIntent = Intent(intent).apply { data = null }
            host.replaceIntent(clearedIntent)
        }
    }

    fun handleFileImportResult(result: ActivityResult) {
        val data = result.data
        if (result.resultCode != AppCompatActivity.RESULT_OK || data == null) {
            updateImportButtonState(false)
            return
        }

        spotifyFileImportManager.importSelection(
            data,
            object : SpotifyFileImportManager.Callback {
                override fun onImportCompleted(summary: SpotifyImportSummary) {
                    updateImportButtonState(false)
                    Toast.makeText(activity, R.string.spotify_file_import_success, Toast.LENGTH_SHORT).show()
                    host.openSpotifyLibrary()
                }

                override fun onImportFailed(message: String) {
                    updateImportButtonState(false)
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.spotify_file_import_failure, message),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    private fun showImportDialog() {
        val summary = spotifyImportManager.getLastImportSummary()
        if (!spotifyImportManager.isConfigured() && !summary.hasData()) {
            startSpotifyFileImport()
            return
        }

        val actions = mutableListOf<Int>()
        val labels = mutableListOf<String>()

        if (spotifyImportManager.isConfigured()) {
            actions.add(R.string.spotify_import_dialog_action)
            labels.add(activity.getString(R.string.spotify_import_dialog_action))
        }

        actions.add(R.string.spotify_import_dialog_file_action)
        labels.add(activity.getString(R.string.spotify_import_dialog_file_action))

        if (summary.hasData()) {
            actions.add(R.string.spotify_import_dialog_open_library)
            labels.add(activity.getString(R.string.spotify_import_dialog_open_library))
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.spotify_import_dialog_title)
            .setMessage(buildDialogMessage(summary))
            .setNegativeButton(android.R.string.cancel, null)
            .setItems(labels.toTypedArray()) { _, which -> handleDialogAction(actions[which]) }
            .show()
    }

    private fun handleDialogAction(actionRes: Int) {
        when (actionRes) {
            R.string.spotify_import_dialog_action -> startSpotifyImport()
            R.string.spotify_import_dialog_file_action -> startSpotifyFileImport()
            R.string.spotify_import_dialog_open_library -> host.openSpotifyLibrary()
        }
    }

    private fun buildDialogMessage(summary: SpotifyImportSummary?): String {
        if (summary != null && summary.hasData()) {
            return if (spotifyImportManager.isConfigured()) {
                summary.toDisplayText()
            } else {
                activity.getString(R.string.spotify_import_direct_unavailable) + "\n\n" + summary.toDisplayText()
            }
        }

        return if (spotifyImportManager.isConfigured()) {
            activity.getString(R.string.spotify_import_idle)
        } else {
            activity.getString(R.string.spotify_import_direct_unavailable)
        }
    }

    private fun startSpotifyImport() {
        if (importInProgress) {
            return
        }
        if (!spotifyImportManager.isConfigured()) {
            Toast.makeText(activity, R.string.spotify_import_direct_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        updateImportButtonState(true)
        Toast.makeText(activity, R.string.spotify_import_progress, Toast.LENGTH_SHORT).show()
        spotifyImportManager.startAuthorization(activity)
    }

    private fun startSpotifyFileImport() {
        if (importInProgress) {
            return
        }

        updateImportButtonState(true)
        Toast.makeText(activity, R.string.spotify_file_import_progress, Toast.LENGTH_SHORT).show()
        host.launchSpotifyFileImport(spotifyFileImportManager.createOpenDocumentIntent())
    }

    private fun updateImportButtonState(inProgress: Boolean) {
        importInProgress = inProgress
        importButton.isEnabled = !inProgress
        importButton.alpha = if (inProgress) 0.6f else 1.0f
    }
}
