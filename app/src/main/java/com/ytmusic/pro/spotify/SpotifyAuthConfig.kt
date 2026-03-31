package com.ytmusic.pro.spotify

import android.net.Uri
import android.text.TextUtils
import androidx.core.net.toUri

class SpotifyAuthConfig(
    val clientId: String,
    val redirectUri: String,
) {

    fun isConfigured(): Boolean {
        return !TextUtils.isEmpty(clientId) && !TextUtils.isEmpty(redirectUri)
    }

    fun buildAuthorizationUri(state: String, codeChallenge: String): Uri {
        return AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()
    }

    fun matchesRedirect(uri: Uri?): Boolean {
        if (uri == null || TextUtils.isEmpty(redirectUri)) {
            return false
        }
        val expectedUri = redirectUri.toUri()
        return TextUtils.equals(expectedUri.scheme, uri.scheme) &&
            TextUtils.equals(expectedUri.host, uri.host) &&
            TextUtils.equals(expectedUri.path, uri.path)
    }

    private companion object {
        const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        val SCOPES = arrayOf(
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-follow-read",
        )
    }
}
