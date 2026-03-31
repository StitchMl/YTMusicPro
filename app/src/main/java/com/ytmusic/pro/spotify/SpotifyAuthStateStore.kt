package com.ytmusic.pro.spotify

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.core.content.edit

class SpotifyAuthStateStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePendingRequest(state: String, verifier: String) {
        preferences.edit {
            putString(KEY_STATE, state)
                .putString(KEY_VERIFIER, verifier)
        }
    }

    fun consumeVerifierForState(returnedState: String?): String? {
        val storedState = preferences.getString(KEY_STATE, null)
        val verifier = preferences.getString(KEY_VERIFIER, null)
        clear()
        if (!TextUtils.equals(storedState, returnedState) || TextUtils.isEmpty(verifier)) {
            return null
        }
        return verifier
    }

    fun clear() {
        preferences.edit { remove(KEY_STATE).remove(KEY_VERIFIER) }
    }

    private companion object {
        const val PREFS_NAME = "spotify_auth"
        const val KEY_STATE = "state"
        const val KEY_VERIFIER = "verifier"
    }
}
