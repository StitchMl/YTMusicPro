package com.ytmusic.pro.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.ytmusic.pro.BuildConfig

class SpotifyImportManager(context: Context) {

    interface Callback {
        fun onImportCompleted(summary: SpotifyImportSummary)

        fun onImportFailed(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val authConfig = SpotifyAuthConfig(BuildConfig.SPOTIFY_CLIENT_ID, BuildConfig.SPOTIFY_REDIRECT_URI)
    private val authStateStore = SpotifyAuthStateStore(context)
    private val apiClient = SpotifyApiClient()
    private val importStore = SpotifyImportStore(context)

    fun isConfigured(): Boolean {
        return authConfig.isConfigured()
    }

    fun getLastImportSummary(): SpotifyImportSummary {
        return importStore.loadSummary()
    }

    fun startAuthorization(activity: Activity) {
        val verifier = SpotifyPkceUtils.generateCodeVerifier()
        val state = SpotifyPkceUtils.generateState()
        authStateStore.savePendingRequest(state, verifier)
        val authorizeUri = authConfig.buildAuthorizationUri(state, SpotifyPkceUtils.buildCodeChallenge(verifier))
        activity.startActivity(Intent(Intent.ACTION_VIEW, authorizeUri))
    }

    fun handleRedirect(intent: Intent?, callback: Callback): Boolean {
        if (intent == null) {
            return false
        }

        val redirectUri = intent.data
        if (!authConfig.matchesRedirect(redirectUri)) {
            return false
        }

        val error = redirectUri?.getQueryParameter("error")
        if (!TextUtils.isEmpty(error)) {
            callback.onImportFailed(error!!)
            return true
        }

        val returnedState = redirectUri?.getQueryParameter("state")
        val verifier = authStateStore.consumeVerifierForState(returnedState)
        val code = redirectUri?.getQueryParameter("code")
        if (TextUtils.isEmpty(code) || TextUtils.isEmpty(verifier)) {
            callback.onImportFailed("callback Spotify non valido")
            return true
        }

        Thread(
            {
                try {
                    val accessToken = apiClient.exchangeCodeForToken(code!!, verifier!!, authConfig)
                    val importData = apiClient.importLibrary(accessToken)
                    importStore.save(importData)
                    val summary = SpotifyImportSummary.fromImportData(importData)
                    mainHandler.post { callback.onImportCompleted(summary) }
                } catch (e: Exception) {
                    mainHandler.post {
                        callback.onImportFailed(e.message ?: "errore sconosciuto")
                    }
                }
            },
            "spotify-import",
        ).start()

        return true
    }
}
