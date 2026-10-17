package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaUrlRules

/**
 * The JavaScript that watches how a page actually loads media, and the name it reports through.
 *
 * It replaces a hook set that could not work. The old script did intercept `XMLHttpRequest`,
 * `fetch` and `video.src`, but it only called back into Kotlin for `blob:` URLs — and that handler
 * did nothing but log. Everything else went into a page-side array which was later serialised as
 * `<a href="…">` and then discarded by the DOM detector, which keeps anchors only when the URL
 * carries a media extension. The one case worth having — media on an extension-less CDN path — was
 * therefore dropped twice, and the interception code around it was decoration.
 *
 * Two things make this version work:
 *
 *  - It reads the **response** `Content-Type` (`getResponseHeader` for XHR, `headers.get` for
 *    fetch, `addSourceBuffer` for MSE) and, when a server uses a generic binary MIME, its
 *    `Content-Disposition` filename if the browser exposes it to the page. These response headers
 *    identify extension-less URLs without guessing or making an extra request.
 *  - It reports over [BRIDGE_NAME] so a sighting becomes a candidate immediately, instead of
 *    travelling through serialised HTML on the off-chance a regex likes it.
 *
 * The filter here is only a traffic guard, so the bridge is not called for every asset in the
 * document. [com.salvia.salviabrowxer.core.model.MediaSniffAdmission] on the Kotlin side is the
 * authority and re-decides everything it receives.
 *
 * The extension lists are interpolated from [MediaUrlRules] rather than written out again here, so
 * the two sides cannot disagree about what media looks like.
 *
 * Not covered, deliberately: worker and service-worker scopes. They have their own `fetch` and the
 * script only runs in the window, so a player that fetches from a worker stays invisible. Hooking
 * workers means injecting into them too, which needs a second mechanism for a rarer case.
 */
object MediaSniffer {

    /** The `JavascriptInterface` name the page reports on. Registered by `TabWebViewStore`. */
    const val BRIDGE_NAME: String = "SalviaMedia"

    /**
     * The `WebViewCompat.addWebMessageListener` object the recording hook posts to.
     *
     * Its whole reason to exist is the payload type: a segment is handed over as an `ArrayBuffer`,
     * which crosses the bridge as bytes. The `JavascriptInterface` path can only take strings, and
     * base64 would add a third more memory per segment — on a 4 MB segment that is a copy the phone
     * cannot afford every few seconds, on top of the copy the page already holds.
     */
    const val MSE_PORT_NAME: String = "SalviaMsePort"

    val script: String = SNIFFER_TEMPLATE
        .replace("__MSE_PORT__", MSE_PORT_NAME)
        .replace("__PLAYLIST_MIME_ARRAY__", MediaUrlRules.PLAYLIST_MIME_JS_ARRAY)
        .replace("__DASH_EXTENSION__", MediaUrlRules.DASH_EXTENSION)
        .replace("__MEDIA_EXT__", MediaUrlRules.EXTENSION_ALTERNATION)
        .replace("__NON_MEDIA_EXT__", MediaUrlRules.NON_MEDIA_ALTERNATION)
        .replace("__SEGMENT_EXT__", MediaUrlRules.SEGMENT_ALTERNATION)

