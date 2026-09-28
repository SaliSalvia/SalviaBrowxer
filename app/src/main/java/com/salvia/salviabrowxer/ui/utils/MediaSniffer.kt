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
 *    fetch, `addSourceBuffer` for MSE) instead of guessing from the URL. That is the server stating
 *    what the bytes are, it needs no extra request, and it exists for extension-less URLs.
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

    val script: String = SNIFFER_TEMPLATE
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
    var LIMIT = 60;

    var reported = {};
    var order = window.__salviaFound;
    if (!order || !order.length) { order = []; window.__salviaFound = order; }

    function absolute(u) {
        try { return new URL(u, location.href).href; } catch (e) { return ''; }
    }

    function isMediaMime(m) {
        if (!m || SEGMENT_MIME.test(m)) return false;
        return m.indexOf('video/') === 0 || m.indexOf('audio/') === 0 ||
               m === 'application/vnd.apple.mpegurl' || m === 'application/x-mpegurl' ||
               m === 'application/dash+xml';
    }

    function tell(u, mime, origin, kind) {
        var b = window.SalviaMedia;
        if (!b || !b.onMediaUrlFound) return;
        try { b.onMediaUrlFound(location.href, u, mime, origin, kind || ''); } catch (e) {}
    }

    // A range that starts at zero and has no end asks for the whole file. Anything else is a piece
    // of it — and a piece is what a player streams, never what the user wants saved.
    function isPartialRange(value) {
        if (!value) return false;
        var v = String(value).toLowerCase().replace(/\s/g, '');
        if (v.indexOf('bytes=') !== 0) return true;
        var first = v.substring(6).split(',')[0];
        var dash = first.indexOf('-');
        if (dash <= 0) return true;
        return !(dash === first.length - 1 && first.charAt(0) === '0');
    }

    // Prefilter only: the Kotlin side decides. Called for anything the page fetched, including
    // HLS fragments, so the checks that keep fragments out of the tray live here too.
    function report(rawUrl, rawMime, origin, ranged, kind) {
        try {
            var u = absolute(rawUrl);
            if (u.length < 12) return;
            var blob = u.indexOf('blob:') === 0;
            if (!blob && u.indexOf('http') !== 0) return;

            var mime = (rawMime || '').split(';')[0].trim().toLowerCase();
            var strong = isMediaMime(mime);
            var meta = origin === 'element-metadata';

            if (blob) {
                if (!strong && window.__salviaMseMime) { mime = window.__salviaMseMime; strong = isMediaMime(mime); }
                if (!strong) return;
            } else {
                if (NON_MEDIA_EXT.test(u)) return;
                if (SEGMENT_EXT.test(u)) return;
                if (ranged) return;
                if (!strong && !meta && !MEDIA_EXT.test(u)) return;
            }

            if (reported[u]) return;
            reported[u] = true;
            order.push(u);
            if (order.length > LIMIT) order.shift();
            tell(u, mime, origin, kind);
        } catch (e) {}
    }

    // 1. XHR. The response header is the server's own answer, and the request's Range header says
    //    whether the page asked for a file or for a piece of one.
    try {
        var xhrOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
            try { this.__salviaUrl = url; this.__salviaRange = null; } catch (e) {}
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
                    var mime = '';
                    try { mime = self.getResponseHeader('Content-Type') || ''; } catch (e) {}
                    if (!mime) { try { if (self.response && self.response.type) mime = self.response.type; } catch (e) {} }
                    report(self.__salviaUrl, mime, 'xhr', isPartialRange(self.__salviaRange), '');
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
                    var h = init && init.headers;
                    if (h) {
                        if (typeof h.get === 'function') rangeValue = h.get('range');
                        else for (var k in h) { if (String(k).toLowerCase() === 'range') rangeValue = h[k]; }
                    }
                } catch (e) {}
                var p = fetchOrig.apply(this, arguments);
                try {
                    p.then(function (res) {
                        try {
                            var mime = '';
                            if (res && res.headers && res.headers.get) mime = res.headers.get('content-type') || '';
                            report(url, mime, 'fetch', isPartialRange(rangeValue), '');
                        } catch (e) {}
                    }).catch(function () {});
                } catch (e) {}
                return p;
            };
        }
    } catch (e) {}

    // 3. MediaSource. A player that feeds encoded data to a SourceBuffer never exposes a file, and
    //    the type it declares is the only thing that says what the blob URL actually contains.
    try {
        var createOrig = window.URL && window.URL.createObjectURL;
        var blobSources = [];
        if (createOrig) {
            window.URL.createObjectURL = function (obj) {
                var url = createOrig.apply(this, arguments);
                try {
                    if (window.MediaSource && obj instanceof window.MediaSource) {
                        blobSources.push({ src: obj, url: url });
                        if (blobSources.length > 4) blobSources.shift();
                    }
                } catch (e) {}
                return url;
            };
        }
        var msProto = window.MediaSource && window.MediaSource.prototype;
        if (msProto && msProto.addSourceBuffer) {
            var addOrig = msProto.addSourceBuffer;
            msProto.addSourceBuffer = function (mime) {
                try {
                    var clean = (mime || '').split(';')[0].trim().toLowerCase();
                    if (clean) window.__salviaMseMime = clean;
                    for (var i = 0; i < blobSources.length; i++) {
                        if (blobSources[i].src === this) {
                            report(blobSources[i].url, clean, 'element-metadata', false, '');
                            break;
                        }
                    }
                } catch (e) {}
                return addOrig.apply(this, arguments);
            };
        }
    } catch (e) {}

    // 4. Media elements. A plain <video src> on an extension-less CDN path is invisible to every
    //    other layer, but the element itself answers the question: once it reports loaded metadata
    //    the URL is playable media, whatever the path looks like.
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
                if (owner) loaded = owner.readyState > 0 || owner.duration > 0 || owner.videoWidth > 0;
            } catch (e) {}
            report(url, '', loaded ? 'element-metadata' : 'element-source', false, kind);
        } catch (e) {}
    }

    function scan() {
        try {
            var nodes = document.querySelectorAll('video,audio,source');
            for (var i = 0; i < nodes.length; i++) scanElement(nodes[i]);
        } catch (e) {}
    }

    try {
        document.addEventListener('loadedmetadata', function (ev) {
            if (ev && ev.target) scanElement(ev.target);
        }, true);
        document.addEventListener('DOMContentLoaded', scan, true);
        window.addEventListener('load', scan, true);
    } catch (e) {}

    try {
        var obs = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                    var n = added[j];
                    if (!n || !n.tagName) continue;
                    var t = n.tagName.toLowerCase();
                    if (t === 'video' || t === 'audio' || t === 'source') scanElement(n);
                }
            }
        });
        obs.observe(document.documentElement || document, { childList: true, subtree: true });
    } catch (e) {}

    // Last resort for a player that swaps currentSrc without firing anything useful. Bounded by the
    // dedupe set and the report cap, so a page with no media costs one querySelectorAll every 2s.
    setInterval(scan, 2000);
})();
"""
}
