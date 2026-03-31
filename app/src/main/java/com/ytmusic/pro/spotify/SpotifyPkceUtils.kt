package com.ytmusic.pro.spotify

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

object SpotifyPkceUtils {

    private val RANDOM = SecureRandom()

    @JvmStatic
    fun generateCodeVerifier(): String = randomUrlSafeString(64)

    @JvmStatic
    fun generateState(): String = randomUrlSafeString(24)

    @JvmStatic
    fun buildCodeChallenge(verifier: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val challengeBytes = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
            Base64.encodeToString(challengeBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            throw IllegalStateException("Unable to generate Spotify PKCE challenge", e)
        }
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val randomBytes = ByteArray(byteCount)
        RANDOM.nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
