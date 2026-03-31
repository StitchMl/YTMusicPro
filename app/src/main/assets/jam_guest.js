(function () {
    const roomBadge = document.getElementById("roomBadge");
    const artwork = document.getElementById("artwork");
    const nowPlayingTitle = document.getElementById("nowPlayingTitle");
    const nowPlayingMeta = document.getElementById("nowPlayingMeta");
    const currentJamMeta = document.getElementById("currentJamMeta");
    const statusMessage = document.getElementById("statusMessage");
    const displayNameInput = document.getElementById("displayName");
    const playButton = document.getElementById("playButton");
    const pauseButton = document.getElementById("pauseButton");
    const skipButton = document.getElementById("skipButton");
    const stopButton = document.getElementById("stopButton");
    const searchInput = document.getElementById("searchInput");
    const searchButton = document.getElementById("searchButton");
    const searchResults = document.getElementById("searchResults");
    const searchEmpty = document.getElementById("searchEmpty");
    const queueList = document.getElementById("queueList");
    const emptyQueue = document.getElementById("emptyQueue");

    const buttons = [playButton, pauseButton, skipButton, stopButton, searchButton];
    const emptyArtwork =
        "data:image/svg+xml;utf8," +
        encodeURIComponent(
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 300'>" +
            "<defs><linearGradient id='g' x1='0%' y1='0%' x2='100%' y2='100%'>" +
            "<stop offset='0%' stop-color='%23ff355f'/><stop offset='100%' stop-color='%23141414'/>" +
            "</linearGradient></defs>" +
            "<rect width='300' height='300' rx='42' fill='url(%23g)'/>" +
            "<circle cx='150' cy='150' r='72' fill='rgba(255,255,255,0.18)'/>" +
            "<circle cx='150' cy='150' r='18' fill='white'/></svg>"
        );

    let refreshTimer = null;
    let searchTimer = null;
    let lastSearchQuery = "";

    function escapeHtml(text) {
        return String(text || "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }

    function getGuestName() {
        return displayNameInput.value.trim();
    }

    function setStatus(message, isError) {
        statusMessage.textContent = message || "";
        statusMessage.style.color = isError ? "#ffc3c3" : "#b8ffd2";
    }

    function setBusy(isBusy) {
        buttons.forEach(button => {
            button.disabled = isBusy;
        });
    }

    async function postJson(url, body) {
        setBusy(true);
        try {
            const response = await fetch(url, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(body || {})
            });
            const data = await response.json();
            if (!response.ok || data.success === false) {
                throw new Error(data.message || "Richiesta non riuscita.");
            }
            if (data.state) {
                renderState(data.state);
            }
            setStatus(data.message || "Operazione completata.", false);
            return data;
        } catch (error) {
            setStatus(error.message || "Operazione non riuscita.", true);
            throw error;
        } finally {
            setBusy(false);
        }
    }

    async function refreshState() {
        try {
            const response = await fetch("/api/state", { cache: "no-store" });
            const state = await response.json();
            renderState(state);
        } catch (error) {
            setStatus("Connessione con l'host persa.", true);
        }
    }

    async function searchTracks(query) {
        const normalizedQuery = (query || "").trim();
        if (normalizedQuery.length < 2) {
            lastSearchQuery = "";
            searchResults.innerHTML = "";
            searchEmpty.textContent = "Inizia a digitare per vedere i risultati.";
            searchEmpty.classList.remove("hidden");
            return;
        }

        lastSearchQuery = normalizedQuery;
        searchEmpty.textContent = "Ricerca in corso...";
        searchEmpty.classList.remove("hidden");
        searchResults.innerHTML = "";

        try {
            const response = await fetch("/api/search?q=" + encodeURIComponent(normalizedQuery), {
                cache: "no-store"
            });
            const data = await response.json();
            if (!response.ok || data.success === false) {
                throw new Error(data.message || "Ricerca non riuscita.");
            }
            if (lastSearchQuery !== normalizedQuery) {
                return;
            }
            renderSearchResults(data.results || []);
        } catch (error) {
            searchEmpty.textContent = "Impossibile recuperare i risultati.";
            searchEmpty.classList.remove("hidden");
            setStatus(error.message || "Ricerca non riuscita.", true);
        }
    }

    function renderState(state) {
        if (!state) {
            return;
        }

        roomBadge.textContent = state.active
            ? "Room " + state.roomCode + " live"
            : "Jam non attiva";

        const nowPlaying = state.nowPlaying || {};
        const title = [nowPlaying.title, nowPlaying.artist].filter(Boolean).join(" - ");
        nowPlayingTitle.textContent = title || "Nessun brano rilevato";

        const playbackState = nowPlaying.isPlaying ? "In riproduzione" : "In pausa";
        const duration = nowPlaying.duration > 0
            ? " | " + formatSeconds(Math.round(nowPlaying.duration / 1000))
            : "";
        nowPlayingMeta.textContent = playbackState + duration;
        currentJamMeta.textContent = state.currentJamEntry
            ? "Brano jam attivo: " + state.currentJamEntry.displayTitle + " | " + state.currentJamEntry.submittedBy
            : "";
        artwork.src = nowPlaying.albumArtUrl || emptyArtwork;
        renderQueue(state.queue || []);
    }

    function renderSearchResults(results) {
        searchResults.innerHTML = "";
        if (!results || results.length === 0) {
            searchEmpty.textContent = "Nessun risultato trovato.";
            searchEmpty.classList.remove("hidden");
            return;
        }

        searchEmpty.classList.add("hidden");
        results.forEach(result => {
            const item = document.createElement("li");
            item.className = "result";
            item.innerHTML =
                "<img alt='' src='" + escapeAttribute(result.thumbnailUrl || emptyArtwork) + "'>" +
                "<div>" +
                "<p class='result-title'>" + escapeHtml(result.title) + "</p>" +
                "<span class='result-meta'>" + escapeHtml(buildMeta(result)) + "</span>" +
                "</div>" +
                "<button class='primary' type='button'>Aggiungi</button>";

            item.querySelector("button").addEventListener("click", function () {
                postJson("/api/queue", {
                    mediaUrl: result.mediaUrl,
                    submittedBy: getGuestName(),
                    label: result.title,
                    thumbnailUrl: result.thumbnailUrl || ""
                });
            });
            searchResults.appendChild(item);
        });
    }

    function renderQueue(queue) {
        queueList.innerHTML = "";
        if (!queue || queue.length === 0) {
            emptyQueue.hidden = false;
            return;
        }

        emptyQueue.hidden = true;
        queue.forEach((entry, index) => {
            const item = document.createElement("li");
            item.className = "queue-item";
            item.innerHTML =
                "<img alt='' src='" + escapeAttribute(entry.thumbnailUrl || emptyArtwork) + "'>" +
                "<div>" +
                "<p class='queue-title'>" + escapeHtml((index + 1) + ". " + entry.displayTitle) + "</p>" +
                "<span class='queue-meta'>" + escapeHtml(entry.submittedBy || "Guest") + "</span>" +
                "</div>";
            queueList.appendChild(item);
        });
    }

    function buildMeta(result) {
        const chunks = [];
        if (result.channel) {
            chunks.push(result.channel);
        }
        if (result.durationText) {
            chunks.push(result.durationText);
        }
        return chunks.join(" | ");
    }

    function formatSeconds(seconds) {
        const safeSeconds = Math.max(0, seconds || 0);
        const minutes = Math.floor(safeSeconds / 60);
        const remainder = safeSeconds % 60;
        return minutes + ":" + String(remainder).padStart(2, "0");
    }

    function escapeAttribute(text) {
        return escapeHtml(text);
    }

    function scheduleSearch() {
        if (searchTimer) {
            clearTimeout(searchTimer);
        }
        searchTimer = setTimeout(function () {
            searchTracks(searchInput.value);
        }, 300);
    }

    playButton.addEventListener("click", function () {
        postJson("/api/play", {
            submittedBy: getGuestName()
        });
    });

    pauseButton.addEventListener("click", function () {
        postJson("/api/pause", {
            submittedBy: getGuestName()
        });
    });

    skipButton.addEventListener("click", function () {
        postJson("/api/skip", {
            submittedBy: getGuestName()
        });
    });

    stopButton.addEventListener("click", function () {
        postJson("/api/stop", {
            submittedBy: getGuestName()
        });
    });

    searchButton.addEventListener("click", function () {
        searchTracks(searchInput.value);
    });

    searchInput.addEventListener("input", scheduleSearch);
    searchInput.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
            event.preventDefault();
            searchTracks(searchInput.value);
        }
    });

    refreshState();
    refreshTimer = setInterval(refreshState, 2500);
    window.addEventListener("beforeunload", function () {
        if (refreshTimer) {
            clearInterval(refreshTimer);
        }
        if (searchTimer) {
            clearTimeout(searchTimer);
        }
    });
})();
