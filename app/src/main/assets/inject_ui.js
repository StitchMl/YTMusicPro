(function () {
    try {
        console.log("YTMusic Pro UI script start");

        if (!window.__YTMusicPro || window.__YTMusicProUiInjected) {
            return;
        }
        window.__YTMusicProUiInjected = true;

        const STYLE_ID = "ytmusic-pro-style";
        const JAM_ATTR = "data-ytmusic-pro-jam-control";
        const JAM_VALUE = "true";
        const JAM_URL = "ytmusicpro://jam";
        const LEGACY_REMOVE_CLASS = "ytmusic-pro-queue-remove";
        const OPEN_APP_PATTERNS = ["apri app", "apri nell app", "apri nellapp", "open app"];
        const QUEUE_REMOVE_PATTERNS = [
            "rimuovi dalla coda",
            "rimuovi",
            "remove from queue",
            "remove",
            "elimina dalla coda",
            "elimina"
        ];
        const QUEUE_MENU_PATTERNS = ["altre azioni", "more actions", "menu", "opzioni", "options"];
        const QUEUE_DRAG_PATTERNS = ["drag", "reorder", "riordina", "sposta"];
        const QUEUE_ITEM_ATTR = "data-ytmusic-pro-queue-item";
        const QUEUE_CONTROLS_ATTR = "data-ytmusic-pro-queue-controls";
        const QUEUE_HANDLE_ATTR = "data-ytmusic-pro-queue-handle";
        const QUEUE_REMOVE_ATTR = "data-ytmusic-pro-queue-remove";
        const QUEUE_MENU_HIDDEN_ATTR = "data-ytmusic-pro-queue-menu-hidden";
        const QUEUE_POPUP_HIDDEN_ATTR = "data-ytmusic-pro-queue-popup-hidden";
        const QUEUE_DRAGGING_ATTR = "data-ytmusic-pro-queue-dragging";
        const QUEUE_DROP_TARGET_ATTR = "data-ytmusic-pro-queue-drop-target";
        const QUEUE_HANDLE_LINE_ATTR = "data-ytmusic-pro-queue-handle-line";
        const QUEUE_LONG_PRESS_MS = 260;
        const QUEUE_DRAG_CANCEL_PX = 14;

        let mountTimer = 0;
        let lastJamActivationAt = 0;
        let activeInjectedQueueDrag = null;
        let lastQueueRemoveFingerprint = "";
        let lastQueueRemoveAt = 0;

        function ensureStyles() {
            if (document.getElementById(STYLE_ID)) {
                return;
            }

            const style = document.createElement("style");
            style.id = STYLE_ID;
            style.textContent = `
                ytmusic-mealbar-promo-renderer,
                ytmusic-upsell-dialog-renderer {
                    display: none !important;
                }

                ytmusic-player-bar {
                    background: rgba(0, 0, 0, 0.7) !important;
                    backdrop-filter: blur(15px) !important;
                    border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
                }

                .${LEGACY_REMOVE_CLASS} {
                    display: none !important;
                }

                [${QUEUE_ITEM_ATTR}="true"] {
                    position: relative !important;
                }

                [${QUEUE_ITEM_ATTR}="true"][${QUEUE_DRAGGING_ATTR}="true"] {
                    opacity: 0.84 !important;
                }

                [${QUEUE_ITEM_ATTR}="true"][${QUEUE_DROP_TARGET_ATTR}="true"] {
                    background: rgba(255, 255, 255, 0.08) !important;
                    outline: 1px solid rgba(255, 255, 255, 0.16) !important;
                    outline-offset: -1px !important;
                    border-radius: 16px !important;
                }

                [${QUEUE_CONTROLS_ATTR}="true"] {
                    position: absolute !important;
                    inset: 0 !important;
                    pointer-events: none !important;
                    z-index: 30 !important;
                }

                [${QUEUE_HANDLE_ATTR}="true"],
                [${QUEUE_REMOVE_ATTR}="true"] {
                    position: absolute !important;
                    top: 50% !important;
                    transform: translateY(-50%) !important;
                    border: 0 !important;
                    pointer-events: auto !important;
                    display: flex !important;
                    align-items: center !important;
                    justify-content: center !important;
                    user-select: none !important;
                    -webkit-user-select: none !important;
                    -webkit-touch-callout: none !important;
                    box-sizing: border-box !important;
                    padding: 0 !important;
                    margin: 0 !important;
                    z-index: 2 !important;
                }

                [${QUEUE_HANDLE_ATTR}="true"] {
                    left: 8px !important;
                    width: 44px !important;
                    height: 58px !important;
                    border-radius: 20px !important;
                    background: rgba(255, 255, 255, 0.16) !important;
                    border: 1px solid rgba(255, 255, 255, 0.2) !important;
                    backdrop-filter: blur(8px) !important;
                    touch-action: none !important;
                }

                [${QUEUE_HANDLE_ATTR}="true"] [${QUEUE_HANDLE_LINE_ATTR}="true"] {
                    width: 3px !important;
                    height: 21px !important;
                    border-radius: 999px !important;
                    background: rgba(255, 255, 255, 0.86) !important;
                    margin: 0 3px !important;
                    display: block !important;
                }

                [${QUEUE_REMOVE_ATTR}="true"] {
                    right: 8px !important;
                    width: 54px !important;
                    height: 54px !important;
                    border-radius: 999px !important;
                    background: rgba(176, 30, 59, 0.98) !important;
                    color: #ffffff !important;
                    font-size: 28px !important;
                    font-weight: 700 !important;
                    line-height: 1 !important;
                    box-shadow: 0 6px 18px rgba(176, 30, 59, 0.34) !important;
                    touch-action: manipulation !important;
                }

                [${QUEUE_REMOVE_ATTR}="true"] > span {
                    transform: translateY(-1px) !important;
                }

                [${QUEUE_MENU_HIDDEN_ATTR}="true"] {
                    opacity: 0 !important;
                    pointer-events: none !important;
                }

                [${QUEUE_POPUP_HIDDEN_ATTR}="true"] {
                    opacity: 0 !important;
                    pointer-events: none !important;
                }

            `;

            (document.head || document.documentElement).appendChild(style);
        }

        function normalizeLabel(value) {
            return String(value || "")
                .toLowerCase()
                .normalize("NFD")
                .replace(/[\u0300-\u036f]/g, "")
                .replace(/[\u0027\u2019]/g, "")
                .replace(/\s+/g, " ")
                .trim();
        }

        function matchesAnyPattern(value, patterns) {
            const normalized = normalizeLabel(value);
            if (!normalized) {
                return false;
            }

            return patterns.some(function (pattern) {
                return normalized === pattern || normalized.indexOf(pattern) >= 0;
            });
        }

        function matchesElementLabel(element, patterns) {
            if (!element) {
                return false;
            }

            return matchesAnyPattern(element.getAttribute && element.getAttribute("aria-label"), patterns) ||
                matchesAnyPattern(element.getAttribute && element.getAttribute("title"), patterns) ||
                matchesAnyPattern(element.innerText, patterns) ||
                matchesAnyPattern(element.textContent, patterns);
        }

        function matchesOpenAppLabel(value) {
            return matchesAnyPattern(value, OPEN_APP_PATTERNS);
        }

        function getRoots() {
            const roots = [document];
            const queue = [document.documentElement];

            while (queue.length > 0) {
                const node = queue.shift();
                if (!node || !node.querySelectorAll) {
                    continue;
                }

                node.querySelectorAll("*").forEach(function (element) {
                    if (element.shadowRoot) {
                        roots.push(element.shadowRoot);
                        queue.push(element.shadowRoot);
                    }
                });
            }

            return roots;
        }

        function queryAllDeep(root, selector) {
            const results = [];
            const seen = new Set();
            const queue = [root];

            while (queue.length > 0) {
                const node = queue.shift();
                if (!node || !node.querySelectorAll) {
                    continue;
                }

                node.querySelectorAll(selector).forEach(function (element) {
                    if (!seen.has(element)) {
                        seen.add(element);
                        results.push(element);
                    }
                });

                node.querySelectorAll("*").forEach(function (element) {
                    if (element.shadowRoot) {
                        queue.push(element.shadowRoot);
                    }
                });
            }

            return results;
        }

        function queryFirstDeep(root, selector) {
            const matches = queryAllDeep(root, selector);
            return matches.length > 0 ? matches[0] : null;
        }

        function isVisible(element) {
            if (!element || !element.getBoundingClientRect) {
                return false;
            }

            const rect = element.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
        }

        function findClickableAncestor(element) {
            if (!element || typeof element.closest !== "function") {
                return element || null;
            }

            return element.closest([
                "button",
                "a",
                "[role=\"button\"]",
                "[role=\"menuitem\"]",
                "ytmusic-button-renderer",
                "yt-button-shape",
                "tp-yt-paper-button",
                "tp-yt-paper-icon-button",
                "yt-icon-button",
                "tp-yt-paper-item",
                "ytmusic-menu-service-item-renderer"
            ].join(", ")) || element;
        }

        function clickElement(element) {
            if (!element) {
                return false;
            }

            try {
                element.scrollIntoView({ block: "nearest", inline: "nearest" });
            } catch (_error) {
            }

            try {
                if (typeof element.click === "function") {
                    element.click();
                    return true;
                }
            } catch (_error) {
            }

            return false;
        }

        function replaceTextNodes(root) {
            if (!root || !root.ownerDocument) {
                return false;
            }

            const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            let changed = false;
            let node = walker.nextNode();

            while (node) {
                if (matchesOpenAppLabel(node.nodeValue)) {
                    node.nodeValue = "Jam";
                    changed = true;
                }
                node = walker.nextNode();
            }

            return changed;
        }

        function replaceAccessibleLabels(root) {
            if (!root || !root.querySelectorAll) {
                return;
            }

            root.querySelectorAll("[aria-label], [title]").forEach(function (element) {
                if (matchesOpenAppLabel(element.getAttribute("aria-label"))) {
                    element.setAttribute("aria-label", "Jam");
                }
                if (matchesOpenAppLabel(element.getAttribute("title"))) {
                    element.setAttribute("title", "Jam");
                }
            });
        }

        function bindJamAction(candidate) {
            if (candidate[JAM_ATTR]) {
                return;
            }

            const navigateToJam = function (event) {
                if (event) {
                    event.preventDefault();
                    event.stopPropagation();
                    if (event.stopImmediatePropagation) {
                        event.stopImmediatePropagation();
                    }
                }

                const now = Date.now();
                if (now - lastJamActivationAt < 500) {
                    return;
                }
                lastJamActivationAt = now;

                try {
                    window.location.href = JAM_URL;
                } catch (error) {
                    console.error("YTMusic Pro: unable to navigate to jam URL", error);
                }
            };

            if (candidate.tagName && candidate.tagName.toLowerCase() === "a") {
                try {
                    candidate.setAttribute("href", JAM_URL);
                    candidate.href = JAM_URL;
                } catch (_error) {
                }
            }

            candidate.removeAttribute("target");
            candidate.removeAttribute("rel");
            candidate.removeAttribute("disabled");
            candidate.setAttribute("aria-disabled", "false");
            candidate.style.pointerEvents = "auto";
            candidate.style.cursor = "pointer";
            candidate.style.touchAction = "manipulation";
            candidate.addEventListener("click", navigateToJam, true);
            candidate.addEventListener("touchend", navigateToJam, true);
            candidate[JAM_ATTR] = navigateToJam;
            candidate.setAttribute(JAM_ATTR, JAM_VALUE);
        }

        function patchJamCandidate(candidate) {
            if (!candidate || !candidate.isConnected) {
                return false;
            }

            let changed = replaceTextNodes(candidate);
            replaceAccessibleLabels(candidate);

            if (matchesOpenAppLabel(candidate.getAttribute("aria-label"))) {
                candidate.setAttribute("aria-label", "Jam");
                changed = true;
            }

            if (matchesOpenAppLabel(candidate.getAttribute("title"))) {
                candidate.setAttribute("title", "Jam");
                changed = true;
            }

            const ownTextMatch =
                matchesOpenAppLabel(candidate.innerText) ||
                matchesOpenAppLabel(candidate.textContent);
            if (!changed && ownTextMatch && candidate.childElementCount === 0) {
                candidate.textContent = "Jam";
                changed = true;
            }

            bindJamAction(candidate);
            return changed || candidate.getAttribute(JAM_ATTR) === JAM_VALUE;
        }

        function collectOpenAppCandidates() {
            const selector = [
                "[aria-label]",
                "[title]",
                "button",
                "a",
                "[role=\"button\"]",
                "ytmusic-button-renderer",
                "yt-button-shape",
                "tp-yt-paper-button"
            ].join(", ");
            const seen = new Set();
            const results = [];

            getRoots().forEach(function (root) {
                root.querySelectorAll(selector).forEach(function (element) {
                    const matched =
                        matchesOpenAppLabel(element.getAttribute("aria-label")) ||
                        matchesOpenAppLabel(element.getAttribute("title")) ||
                        matchesOpenAppLabel(element.innerText) ||
                        matchesOpenAppLabel(element.textContent);
                    if (!matched) {
                        return;
                    }

                    const candidate = findClickableAncestor(element);
                    if (!candidate || seen.has(candidate)) {
                        return;
                    }

                    seen.add(candidate);
                    results.push(candidate);
                });
            });

            return results;
        }

        function mountJamButtons() {
            collectOpenAppCandidates().forEach(patchJamCandidate);
        }

        function updateDebugBadge() {
        }

        function isQueueLikeContainer(element) {
            if (!element || typeof element.closest !== "function") {
                return false;
            }

            return !!element.closest([
                "ytmusic-player-page",
                "ytmusic-player-queue",
                "ytmusic-tab-renderer",
                "[page-type]",
                "#contents"
            ].join(", "));
        }

        function isQueueLikeItem(element) {
            if (!element || !isVisible(element) || !isQueueLikeContainer(element)) {
                return false;
            }

            if (element.closest && element.closest("ytmusic-menu-popup-renderer, ytmusic-menu-service-item-renderer")) {
                return false;
            }

            const text = normalizeLabel(element.innerText || element.textContent);
            if (!text) {
                return false;
            }

            const hasThumbnail = !!queryFirstDeep(element, "img, ytmusic-thumbnail-renderer");
            const hasMenuButton = !!findQueueMenuButton(element);
            const hasDuration = /\b\d{1,2}:\d{2}\b/.test(text);

            return hasThumbnail || hasMenuButton || hasDuration;
        }

        function collectQueueItems() {
            const seen = new Set();
            const items = [];
            const selectors = [
                "ytmusic-player-queue-item",
                "ytmusic-player-page ytmusic-responsive-list-item-renderer",
                "ytmusic-player-page ytmusic-two-row-item-renderer",
                "ytmusic-player-page ytmusic-shelf-renderer ytmusic-responsive-list-item-renderer"
            ];

            getRoots().forEach(function (root) {
                selectors.forEach(function (selector) {
                    root.querySelectorAll(selector).forEach(function (item) {
                        if (seen.has(item) || !isQueueLikeItem(item)) {
                            return;
                        }
                        seen.add(item);
                        items.push(item);
                    });
                });
            });

            return items;
        }

        function isInjectedQueueControl(element) {
            return !!(element && element.closest && element.closest("[" + QUEUE_CONTROLS_ATTR + "]"));
        }

        function findQueueMenuButton(item) {
            const exact = queryFirstDeep(item, "ytmusic-menu-renderer yt-button-shape[id=\"button-shape\"] button");
            if (exact && isVisible(exact) && !isInjectedQueueControl(exact)) {
                return exact;
            }

            const candidates = queryAllDeep(
                item,
                "ytmusic-menu-renderer button, button, [role=\"button\"], tp-yt-paper-icon-button, yt-icon-button, yt-button-shape button"
            ).filter(function (element) {
                return isVisible(element) && !isInjectedQueueControl(element);
            });

            return candidates.find(function (element) {
                return matchesElementLabel(element, QUEUE_MENU_PATTERNS);
            }) || candidates[candidates.length - 1] || null;
        }

        function findQueueDragTarget(item) {
            const explicit = queryFirstDeep(
                item,
                "[draggable=\"true\"], #drag-handle, .drag-handle, [aria-label*=\"drag\" i], [aria-label*=\"reorder\" i]"
            );
            if (explicit && isVisible(explicit)) {
                return explicit;
            }

            return queryAllDeep(
                item,
                "button, [role=\"button\"], tp-yt-paper-icon-button, yt-icon-button"
            ).find(function (element) {
                return isVisible(element) &&
                    !isInjectedQueueControl(element) &&
                    matchesElementLabel(element, QUEUE_DRAG_PATTERNS);
            }) || item;
        }

        function extractQueueItemParts(item) {
            const titleElement = queryFirstDeep(
                item,
                [
                    ".title",
                    ".primary-text",
                    "[slot=\"title\"]",
                    "yt-formatted-string.title",
                    "yt-formatted-string[title]",
                    "h3",
                    "a[href*='watch']"
                ].join(", ")
            );
            const subtitleElement = queryFirstDeep(
                item,
                [
                    ".subtitle",
                    ".secondary-text",
                    ".byline",
                    "yt-formatted-string.byline",
                    "yt-formatted-string.subtitle"
                ].join(", ")
            );
            const durationMatch = (item.innerText || item.textContent || "").match(/\b\d{1,2}:\d{2}\b/);
            const image = queryFirstDeep(item, "img");
            const title = normalizeLabel(titleElement && (titleElement.getAttribute("title") || titleElement.innerText || titleElement.textContent));
            const subtitle = normalizeLabel(subtitleElement && (subtitleElement.getAttribute("title") || subtitleElement.innerText || subtitleElement.textContent));
            const duration = normalizeLabel(durationMatch ? durationMatch[0] : "");
            const imageKey = normalizeLabel(image && (image.getAttribute("src") || image.currentSrc || ""));

            return {
                title: title,
                subtitle: subtitle,
                duration: duration,
                imageKey: imageKey
            };
        }

        function fingerprintQueueItem(item) {
            const parts = extractQueueItemParts(item);
            const fingerprint = [parts.title, parts.subtitle, parts.duration, parts.imageKey].filter(Boolean).join("|");
            return fingerprint || normalizeLabel(item.innerText || item.textContent);
        }

        function roundRect(rect) {
            return {
                left: Math.round(rect.left * 10) / 10,
                top: Math.round(rect.top * 10) / 10,
                width: Math.round(rect.width * 10) / 10,
                height: Math.round(rect.height * 10) / 10
            };
        }

        function isViewportVisibleRect(rect) {
            if (!rect || rect.width <= 1 || rect.height <= 1) {
                return false;
            }

            const margin = 24;
            return rect.bottom >= -margin &&
                rect.right >= -margin &&
                rect.top <= window.innerHeight + margin &&
                rect.left <= window.innerWidth + margin;
        }

        function stopQueueControlEvent(event, shouldPreventDefault) {
            if (!event) {
                return;
            }

            if (shouldPreventDefault && typeof event.preventDefault === "function") {
                event.preventDefault();
            }
            if (typeof event.stopPropagation === "function") {
                event.stopPropagation();
            }
            if (typeof event.stopImmediatePropagation === "function") {
                event.stopImmediatePropagation();
            }
        }

        function extractEventPoint(event) {
            const touch =
                (event && event.touches && event.touches[0]) ||
                (event && event.changedTouches && event.changedTouches[0]) ||
                event ||
                {};

            return {
                x: Number(touch.clientX || touch.pageX || 0),
                y: Number(touch.clientY || touch.pageY || 0)
            };
        }

        function findScrollableAncestor(element) {
            let current = element;

            while (current) {
                if (current === document.body || current === document.documentElement) {
                    break;
                }

                try {
                    const style = window.getComputedStyle(current);
                    const overflowY = String(style.overflowY || "");
                    if (/(auto|scroll)/i.test(overflowY) && current.scrollHeight > current.clientHeight + 8) {
                        return current;
                    }
                } catch (_error) {
                }

                if (current.parentElement) {
                    current = current.parentElement;
                    continue;
                }

                const root = current.getRootNode ? current.getRootNode() : null;
                current = root && root.host ? root.host : null;
            }

            return document.scrollingElement || document.documentElement || document.body;
        }

        function resolveQueueRuntime(item) {
            if (!item || !item.isConnected) {
                return null;
            }

            const items = collectQueueItems();
            const index = items.indexOf(item);
            if (index < 0) {
                return null;
            }

            return {
                item: item,
                items: items,
                index: index,
                fingerprint: fingerprintQueueItem(item)
            };
        }

        function clearQueueDropTarget() {
            if (activeInjectedQueueDrag && activeInjectedQueueDrag.targetItem) {
                activeInjectedQueueDrag.targetItem.removeAttribute(QUEUE_DROP_TARGET_ATTR);
            }
        }

        function updateQueueDropTarget(targetIndex) {
            if (!activeInjectedQueueDrag) {
                return;
            }

            const items = collectQueueItems();
            const nextItem = items[targetIndex] || null;
            if (activeInjectedQueueDrag.targetItem === nextItem) {
                activeInjectedQueueDrag.targetIndex = targetIndex;
                return;
            }

            clearQueueDropTarget();
            activeInjectedQueueDrag.targetIndex = targetIndex;
            activeInjectedQueueDrag.targetItem = nextItem;
            if (nextItem) {
                nextItem.setAttribute(QUEUE_DROP_TARGET_ATTR, "true");
            }
        }

        function clearInjectedQueueDrag(cancelled) {
            const drag = activeInjectedQueueDrag;
            if (!drag) {
                return;
            }

            if (drag.longPressTimer) {
                window.clearTimeout(drag.longPressTimer);
            }
            if (drag.item) {
                drag.item.removeAttribute(QUEUE_DRAGGING_ATTR);
            }
            clearQueueDropTarget();

            if (!cancelled && drag.dragActive && drag.targetIndex != null && drag.targetIndex !== drag.sourceIndex) {
                triggerQueueItemMoveByIdentity(drag.fingerprint, drag.sourceIndex, drag.targetIndex);
            }

            if (drag.handle && drag.pointerId != null && typeof drag.handle.releasePointerCapture === "function") {
                try {
                    drag.handle.releasePointerCapture(drag.pointerId);
                } catch (_error) {
                }
            }

            activeInjectedQueueDrag = null;
        }

        function maybeAutoScrollQueue(drag, pointerY) {
            if (!drag || !drag.scrollContainer) {
                return;
            }

            const container = drag.scrollContainer;
            const containerRect =
                container === document.scrollingElement || container === document.documentElement || container === document.body
                    ? {
                        top: 0,
                        bottom: window.innerHeight
                    }
                    : container.getBoundingClientRect();
            const threshold = 72;
            const step = 18;

            if (pointerY <= containerRect.top + threshold) {
                if (typeof container.scrollBy === "function") {
                    container.scrollBy(0, -step);
                } else {
                    container.scrollTop -= step;
                }
                return;
            }

            if (pointerY >= containerRect.bottom - threshold) {
                if (typeof container.scrollBy === "function") {
                    container.scrollBy(0, step);
                } else {
                    container.scrollTop += step;
                }
            }
        }

        function findNearestQueueIndex(pointerY) {
            const items = collectQueueItems();
            if (!items.length) {
                return null;
            }

            let nearestIndex = null;
            let nearestDistance = Number.POSITIVE_INFINITY;
            let firstVisibleIndex = null;
            let firstVisibleCenter = null;
            let lastVisibleIndex = null;
            let lastVisibleCenter = null;

            items.forEach(function (item, index) {
                const rect = item.getBoundingClientRect();
                if (!isViewportVisibleRect(rect)) {
                    return;
                }

                const centerY = rect.top + (rect.height / 2);
                if (firstVisibleIndex == null) {
                    firstVisibleIndex = index;
                    firstVisibleCenter = centerY;
                }
                lastVisibleIndex = index;
                lastVisibleCenter = centerY;

                const distance = Math.abs(pointerY - centerY);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = index;
                }
            });

            if (nearestIndex != null) {
                return nearestIndex;
            }

            if (firstVisibleIndex != null && pointerY <= firstVisibleCenter) {
                return firstVisibleIndex;
            }
            if (lastVisibleIndex != null && pointerY >= lastVisibleCenter) {
                return lastVisibleIndex;
            }

            return items.length - 1;
        }

        function activateInjectedQueueDrag() {
            const drag = activeInjectedQueueDrag;
            if (!drag || drag.dragActive) {
                return;
            }

            drag.dragActive = true;
            drag.item.setAttribute(QUEUE_DRAGGING_ATTR, "true");
            updateQueueDropTarget(drag.sourceIndex);
        }

        function handleInjectedQueuePointerMove(event) {
            const drag = activeInjectedQueueDrag;
            if (!drag) {
                return;
            }

            if (drag.pointerId != null && event.pointerId != null && drag.pointerId !== event.pointerId) {
                return;
            }

            const point = extractEventPoint(event);
            drag.lastX = point.x;
            drag.lastY = point.y;
            stopQueueControlEvent(event, true);

            if (!drag.dragActive) {
                const deltaX = point.x - drag.startX;
                const deltaY = point.y - drag.startY;
                if ((deltaX * deltaX) + (deltaY * deltaY) > QUEUE_DRAG_CANCEL_PX * QUEUE_DRAG_CANCEL_PX) {
                    clearInjectedQueueDrag(true);
                }
                return;
            }

            stopQueueControlEvent(event, true);
            maybeAutoScrollQueue(drag, point.y);
            const targetIndex = findNearestQueueIndex(point.y);
            if (targetIndex != null) {
                updateQueueDropTarget(targetIndex);
            }
        }

        function handleInjectedQueuePointerUp(event, cancelled) {
            const drag = activeInjectedQueueDrag;
            if (!drag) {
                return;
            }

            if (drag.pointerId != null && event && event.pointerId != null && drag.pointerId !== event.pointerId) {
                return;
            }

            stopQueueControlEvent(event, true);
            clearInjectedQueueDrag(!!cancelled);
        }

        function startInjectedQueueHandleDrag(handle, event) {
            const item = resolveMountedQueueItem(handle);
            const runtime = resolveQueueRuntime(item);
            if (!runtime) {
                return;
            }

            clearInjectedQueueDrag(true);
            stopQueueControlEvent(event, true);

            const point = extractEventPoint(event);
            activeInjectedQueueDrag = {
                item: runtime.item,
                fingerprint: runtime.fingerprint,
                sourceIndex: runtime.index,
                targetIndex: runtime.index,
                targetItem: runtime.item,
                scrollContainer: findScrollableAncestor(runtime.item),
                handle: handle,
                pointerId: event.pointerId != null ? event.pointerId : null,
                startX: point.x,
                startY: point.y,
                lastX: point.x,
                lastY: point.y,
                dragActive: false,
                longPressTimer: window.setTimeout(activateInjectedQueueDrag, QUEUE_LONG_PRESS_MS)
            };

            if (typeof handle.setPointerCapture === "function" && event.pointerId != null) {
                try {
                    handle.setPointerCapture(event.pointerId);
                } catch (_error) {
                }
            }
        }

        function resolveMountedQueueItem(control) {
            if (!control) {
                return null;
            }

            const controlsRoot = control.closest ? control.closest("[" + QUEUE_CONTROLS_ATTR + "]") : null;
            if (controlsRoot && controlsRoot.__ytmusicProQueueItem && controlsRoot.__ytmusicProQueueItem.isConnected) {
                return controlsRoot.__ytmusicProQueueItem;
            }

            return control.closest ? control.closest("[" + QUEUE_ITEM_ATTR + "]") : null;
        }

        function handleQueueRemoveClick(event) {
            requestQueueItemRemoval(event.currentTarget, event);
        }

        function requestQueueItemRemoval(control, event) {
            stopQueueControlEvent(event, true);
            const item = resolveMountedQueueItem(control);
            const runtime = resolveQueueRuntime(item);
            if (!runtime) {
                return;
            }

            const now = Date.now();
            if (
                runtime.fingerprint &&
                runtime.fingerprint === lastQueueRemoveFingerprint &&
                now - lastQueueRemoveAt < 500
            ) {
                return;
            }

            lastQueueRemoveFingerprint = runtime.fingerprint;
            lastQueueRemoveAt = now;
            triggerQueueItemRemovalByIdentity(runtime.fingerprint, runtime.index);
        }

        function suppressQueueMenuButton(item) {
            const menuButton = findQueueMenuButton(item);
            if (!menuButton) {
                return;
            }

            menuButton.setAttribute(QUEUE_MENU_HIDDEN_ATTR, "true");
        }

        function restoreOrphanQueueMenus(activeItems) {
            Array.prototype.forEach.call(
                document.querySelectorAll("[" + QUEUE_MENU_HIDDEN_ATTR + "=\"true\"]"),
                function (button) {
                    const hostItem = button.closest ? button.closest("[" + QUEUE_ITEM_ATTR + "]") : null;
                    if (!hostItem || !activeItems.has(hostItem)) {
                        button.removeAttribute(QUEUE_MENU_HIDDEN_ATTR);
                    }
                }
            );
        }

        function createQueueHandleElement() {
            const handle = document.createElement("button");
            handle.type = "button";
            handle.setAttribute(QUEUE_HANDLE_ATTR, "true");
            handle.setAttribute("aria-label", "Sposta il brano");

            for (let index = 0; index < 2; index += 1) {
                const line = document.createElement("span");
                line.setAttribute(QUEUE_HANDLE_LINE_ATTR, "true");
                handle.appendChild(line);
            }

            handle.addEventListener("pointerdown", function (event) {
                if (event.button != null && event.button !== 0) {
                    return;
                }
                startInjectedQueueHandleDrag(handle, event);
            }, true);
            handle.addEventListener("touchstart", function (event) {
                startInjectedQueueHandleDrag(handle, event);
            }, { capture: true, passive: false });
            handle.addEventListener("dragstart", function (event) {
                stopQueueControlEvent(event, true);
            }, true);
            return handle;
        }

        function createQueueRemoveElement() {
            const removeButton = document.createElement("button");
            removeButton.type = "button";
            removeButton.setAttribute(QUEUE_REMOVE_ATTR, "true");
            removeButton.setAttribute("aria-label", "Rimuovi il brano dalla coda");

            const glyph = document.createElement("span");
            glyph.setAttribute("aria-hidden", "true");
            glyph.textContent = "X";
            removeButton.appendChild(glyph);

            removeButton.addEventListener("pointerdown", function (event) {
                stopQueueControlEvent(event, true);
            }, true);
            removeButton.addEventListener("pointerup", function (event) {
                requestQueueItemRemoval(removeButton, event);
            }, true);
            removeButton.addEventListener("touchstart", function (event) {
                stopQueueControlEvent(event, true);
            }, { capture: true, passive: false });
            removeButton.addEventListener("touchend", function (event) {
                requestQueueItemRemoval(removeButton, event);
            }, { capture: true, passive: false });
            removeButton.addEventListener("click", handleQueueRemoveClick, true);
            return removeButton;
        }

        function ensureQueueControls(item) {
            if (!item || !item.isConnected) {
                return null;
            }

            item.setAttribute(QUEUE_ITEM_ATTR, "true");
            suppressQueueMenuButton(item);

            let controls = queryFirstDeep(item, "[" + QUEUE_CONTROLS_ATTR + "]");
            if (!controls || controls.closest("[" + QUEUE_ITEM_ATTR + "]") !== item) {
                controls = document.createElement("div");
                controls.setAttribute(QUEUE_CONTROLS_ATTR, "true");
                controls.appendChild(createQueueHandleElement());
                controls.appendChild(createQueueRemoveElement());
                item.appendChild(controls);
            }

            controls.__ytmusicProQueueItem = item;
            return controls;
        }

        function cleanupQueueControls(activeItems) {
            Array.prototype.forEach.call(
                document.querySelectorAll("[" + QUEUE_CONTROLS_ATTR + "]"),
                function (controls) {
                    const item =
                        controls.__ytmusicProQueueItem ||
                        (controls.parentElement && controls.parentElement.closest
                            ? controls.parentElement.closest("[" + QUEUE_ITEM_ATTR + "]")
                            : null);
                    if (!item || !activeItems.has(item)) {
                        if (controls.parentNode) {
                            controls.parentNode.removeChild(controls);
                        }
                    }
                }
            );

            Array.prototype.forEach.call(
                document.querySelectorAll("[" + QUEUE_ITEM_ATTR + "]"),
                function (item) {
                    if (!activeItems.has(item)) {
                        item.removeAttribute(QUEUE_ITEM_ATTR);
                        item.removeAttribute(QUEUE_DRAGGING_ATTR);
                        item.removeAttribute(QUEUE_DROP_TARGET_ATTR);
                    }
                }
            );

            restoreOrphanQueueMenus(activeItems);

            if (activeInjectedQueueDrag && !activeItems.has(activeInjectedQueueDrag.item)) {
                clearInjectedQueueDrag(true);
            }
        }

        function mountQueueControls() {
            const items = collectQueueItems();
            const activeItems = new Set(items);

            items.forEach(function (item) {
                ensureQueueControls(item);
            });

            cleanupQueueControls(activeItems);
        }

        function publishQueueLayout() {
            queryAllDeep(document, "." + LEGACY_REMOVE_CLASS).forEach(function (element) {
                if (element && element.parentNode) {
                    element.parentNode.removeChild(element);
                }
            });

            mountQueueControls();

            const queueEntries = collectQueueItems().map(function (item, index) {
                const itemRect = item.getBoundingClientRect();
                const dragTarget = findQueueDragTarget(item);
                const dragRect = dragTarget && dragTarget.getBoundingClientRect
                    ? dragTarget.getBoundingClientRect()
                    : itemRect;

                return {
                    item: item,
                    index: index,
                    itemRect: itemRect,
                    dragRect: dragRect
                };
            });
            const queueItems = queueEntries.map(function (entry) { return entry.item; });
            const visibleQueueEntries = queueEntries.filter(function (entry) {
                return isViewportVisibleRect(entry.itemRect);
            });
            updateDebugBadge({
                items: visibleQueueEntries.length,
                menuButtons: queueItems.filter(function (item) { return !!findQueueMenuButton(item); }).length,
                dragTargets: queueItems.filter(function (item) { return !!findQueueDragTarget(item); }).length,
                tags: queueItems.slice(0, 2).map(function (item) {
                    return String(item.tagName || "").toLowerCase();
                }).filter(Boolean).join("/")
            });

            void visibleQueueEntries;
        }

        function resolveQueueItemByIndex(index) {
            return collectQueueItems()[index] || null;
        }

        function resolveQueueItemByIdentity(fingerprint, fallbackIndex) {
            const items = collectQueueItems();
            const normalizedFingerprint = normalizeLabel(fingerprint);

            if (normalizedFingerprint) {
                const exact = items.find(function (item) {
                    return fingerprintQueueItem(item) === normalizedFingerprint;
                });
                if (exact) {
                    return exact;
                }
            }

            return items[fallbackIndex] || null;
        }

        function findQueuePopupContainer() {
            return queryFirstDeep(document, "ytmusic-app ytmusic-popup-container tp-yt-iron-dropdown") ||
                queryFirstDeep(document, "ytmusic-popup-container tp-yt-iron-dropdown") ||
                queryFirstDeep(document, "tp-yt-paper-dialog");
        }

        function withHiddenQueuePopup() {
            let released = false;
            let observer = null;

            function markPopupHidden() {
                const popup = findQueuePopupContainer();
                if (popup) {
                    popup.setAttribute(QUEUE_POPUP_HIDDEN_ATTR, "true");
                }
            }

            try {
                const root = document.documentElement || document;
                if (root && typeof MutationObserver !== "undefined") {
                    observer = new MutationObserver(markPopupHidden);
                    observer.observe(root, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ["opened", "aria-hidden", "style", "class"]
                    });
                }
            } catch (_error) {
            }

            markPopupHidden();

            return function release() {
                if (released) {
                    return;
                }
                released = true;

                if (observer) {
                    observer.disconnect();
                }

                Array.prototype.forEach.call(
                    document.querySelectorAll("[" + QUEUE_POPUP_HIDDEN_ATTR + "=\"true\"]"),
                    function (popup) {
                        popup.removeAttribute(QUEUE_POPUP_HIDDEN_ATTR);
                    }
                );
            };
        }

        function findQueueRemoveAction(popup) {
            if (!popup) {
                return null;
            }

            const candidates = [
                queryFirstDeep(popup, "tp-yt-paper-listbox ytmusic-menu-service-item-renderer:nth-of-type(4)"),
                queryFirstDeep(popup, "tp-yt-paper-listbox ytmusic-menu-service-item-renderer:nth-of-type(3)")
            ].filter(Boolean);

            const exact = candidates.find(function (element) {
                return matchesElementLabel(element, QUEUE_REMOVE_PATTERNS);
            });
            if (exact) {
                return findClickableAncestor(exact);
            }

            return queryAllDeep(
                popup,
                "ytmusic-menu-service-item-renderer, ytmusic-toggle-menu-service-item-renderer, tp-yt-paper-item, [role=\"menuitem\"], button, a, yt-formatted-string"
            ).map(findClickableAncestor).find(function (element) {
                return element && isVisible(element) && matchesElementLabel(element, QUEUE_REMOVE_PATTERNS);
            }) || null;
        }

        function removeQueueItemFromHosts(item) {
            const sourceData =
                item.data ||
                (item.__data && (item.__data.data || item.__data.item)) ||
                null;

            if (!sourceData) {
                return false;
            }

            const visited = new Set();
            let current = item;

            while (current) {
                if (!visited.has(current)) {
                    visited.add(current);
                    if (tryRemoveFromHost(current, sourceData)) {
                        return true;
                    }
                }

                if (current.parentElement) {
                    current = current.parentElement;
                    continue;
                }

                const root = current.getRootNode ? current.getRootNode() : null;
                current = root && root.host ? root.host : null;
            }

            return false;
        }

        function tryRemoveFromHost(host, sourceData) {
            if (!host) {
                return false;
            }

            const candidates = [host, host.__data, host.data, host.__dataHost].filter(Boolean);
            for (let i = 0; i < candidates.length; i += 1) {
                const container = candidates[i];
                const keys = Object.keys(container);

                for (let keyIndex = 0; keyIndex < keys.length; keyIndex += 1) {
                    const key = keys[keyIndex];
                    const value = container[key];
                    if (!Array.isArray(value) || value.length < 1) {
                        continue;
                    }

                    const sourceIndex = value.indexOf(sourceData);
                    if (sourceIndex < 0) {
                        continue;
                    }

                    value.splice(sourceIndex, 1);

                    if (typeof host.notifyPath === "function") {
                        try {
                            host.notifyPath(key, value);
                        } catch (_error) {
                        }
                    }
                    if (typeof host.requestUpdate === "function") {
                        try {
                            host.requestUpdate();
                        } catch (_error) {
                        }
                    }
                    if (typeof host.set === "function") {
                        try {
                            host.set(key, value.slice());
                        } catch (_error) {
                        }
                    }
                    return true;
                }
            }

            return false;
        }

        function triggerQueueItemRemovalByIdentity(fingerprint, index) {
            const item = resolveQueueItemByIdentity(fingerprint, index);
            if (!item) {
                return false;
            }

            const removedFromHost = removeQueueItemFromHosts(item);
            if (removedFromHost) {
                window.setTimeout(publishQueueLayout, 80);
                window.setTimeout(publishQueueLayout, 240);
                window.setTimeout(scheduleMount, 420);
                return true;
            }

            const menuButton = findQueueMenuButton(item);
            if (!menuButton) {
                console.warn("YTMusic Pro: queue menu button not found for index", index);
                return false;
            }

            menuButton.removeAttribute(QUEUE_MENU_HIDDEN_ATTR);
            const releasePopupHide = withHiddenQueuePopup();

            if (!clickElement(menuButton)) {
                try {
                    item.dispatchEvent(new MouseEvent("contextmenu", { bubbles: true, cancelable: false }));
                } catch (_error) {
                }
            }

            [30, 90, 180, 320, 520].forEach(function (delay) {
                window.setTimeout(function () {
                    const currentPopup = findQueuePopupContainer();
                    const action = findQueueRemoveAction(currentPopup);
                    if (action) {
                        clickElement(action);
                    }
                    menuButton.setAttribute(QUEUE_MENU_HIDDEN_ATTR, "true");
                    publishQueueLayout();
                }, delay);
            });
            window.setTimeout(releasePopupHide, 900);

            return true;
        }

        function reorderArrayInHosts(item, targetItem, targetIndex) {
            const sourceData =
                item.data ||
                (item.__data && (item.__data.data || item.__data.item)) ||
                null;
            const targetData =
                targetItem && (
                    targetItem.data ||
                    (targetItem.__data && (targetItem.__data.data || targetItem.__data.item)) ||
                    null
                );

            if (!sourceData) {
                return false;
            }

            const visited = new Set();
            let current = item;

            while (current) {
                if (!visited.has(current)) {
                    visited.add(current);
                    if (tryReorderOnHost(current, sourceData, targetData, targetIndex)) {
                        return true;
                    }
                }

                if (current.parentElement) {
                    current = current.parentElement;
                    continue;
                }

                const root = current.getRootNode ? current.getRootNode() : null;
                current = root && root.host ? root.host : null;
            }

            return false;
        }

        function tryReorderOnHost(host, sourceData, targetData, targetIndex) {
            if (!host) {
                return false;
            }

            const candidates = [host, host.__data, host.data, host.__dataHost].filter(Boolean);
            for (let i = 0; i < candidates.length; i += 1) {
                const container = candidates[i];
                const keys = Object.keys(container);
                for (let keyIndex = 0; keyIndex < keys.length; keyIndex += 1) {
                    const key = keys[keyIndex];
                    const value = container[key];
                    if (!Array.isArray(value) || value.length < 2) {
                        continue;
                    }

                    const fromIndex = value.indexOf(sourceData);
                    if (fromIndex < 0) {
                        continue;
                    }

                    let destinationIndex = targetData ? value.indexOf(targetData) : targetIndex;
                    if (destinationIndex < 0) {
                        destinationIndex = Math.max(0, Math.min(targetIndex, value.length - 1));
                    }
                    if (destinationIndex === fromIndex) {
                        return true;
                    }

                    const moved = value.splice(fromIndex, 1)[0];
                    value.splice(destinationIndex, 0, moved);

                    if (typeof host.notifyPath === "function") {
                        try {
                            host.notifyPath(key, value);
                        } catch (_error) {
                        }
                    }
                    if (typeof host.requestUpdate === "function") {
                        try {
                            host.requestUpdate();
                        } catch (_error) {
                        }
                    }
                    if (typeof host.set === "function") {
                        try {
                            host.set(key, value.slice());
                        } catch (_error) {
                        }
                    }
                    return true;
                }
            }

            return false;
        }

        function reorderDomNodes(item, targetItem, targetIndex) {
            if (!item || !targetItem) {
                return false;
            }

            const parent = item.parentNode;
            if (!parent || parent !== targetItem.parentNode) {
                return false;
            }

            const items = Array.prototype.filter.call(parent.children, function (child) {
                return isQueueLikeItem(child);
            });
            const currentIndex = items.indexOf(item);
            if (currentIndex < 0) {
                return false;
            }

            const clampedTarget = Math.max(0, Math.min(targetIndex, items.length - 1));
            if (clampedTarget === currentIndex) {
                return true;
            }

            const referenceItem = items[clampedTarget];
            if (!referenceItem) {
                return false;
            }

            if (clampedTarget > currentIndex) {
                parent.insertBefore(item, referenceItem.nextSibling);
            } else {
                parent.insertBefore(item, referenceItem);
            }

            return true;
        }

        function triggerQueueItemMoveByIdentity(fingerprint, fromIndex, toIndex) {
            const item = resolveQueueItemByIdentity(fingerprint, fromIndex);
            const items = collectQueueItems();
            const currentIndex = items.indexOf(item);
            if (!item || currentIndex < 0) {
                return false;
            }

            const clampedTarget = Math.max(0, Math.min(toIndex, items.length - 1));
            if (clampedTarget === currentIndex) {
                return true;
            }

            const targetItem = items[clampedTarget];
            const reordered =
                reorderArrayInHosts(item, targetItem, clampedTarget) ||
                reorderDomNodes(item, targetItem, clampedTarget);

            if (!reordered) {
                return false;
            }

            window.setTimeout(publishQueueLayout, 80);
            window.setTimeout(publishQueueLayout, 240);
            return true;
        }

        window.__YTMusicProQueue = window.__YTMusicProQueue || {};
        window.__YTMusicProQueue.removeByIdentity = triggerQueueItemRemovalByIdentity;
        window.__YTMusicProQueue.moveByIdentity = triggerQueueItemMoveByIdentity;

        window.addEventListener("pointermove", handleInjectedQueuePointerMove, true);
        window.addEventListener("pointerup", function (event) {
            handleInjectedQueuePointerUp(event, false);
        }, true);
        window.addEventListener("pointercancel", function (event) {
            handleInjectedQueuePointerUp(event, true);
        }, true);
        window.addEventListener("touchmove", handleInjectedQueuePointerMove, { capture: true, passive: false });
        window.addEventListener("touchend", function (event) {
            handleInjectedQueuePointerUp(event, false);
        }, { capture: true, passive: false });
        window.addEventListener("touchcancel", function (event) {
            handleInjectedQueuePointerUp(event, true);
        }, { capture: true, passive: false });
        window.addEventListener("blur", function () {
            clearInjectedQueueDrag(true);
        }, true);
        document.addEventListener("visibilitychange", function () {
            if (document.hidden) {
                clearInjectedQueueDrag(true);
            }
        }, true);

        function scheduleMount() {
            if (mountTimer) {
                return;
            }

            mountTimer = window.setTimeout(function () {
                mountTimer = 0;
                ensureStyles();
                mountJamButtons();
                publishQueueLayout();
            }, 120);
        }

        [
            "yt-navigate-finish",
            "yt-page-data-updated",
            "pageshow",
            "popstate",
            "resize",
            "orientationchange",
            "scroll"
        ].forEach(function (eventName) {
            window.addEventListener(eventName, scheduleMount, true);
            document.addEventListener(eventName, scheduleMount, true);
        });

        ensureStyles();
        scheduleMount();
        window.setTimeout(scheduleMount, 800);
        window.setTimeout(scheduleMount, 2200);
        window.setInterval(scheduleMount, 1200);
    } catch (error) {
        console.error("YTMusic Pro UI script failed", error);
    }
})();