    private const val SNIFFER_TEMPLATE = """
(function () {
    if (window.__salviaMediaHook) return;
    window.__salviaMediaHook = true;

    var MEDIA_EXT = /\.(__MEDIA_EXT__)(\?|#|$)/i;
    var NON_MEDIA_EXT = /\.(__NON_MEDIA_EXT__)(\?|#|$)/i;
    var SEGMENT_EXT = /\.(__SEGMENT_EXT__)(\?|#|$)/i;
    var SEGMENT_MIME = /^video\/mp2t/i;
    var PLAYLIST_MIME = __PLAYLIST_MIME_ARRAY__;
    var DASH_FILENAME_EXT = /\.(__DASH_EXTENSION__)(?=$|[?#";\s])/i;
    var LIMIT = 60;

    // A response can arrive before the element is visible. Let stronger player evidence upgrade
    // that URL later instead of discarding it forever as a duplicate.
    var reported = {};
    var focused = '';
    var lastFocusAt = 0;
    var lastPage = location.href;
    var mediaObjects = Object.create(null);
    var objectOrder = [];
    var sourceObjects = new WeakMap();
    // MIME per SourceBuffer (not per MediaSource): the recording hook only ever sees the buffer.
    var sourceBuffers = new WeakMap();

    function resetForPage() {
        if (lastPage === location.href) return;
        lastPage = location.href;
        reported = {};
        order.length = 0;
        focused = '';
        objectOrder.forEach(function(u) { if (mediaObjects[u]) mediaObjects[u].sent = false; });
    }
    var order = window.__salviaFound;
    if (!order || !order.length) { order = []; window.__salviaFound = order; }

    function absolute(u) {
        if (!u) return '';
        try { return new URL(u, location.href).href; } catch (e) { return ''; }
    }

    function isMediaMime(m) {
        if (!m || SEGMENT_MIME.test(m)) return false;
        return m.indexOf('video/') === 0 || m.indexOf('audio/') === 0 ||
               PLAYLIST_MIME.indexOf(m) !== -1 || m === 'application/dash+xml';
    }

    // A response filename is a second server-provided type hint for extension-less binary URLs.
    // It is only a prefilter; Kotlin validates the MIME, filename and fragment exclusions again.
    function responseFilenameExtension(header) {
        if (!header || typeof header !== 'string' || header.length > 2048) return '';
        var match = /(?:^|;)\s*filename\*\s*=\s*(?:"([^"]*)"|'([^']*)'|([^;]*))/i.exec(header);
        if (!match) match = /(?:^|;)\s*filename\s*=\s*(?:"([^"]*)"|'([^']*)'|([^;]*))/i.exec(header);
        if (!match) return '';
        var name = (match[1] || match[2] || match[3] || '').trim();
        if (!name) return '';
        var firstQuote = name.indexOf("'");
        var secondQuote = firstQuote >= 0 ? name.indexOf("'", firstQuote + 1) : -1;
        if (secondQuote >= 0) name = name.substring(secondQuote + 1);
        try { name = decodeURIComponent(name.replace(/\+/g, '%2B')); } catch (e) {}
        name = name.replace(/\\/g, '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        var extension = MEDIA_EXT.exec(name);
        if (extension && !SEGMENT_EXT.test(name)) return extension[1].toLowerCase();
        var dash = DASH_FILENAME_EXT.exec(name);
        return dash ? dash[1].toLowerCase() : '';
    }

    function tell(u, mime, origin, kind, contentDisposition) {
        var b = window.SalviaMedia;
        if (!b || !b.onMediaUrlFound) return;
        try { b.onMediaUrlFound(location.href, u, mime, origin, kind || '', contentDisposition || ''); } catch (e) {}
    }

    // A range that starts at zero and has no end asks for the whole file. Anything else is a piece
    // of it — and a piece is what a player streams, never what the user wants saved.
    function isPartialRange(value) {
        if (!value) return false;
        var v = String(value).toLowerCase().replace(/\s/g, '');
        if (v.indexOf('bytes=') !== 0) return true;
        if (v.indexOf(',') !== -1) return true;
        return v !== 'bytes=0-';
    }

    // Prefilter only: the Kotlin side decides. Called for anything the page fetched, including
    // HLS fragments, so the checks that keep fragments out of the tray live here too.
    function report(rawUrl, rawMime, origin, ranged, kind, rawContentDisposition) {
        try {
            resetForPage();
            var contentDisposition = typeof rawContentDisposition === 'string' && rawContentDisposition.length <= 2048
                ? rawContentDisposition : '';
            var dispositionExtension = (origin === 'xhr' || origin === 'fetch')
                ? responseFilenameExtension(contentDisposition) : '';
            var u = absolute(rawUrl);
            if (u.length < 12 || u.length > 8192 || location.href.length > 4096) return;
            var blob = u.indexOf('blob:') === 0;
            if (!blob && u.indexOf('http') !== 0) return;

            var mime = (rawMime || '').split(';')[0].trim().toLowerCase();
            var strong = isMediaMime(mime);
            var meta = origin === 'element-metadata';

            if (blob) {
                var objectInfo = mediaObjects[u];
                if (!strong && objectInfo) { mime = objectInfo.mime; strong = isMediaMime(mime); }
                if (!strong) return;
            } else {
                if (NON_MEDIA_EXT.test(u)) return;
                if (SEGMENT_EXT.test(u)) return;
                if (ranged || /[?&](bytestart|byteend)=/i.test(u)) return;
                if (!strong && !meta && !MEDIA_EXT.test(u) && !dispositionExtension) return;
            }

            if (blob && mediaObjects[u]) {
                var obj = mediaObjects[u], bridge = window.SalviaMedia;
                if (bridge && bridge.onMediaObjectUrl && !obj.sent) {
                    obj.sent = true;
                    try { bridge.onMediaObjectUrl(location.href, u, mime, obj.kind); } catch(e) {}
                }
            }
            var strength = meta ? 3 : (strong ? 2 : 1);
            var previous = reported[u];
            if (previous && previous.strength >= strength && (!mime || previous.mime === mime)) return;
            if (!reported[u]) {
                order.push(u);
                if (order.length > LIMIT) { delete reported[order.shift()]; }
            }
            reported[u] = {
                strength: strength, mime: mime, origin: origin, kind: kind,
                contentDisposition: contentDisposition
            };
            tell(u, mime, origin, kind, contentDisposition);
        } catch (e) {}
    }

    // 1. XHR. The response header is the server's own answer, and the request's Range header says
    //    whether the page asked for a file or for a piece of one.
    try {
        var xhrOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
            try { this.__salviaUrl = url; this.__salviaRange = null; this.__salviaPage = location.href; } catch (e) {}
            return xhrOpen.apply(this, arguments);
        };
        var xhrHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
            try { if (String(name).toLowerCase() === 'range') this.__salviaRange = value; } catch (e) {}
            return xhrHeader.apply(this, arguments);
        };
        var xhrSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function () {
            try {
                var self = this;
                self.addEventListener('loadend', function () {
                    if (self.__salviaPage !== location.href) return;
                    var mime = '', contentDisposition = '';
                    try { mime = self.getResponseHeader('Content-Type') || ''; } catch (e) {}
                    try { contentDisposition = self.getResponseHeader('Content-Disposition') || ''; } catch (e) {}
                    if (!mime) { try { if (self.response && self.response.type) mime = self.response.type; } catch (e) {} }
                    report(self.responseURL || self.__salviaUrl, mime, 'xhr', self.status === 206 || isPartialRange(self.__salviaRange), '', contentDisposition);
                });
            } catch (e) {}
            return xhrSend.apply(this, arguments);
        };
    } catch (e) {}

    // 2. fetch. Headers only — cloning the response would tee the body and double the memory of
    //    every large video the page streams. Our own chain swallows its rejection so the page sees
    //    exactly the promise it would have seen without us.
    try {
        var fetchOrig = window.fetch;
        if (fetchOrig) {
            window.fetch = function (input, init) {
                var url = (typeof input === 'string') ? input : (input && input.url);
                var rangeValue = null;
                try {
                    var h = (init && init.headers !== undefined) ? init.headers : (input && input.headers);
                    if (h) rangeValue = new Headers(h).get('range');
                } catch (e) {}
                var requestPage = location.href;
                var p = fetchOrig.apply(this, arguments);
                try {
                    p.then(function (res) {
                        try {
                            if (requestPage !== location.href) return;
                            var mime = '', contentDisposition = '';
                            if (res && res.headers && res.headers.get) {
                                mime = res.headers.get('content-type') || '';
                                contentDisposition = res.headers.get('content-disposition') || '';
                            }
                            report((res && res.url) || url, mime, 'fetch', (res && res.status === 206) || isPartialRange(rangeValue), '', contentDisposition);
                        } catch (e) {}
                    }).catch(function () {});
                } catch (e) {}
                return p;
            };
        }
    } catch (e) {}

    // 3. Object URL provenance. A MediaSource is NOT a fetchable Blob, even with video/mp4
    // SourceBuffers. Keep type evidence per object, never in a page-wide "last MIME" variable.
    function rememberObject(url, kind, mime) {
        mediaObjects[url] = {kind: kind, mime: mime || '', sent: false};
        objectOrder.push(url);
        if (objectOrder.length > LIMIT) delete mediaObjects[objectOrder.shift()];
    }
    try {
        var createOrig = window.URL && window.URL.createObjectURL;
        if (createOrig) {
            window.URL.createObjectURL = function (obj) {
                var url = createOrig.apply(this, arguments);
                try {
                    if (window.MediaSource && obj instanceof window.MediaSource) {
                        var info = sourceObjects.get(obj) || {urls: [], mime: ''};
                        info.urls.push(url);
                        if (info.urls.length > LIMIT) info.urls.shift();
                        sourceObjects.set(obj, info);
                        rememberObject(url, 'mse', info.mime);
                        if (info.mime) report(url, info.mime, 'element-metadata', false, '');
                    } else if (window.Blob && obj instanceof window.Blob) {
                        rememberObject(url, 'file', obj.type);
                        // Wait until a player uses it; not every generated Blob is a download.
                    }
                } catch (e) {}
                return url;
            };
        }
        var revokeOrig = window.URL && window.URL.revokeObjectURL;
        if (revokeOrig) {
            window.URL.revokeObjectURL = function (url) {
                var result = revokeOrig.apply(this, arguments);
                delete mediaObjects[url];
                delete reported[url];
                var bridge = window.SalviaMedia;
                if (bridge && bridge.onMediaUrlExpired) {
                    try { bridge.onMediaUrlExpired(location.href, url); } catch(e) {}
                }
                var index = objectOrder.indexOf(url);
                if (index >= 0) objectOrder.splice(index, 1);
                return result;
            };
        }
        var msProto = window.MediaSource && window.MediaSource.prototype;
        if (msProto && msProto.addSourceBuffer) {
            var addOrig = msProto.addSourceBuffer;
            msProto.addSourceBuffer = function (mime) {
                var result = addOrig.apply(this, arguments); // do not report a rejected codec
                try {
                    var clean = (mime || '').split(';')[0].trim().toLowerCase();
                    try { sourceBuffers.set(result, clean); } catch (e) {}
                    var info = sourceObjects.get(this) || {urls: [], mime: ''};
                    // Prefer video when separate audio/video SourceBuffers share one MediaSource.
                    if (!info.mime || clean.indexOf('video/') === 0) info.mime = clean;
                    sourceObjects.set(this, info);
                    info.urls.forEach(function(url) {
                        if (mediaObjects[url]) {
                            mediaObjects[url].mime = info.mime;
                            report(url, info.mime, 'element-metadata', false, '');
                        }
                    });
                } catch (e) {}
                return result;
            };
        }
    } catch (e) {}

    // 4. Media elements. A plain <video src> on an extension-less CDN path is invisible to every
    //    other layer, but the element itself answers the question: once it reports loaded metadata
    //    the URL is playable media, whatever the path looks like.
    //    Markup can also declare the type (`<source type="video/mp4">`, a data-* player attribute).
    //    That is the same evidence the DOM scanner trusts, so it is carried across the bridge
    //    instead of leaving an extension-less source URL detectable by one layer and dropped here.
    function declaredType(n) {
        try {
            if (!n || !n.getAttribute) return '';
            var t = n.getAttribute('type');
            if (!t) return '';
            t = String(t).split(';')[0].trim().toLowerCase();
            return isMediaMime(t) ? t : '';
        } catch (e) { return ''; }
    }

    function scanElement(n) {
        try {
            var player = n.tagName === 'VIDEO' || n.tagName === 'AUDIO';
            var url = null;
            if (player) {
                url = n.currentSrc || n.src || n.getAttribute('src') || n.getAttribute('data-src');
            } else if (n.getAttribute) {
                url = n.getAttribute('src') || n.getAttribute('data-src');
            }
            if (!url) return;

            var owner = player ? n : (n.parentNode && (n.parentNode.tagName === 'VIDEO' || n.parentNode.tagName === 'AUDIO') ? n.parentNode : null);
            var kind = owner ? owner.tagName.toLowerCase() : (player ? n.tagName.toLowerCase() : '');
            var loaded = false;
            try {
                if (owner) loaded = owner.readyState > 0 && absolute(owner.currentSrc) === absolute(url);
            } catch (e) {}
            var type = declaredType(n) || declaredType(owner);
            report(url, type, loaded ? 'element-metadata' : 'element-source', false, kind);
        } catch (e) {}
    }

    // An *observed* source in the visible player is more relevant than a background fetch. This
    // never admits media or starts a download: Kotlin only uses it if the URL was already admitted.
    // No site-specific selectors, no forced playback, no network requests.
    function focusVisiblePlayer() {
        try {
            resetForPage();
            if (window !== window.top) return;
            if (document.visibilityState === 'hidden') {
                focused = '';
                var hiddenBridge = window.SalviaMedia;
                if (hiddenBridge && hiddenBridge.onVisibleMedia) hiddenBridge.onVisibleMedia(location.href, '');
                return;
            }
            var nodes = document.querySelectorAll('video,audio');
            var best = null, bestArea = 0;
            for (var i = 0; i < nodes.length; i++) {
                var n = nodes[i], u = absolute(n.currentSrc || n.src || '');
                if (!u || !reported[u] || !n.getBoundingClientRect) continue;
                var style = window.getComputedStyle(n);
                if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) === 0) continue;
                var r = n.getBoundingClientRect();
                var w = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0));
                var h = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
                var area = w * h;
                if (area < 48 * 48) continue;
                // Playback breaks ties; area is only a viewport relevance signal, not proof that
                // this is the main video (a pre-roll can occupy the same element).
                var score = area * (n.paused ? 1 : 1.25);
                if (score > bestArea) { best = u; bestArea = score; }
            }
            if (best && (best !== focused || Date.now() - lastFocusAt > 1500)) {
                focused = best;
                lastFocusAt = Date.now();
                var b = window.SalviaMedia;
                // The native bounded feed index may have evicted this old URL. Re-announce its
                // stored evidence on focus so scrolling back works without reloading the page.
                var evidence = reported[best], objectInfo = mediaObjects[best];
                if (objectInfo && b && b.onMediaObjectUrl) {
                    b.onMediaObjectUrl(location.href, best, objectInfo.mime, objectInfo.kind);
                }
                tell(best, evidence.mime, evidence.origin, evidence.kind, evidence.contentDisposition);
                if (b && b.onVisibleMedia) b.onVisibleMedia(location.href, best);
            } else if (!best && focused) {
                focused = '';
                var bridge = window.SalviaMedia;
                if (bridge && bridge.onVisibleMedia) bridge.onVisibleMedia(location.href, '');
            }
        } catch (e) {}
    }

    // 5. MSE recording — off until the user asks for it, and honest about what it can produce.
    //
    //    Nothing here changes what the page sees: the page's own appendBuffer runs first, always, and
    //    the bytes we keep are a copy it cannot observe. Playback is never delayed by our work, and
    //    no failure in here may reach the player (every branch ends in a catch).
    //
    //    The recording is a single stream, chosen by the hint the app armed it with: the video stream
    //    for a video, the audio one for audio-only playback. A MediaSource that feeds separate audio
    //    and video buffers would otherwise interleave two streams into a file no player can open
    //    without a real muxer, and this app has none.
    var recording = {armed: false, kind: 'video', mime: '', opened: false, bytes: 0};
    var RECORD_LIMIT_BYTES = 512 * 1024 * 1024;

    function msePort() {
        try { return window.__MSE_PORT__ || null; } catch (e) { return null; }
    }

    // Every frame announces itself once, through the same port a recording uses — the only channel
    // where the app also learns the origin WebKit attributes to the frame, which is the one thing a
    // page cannot forge. With the frames of a document known, a sighting can be attributed to a frame
    // that is really there instead of being taken on a page's word. Nothing is sent when the WebView
    // has no port to send on, so an older WebView keeps working exactly as it did.
    try {
        if (!window.__salviaFrameAnnounced) {
            window.__salviaFrameAnnounced = true;
            var announcePort = msePort();
            if (announcePort) {
                announcePort.postMessage(JSON.stringify({
                    t: 'frame',
                    u: location.href,
                    m: window.top === window
                }));
            }
        }
    } catch (e) {}

    // Encrypted Media is where recording stops. This is a refusal, not a limitation we work around:
    // the bytes of an encrypted stream do not play back, so keeping them would produce a file nobody
    // can open. The app says so instead of writing it.
    function refuseEncrypted() {
        recording.armed = false;
        recording.opened = false;
        try {
            var b = window.SalviaMedia;
            if (b && b.onMseEncrypted) b.onMseEncrypted(location.href, recording.mime);
        } catch (e) {}
    }

    function hasMediaKeys() {
        try {
            if (!document.querySelectorAll) return false;
            var nodes = document.querySelectorAll('video,audio');
            for (var i = 0; i < nodes.length; i++) {
                if (nodes[i] && nodes[i].mediaKeys) return true;
            }
        } catch (e) {}
        return false;
    }

    // Called by the app when the user asks to record the stream that is playing in this tab.
    // Returns false when this WebView cannot do it (no message port) or the page is protected.
    window.__salviaMseArm = function (kind) {
        try {
            if (!window.SourceBuffer || !window.SourceBuffer.prototype.appendBuffer) return false;
            if (!msePort()) return false;
            recording.kind = (kind === 'audio') ? 'audio' : 'video';
            recording.mime = '';
            recording.opened = false;
            recording.bytes = 0;
            if (hasMediaKeys()) { refuseEncrypted(); return false; }
            recording.armed = true;
            return true;
        } catch (e) { return false; }
    };

    window.__salviaMseDisarm = function () {
        try { recording.armed = false; recording.opened = false; } catch (e) {}
    };

    // What the app asks for when the user stops: what was captured, and whether it is still running.
    window.__salviaMseState = function () {
        try { return {armed: recording.armed, bytes: recording.bytes, mime: recording.mime}; }
        catch (e) { return {armed: false, bytes: 0, mime: ''}; }
    };

    try {
        document.addEventListener('encrypted', function () {
            if (recording.armed) refuseEncrypted();
        }, true);
    } catch (e) {}

    try {
        if (document.addEventListener) {
            // A key system can be attached to the element rather than signalled by an event.
            document.addEventListener('play', function () {
                if (recording.armed && hasMediaKeys()) refuseEncrypted();
            }, true);
        }
    } catch (e) {}

    try {
        var sbAppendProto = window.SourceBuffer && window.SourceBuffer.prototype;
        if (sbAppendProto && sbAppendProto.appendBuffer) {
            var appendOrig = sbAppendProto.appendBuffer;
            sbAppendProto.appendBuffer = function (data) {
                var result = appendOrig.apply(this, arguments);
                try {
                    if (!recording.armed || !data || !data.byteLength) return result;
                    var mime = sourceBuffers.get(this) || '';
                    var isAudio = mime.indexOf('audio/') === 0;
                    if (isAudio !== (recording.kind === 'audio')) return result;
                    var port = msePort();
                    if (!port) return result;
                    if (!recording.opened) {
                        recording.opened = true;
                        recording.mime = mime;
                        port.postMessage(JSON.stringify({t: 'open', mime: mime}));
                    }
                    if (recording.bytes + data.byteLength > RECORD_LIMIT_BYTES) {
                        recording.armed = false;
                        port.postMessage(JSON.stringify({t: 'cap', bytes: recording.bytes}));
                        return result;
                    }
                    var copy = data.slice ? data.slice(0)
                        : (data.buffer ? data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) : null);
                    if (!copy) return result;
                    recording.bytes += copy.byteLength;
                    port.postMessage(copy);
                } catch (e) {}
                return result;
            };
        }
    } catch (e) {}

    function scan() {
        try {
            var nodes = document.querySelectorAll('video,audio,source');
            for (var i = 0; i < nodes.length; i++) scanElement(nodes[i]);
            focusVisiblePlayer();
        } catch (e) {}
    }

    try {
        document.addEventListener('loadedmetadata', function (ev) {
            if (ev && ev.target) scanElement(ev.target);
            focusVisiblePlayer();
        }, true);
        document.addEventListener('play', function(ev) {
            if (ev && ev.target) scanElement(ev.target);
            focusVisiblePlayer();
        }, true);
        document.addEventListener('emptied', scan, true);
        document.addEventListener('scroll', scheduleScan, {passive: true, capture: true});
        document.addEventListener('visibilitychange', scan, true);
        window.addEventListener('resize', scheduleScan);
        document.addEventListener('DOMContentLoaded', scan, true);
        window.addEventListener('popstate', scheduleScan);
        window.addEventListener('pageshow', scheduleScan);
        window.addEventListener('load', scan, true);
    } catch (e) {}

    // Batch bursts from virtualized feeds and scrolling; no layout pass per mutation/event.
    var scanPending = false;
    function scheduleScan() {
        if (scanPending) return;
        scanPending = true;
        setTimeout(function() { scanPending = false; scan(); }, 120);
    }
    try {
        var obs = new MutationObserver(scheduleScan);
        obs.observe(document.documentElement || document, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src', 'data-src', 'type']
        });
    } catch (e) {}

    // Native history callbacks can request a batched refresh after pushState/replaceState.
    window.__salviaScan = scheduleScan;

    // Last resort for a player that swaps currentSrc without firing anything useful. Bounded by the
    // dedupe set and the report cap, so a page with no media costs one querySelectorAll every 2s.
    setInterval(scan, 2000);
    scan();
})();
"""
}
