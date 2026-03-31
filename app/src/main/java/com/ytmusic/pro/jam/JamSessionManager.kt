package com.ytmusic.pro.jam

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ytmusic.pro.playback.PlaybackCommandExecutor
import com.ytmusic.pro.playback.PlaybackControlContract
import com.ytmusic.pro.playback.PlaybackMetadataPoller
import com.ytmusic.pro.playback.PlaybackSnapshot
import com.ytmusic.pro.web.webapp.WebAppBridge
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import kotlin.random.Random

class JamSessionManager(
    context: Context,
    private val playbackCommandExecutor: PlaybackCommandExecutor,
) : PlaybackMetadataPoller.Listener, WebAppBridge.Listener, JamHttpServer.Controller {

    interface Listener {
        fun onJamStateChanged(state: JamSessionState)
    }

    data class StartResult(
        val success: Boolean,
        val state: JamSessionState,
        val message: String? = null,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<Listener>()
    private val queue = mutableListOf<JamQueueEntry>()
    private val lock = Any()
    private val searchService = JamYouTubeSearchService()

    private var currentSnapshot = PlaybackSnapshot.empty()
    private var currentJamEntry: JamQueueEntry? = null
    private var roomCode: String? = null
    private var joinUrl: String? = null
    private var server: JamHttpServer? = null

    fun addListener(listener: Listener) {
        synchronized(lock) {
            listeners += listener
        }
        listener.onJamStateChanged(currentState())
    }

    fun removeListener(listener: Listener) {
        synchronized(lock) {
            listeners -= listener
        }
    }

    fun isActive(): Boolean {
        synchronized(lock) {
            return server != null
        }
    }

    fun startSession(): StartResult {
        synchronized(lock) {
            val existingServer = server
            if (existingServer != null) {
                return StartResult(
                    success = true,
                    state = buildStateLocked(),
                )
            }
        }

        val hostAddress = LocalNetworkAddressResolver.findLocalIpv4Address()
            ?: return StartResult(
                success = false,
                state = currentState(),
                message = "No local network address available.",
            )

        val room = generateRoomCode()
        val startedServer = startServer()
            ?: return StartResult(
                success = false,
                state = currentState(),
                message = "No available port for jam session.",
            )

        val url = "http://$hostAddress:${startedServer.listeningPort}/"
        synchronized(lock) {
            server = startedServer
            roomCode = room
            joinUrl = url
        }
        notifyStateChanged()
        return StartResult(success = true, state = currentState())
    }

    fun stopSession() {
        val currentServer: JamHttpServer?
        synchronized(lock) {
            currentServer = server
            server = null
            roomCode = null
            joinUrl = null
            currentJamEntry = null
            queue.clear()
        }
        currentServer?.stop()
        notifyStateChanged()
    }

    override fun currentState(): JamSessionState {
        synchronized(lock) {
            return buildStateLocked()
        }
    }

    override fun searchTracks(query: String?): List<JamSearchResult> {
        return searchService.search(query)
    }

    override fun enqueueTrack(
        rawUrl: String?,
        requestedBy: String?,
        label: String?,
        thumbnailUrl: String?,
    ): JamActionResult {
        val normalizedUrl = JamTrackUrlNormalizer.normalize(rawUrl)
            ?: return JamActionResult(
                success = false,
                message = "Incolla un link YouTube o YouTube Music valido.",
                state = currentState(),
            )

        val queueEntry = JamQueueEntry.create(
            mediaUrl = normalizedUrl,
            submittedBy = requestedBy,
            label = label,
            thumbnailUrl = thumbnailUrl,
        )
        val shouldAutoplay: Boolean
        synchronized(lock) {
            queue += queueEntry
            shouldAutoplay = server != null && currentJamEntry == null && !currentSnapshot.isPlaying
        }
        notifyStateChanged()

        if (shouldAutoplay) {
            mainHandler.post { playNextQueuedTrack(useNativeFallback = false) }
        }

        return JamActionResult(
            success = true,
            message = "Brano aggiunto in coda.",
            state = currentState(),
        )
    }

    override fun playTrack(requestedBy: String?): JamActionResult {
        if (!isActive()) {
            return JamActionResult(false, "La jam non e' attiva.", currentState())
        }
        synchronized(lock) {
            currentSnapshot = currentSnapshot.copy(isPlaying = true)
        }
        notifyStateChanged()
        mainHandler.post { playbackCommandExecutor.execute(PlaybackControlContract.ACTION_PLAY, 0L) }
        return JamActionResult(true, "Play inviato.", currentState())
    }

    override fun pauseTrack(requestedBy: String?): JamActionResult {
        if (!isActive()) {
            return JamActionResult(false, "La jam non e' attiva.", currentState())
        }
        synchronized(lock) {
            currentSnapshot = currentSnapshot.copy(isPlaying = false)
        }
        notifyStateChanged()
        mainHandler.post { playbackCommandExecutor.execute(PlaybackControlContract.ACTION_PAUSE, 0L) }
        return JamActionResult(true, "Pausa inviata.", currentState())
    }

    override fun skipTrack(requestedBy: String?): JamActionResult {
        if (!isActive()) {
            return JamActionResult(false, "La jam non e' attiva.", currentState())
        }
        mainHandler.post { playNextQueuedTrack(useNativeFallback = true) }
        return JamActionResult(true, "Skip inviato.", currentState())
    }

    override fun stopTrack(requestedBy: String?): JamActionResult {
        if (!isActive()) {
            return JamActionResult(false, "La jam non e' attiva.", currentState())
        }
        synchronized(lock) {
            currentJamEntry = null
            currentSnapshot = currentSnapshot.copy(isPlaying = false, position = 0L)
        }
        notifyStateChanged()
        mainHandler.post { playbackCommandExecutor.stopPlayback() }
        return JamActionResult(true, "Stop inviato.", currentState())
    }

    override fun onPlaybackSnapshot(snapshot: PlaybackSnapshot) {
        synchronized(lock) {
            currentSnapshot = snapshot
        }
        notifyStateChanged()
    }

    override fun onPlaybackEnded() {
        if (!isActive()) {
            return
        }
        mainHandler.post { playNextQueuedTrack(useNativeFallback = false) }
    }

    private fun playNextQueuedTrack(useNativeFallback: Boolean) {
        val nextEntry: JamQueueEntry?
        synchronized(lock) {
            currentJamEntry = null
            nextEntry = if (queue.isNotEmpty()) queue.removeAt(0) else null
            if (nextEntry != null) {
                currentJamEntry = nextEntry
            }
        }

        when {
            nextEntry != null -> playbackCommandExecutor.playMediaUrl(nextEntry.mediaUrl)
            useNativeFallback -> playbackCommandExecutor.execute(PlaybackControlContract.ACTION_NEXT, 0L)
        }

        notifyStateChanged()
    }

    private fun startServer(): JamHttpServer? {
        for (port in START_PORT..END_PORT) {
            val candidate = JamHttpServer(appContext, port, this)
            try {
                candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                return candidate
            } catch (_: IOException) {
                candidate.stop()
            }
        }
        return null
    }

    private fun buildStateLocked(): JamSessionState {
        return JamSessionState(
            active = server != null,
            roomCode = roomCode,
            joinUrl = joinUrl,
            currentSnapshot = currentSnapshot,
            currentJamEntry = currentJamEntry,
            queue = queue.toList(),
        )
    }

    private fun notifyStateChanged() {
        val state = currentState()
        val snapshotListeners = synchronized(lock) { listeners.toList() }
        mainHandler.post {
            for (listener in snapshotListeners) {
                listener.onJamStateChanged(state)
            }
        }
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    companion object {
        private const val START_PORT = 8787
        private const val END_PORT = 8797
    }
}
