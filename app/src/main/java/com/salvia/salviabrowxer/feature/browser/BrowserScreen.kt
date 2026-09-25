package com.salvia.salviabrowxer.feature.browser

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.bridge.BlobDownloadBridge
import com.salvia.salviabrowxer.ui.components.BrowserBottomBar
import com.salvia.salviabrowxer.ui.components.BrowserTopBar
import com.salvia.salviabrowxer.ui.components.FloatingDownloadButton
import com.salvia.salviabrowxer.ui.components.MediaQualitySelectionSheet
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.utils.WebViewClientWrapper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noMediaMessage = stringResource(R.string.no_media_detected)

    var webView: WebView? by remember { mutableStateOf(null) }
    var pageAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val initialUrl = remember { state.url }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }

    val blobBridge = remember(context) {
        BlobDownloadBridge(
            onBlobCaptured = { pageUrl, blobUrl, file, mime -> viewModel.onBlobCaptured(pageUrl, blobUrl, file, mime) },
            cacheDirProvider = { context.cacheDir }
        )
    }

    val isMediaDetected = state.detectedMedia.isNotEmpty()
    val mediaCount = state.detectedMedia.size
    val onUrlChange = remember(viewModel) { { input: String -> viewModel.onAddressInputChange(input) } }
    val onUrlSubmit = remember(viewModel) { { input: String -> viewModel.loadFromAddressBar(input) } }
    val onBackClick = remember(viewModel) { { viewModel.goBack() } }
    val onForwardClick = remember(viewModel) { { viewModel.goForward() } }
    val onRefreshClick = remember(viewModel) { { viewModel.reload() } }
    val onStopClick = remember(viewModel) { { viewModel.stopLoading() } }
    val onFabClick = remember(viewModel, isMediaDetected) {
        {
            if (isMediaDetected) {
                viewModel.openQualitySheet()
            } else {
                scope.launch { snackbarHostState.showSnackbar(noMediaMessage) }
            }
            Unit
        }
    }

    LaunchedEffect(Unit) {
        viewModel.commands.collectLatest { command ->
            webView?.let { view ->
                when (command) {
                    is BrowserCommand.Load -> if (command.url.isNotBlank()) view.loadUrl(command.url)
                    BrowserCommand.Back -> if (view.canGoBack()) view.goBack()
                    BrowserCommand.Forward -> if (view.canGoForward()) view.goForward()
                    BrowserCommand.Reload -> view.reload()
                    BrowserCommand.Stop -> view.stopLoading()
                    is BrowserCommand.FetchBlob -> {
                        val js = BLOB_FETCH_JS
                            .replace("__BLOB_URL__", command.blobUrl.replace("\\", "\\\\").replace("'", "\\'"))
                            .replace("__PAGE_URL__", command.pageUrl.replace("\\", "\\\\").replace("'", "\\'"))
                        view.evaluateJavascript(js, null)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrowserTopBar(
            url = state.url,
            isLoading = state.isLoading,
            isSecure = state.isSecure,
            canGoBack = state.canGoBack,
            canGoForward = state.canGoForward,
            progress = state.progress,
            onUrlChange = onUrlChange,
            onUrlSubmit = onUrlSubmit,
            onBackClick = onBackClick,
            onForwardClick = onForwardClick,
            onRefreshClick = onRefreshClick,
            onStopClick = onStopClick
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size -> pageAreaSize = size }
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = WebView.OVER_SCROLL_NEVER
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            allowFileAccess = false
                            allowContentAccess = false
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setGeolocationEnabled(false)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                            @Suppress("DEPRECATION")
                            setRenderPriority(WebSettings.RenderPriority.HIGH)
                        }
                        defaultUserAgent = settings.userAgentString

                        // InShot-style: expose blob bridge before any page loads
                        addJavascriptInterface(blobBridge, "SalviaBridge")

                        webViewClient = WebViewClientWrapper(
                            onPageStartedHook = { _, url, _ -> viewModel.onPageStarted(url) },
                            onPageFinishedHook = { view, url ->
                                val pageUrl = view.url ?: url ?: ""
                                viewModel.onPageFinished(pageUrl, view.title)
                                viewModel.updateNavigationState(
                                    canGoBack = view.canGoBack(),
                                    canGoForward = view.canGoForward()
                                )
                                view.evaluateJavascript(IN_SHOT_NETWORK_SNIFFER, null)
                                view.evaluateJavascript(
                                    MEDIA_DETECTION_JAVASCRIPT
                                ) { rawHtml ->
                                    if (pageUrl.isNotEmpty()) {
                                        decodeJavascriptString(rawHtml)
                                            ?.let { html -> viewModel.detectMediaInPage(pageUrl, html) }
                                    }
                                }
                            },
                            onMediaDetectedHook = { candidate -> viewModel.onMediaIntercepted(candidate) }
                        )

                        webChromeClient = object : WebChromeClient() {
                            private var lastProgress = 0
                            private var lastProgressTime = 0L
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                val now = System.currentTimeMillis()
                                if (newProgress == 100 || newProgress - lastProgress >= 2 || now - lastProgressTime >= 80) {
                                    lastProgress = newProgress
                                    lastProgressTime = now
                                    viewModel.onProgressChanged(newProgress)
                                }
                            }
                        }

                        if (initialUrl.isNotBlank()) {
                            loadUrl(initialUrl)
                        }
                    }
                },
                update = { view ->
                    webView = view
                    if (view.settings.javaScriptEnabled != state.isJavaScriptEnabled) {
                        view.settings.javaScriptEnabled = state.isJavaScriptEnabled
                    }
                    val cookiesEnabled = state.areCookiesEnabled
                    if (CookieManager.getInstance().acceptCookie() != cookiesEnabled) {
                        CookieManager.getInstance().setAcceptCookie(cookiesEnabled)
                    }
                    val agent = if (state.isDesktopSite) {
                        Constants.DESKTOP_USER_AGENT
                    } else {
                        defaultUserAgent ?: view.settings.userAgentString
                    }
                    if (view.settings.userAgentString != agent) {
                        view.settings.userAgentString = agent
                        if (view.url?.isNotBlank() == true) view.reload()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isMediaDetected) {
                Text(
                    text = stringResource(R.string.media_detected, mediaCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            FloatingDownloadButton(
                isMediaDetected = isMediaDetected,
                mediaCount = mediaCount,
                onClick = onFabClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                buttonSize = state.floatingButtonSize.dp,
                containerSize = pageAreaSize,
                initialOffset = Offset(state.fabPosition.x, state.fabPosition.y),
                onOffsetChanged = remember(viewModel) {
                    { offset: Offset -> viewModel.saveFabPosition(offset.x, offset.y) }
                }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )

            if (state.isLoading && state.progress < 10) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp),
                    color = AuroraTeal,
                    strokeWidth = 2.dp
                )
            }
        }

        BrowserBottomBar(
            onHomeClick = remember(viewModel) { { viewModel.goHome() } },
            onDownloadsClick = onNavigateToDownloads,
            onSettingsClick = onNavigateToSettings,
            activeDownloadCount = state.activeDownloadCount
        )
    }

    state.qualitySheet?.let { sheet ->
        MediaQualitySelectionSheet(
            mediaInfo = sheet.mediaInfo,
            isResolving = sheet.isResolving,
            onDismiss = remember(viewModel) { { viewModel.closeQualitySheet() } },
            onQualitySelected = remember(viewModel) { { format -> viewModel.handleFormatSelected(format) } }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface("SalviaBridge")
                webViewClient = android.webkit.WebViewClient()
                webChromeClient = null
                destroy()
            }
            webView = null
        }
    }
}

private fun decodeJavascriptString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val trimmed = raw.trim()
    if (!trimmed.startsWith("\"")) return trimmed
    return runCatching { org.json.JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        ?: trimmed.removeSurrounding("\"")
}

/** Fetches a blob: URL chunked so Binder never overflows — InShot style. */
private const val BLOB_FETCH_JS = """
    (function(){
        var blobUrl='__BLOB_URL__';
        var pageUrl='__PAGE_URL__';
        var CHUNK=480*1024;
        try{
            if(window.SalviaBridge) window.SalviaBridge.onBlobUrlFound(pageUrl, blobUrl);
            var xhr=new XMLHttpRequest();
            xhr.open('GET', blobUrl, true);
            xhr.responseType='blob';
            xhr.onload=function(){
                if(xhr.status!==200 && xhr.status!==0) return;
                var blob=xhr.response; if(!blob) return;
                var mime=blob.type || 'video/mp4';
                if(blob.size < 4*1024*1024){
                    var r=new FileReader();
                    r.onloadend=function(){
                        try{ if(window.SalviaBridge) window.SalviaBridge.onBlobData(pageUrl, blobUrl, r.result, mime); }catch(e){}
                    };
                    r.readAsDataURL(blob);
                    return;
                }
                var total=Math.ceil(blob.size/CHUNK);
                var idx=0;
                function next(){
                    if(idx>=total) return;
                    var slice=blob.slice(idx*CHUNK, (idx+1)*CHUNK);
                    var rr=new FileReader();
                    (function(cur){
                        rr.onloadend=function(){
                            try{
                                var b64=rr.result;
                                if(window.SalviaBridge) window.SalviaBridge.onBlobChunk(pageUrl, blobUrl, cur, total, b64, mime);
                            }catch(e){}
                            idx++; next();
                        };
                        rr.onerror=function(){ idx++; next(); };
                    })(idx);
                    rr.readAsDataURL(slice);
                }
                next();
            };
            xhr.onerror=function(){};
            xhr.send();
        }catch(e){}
    })();
"""

/**
 * InShot-style network sniffer: hooks XHR, fetch, video.src and blob URLs.
 * Also reports blob: URLs to SalviaBridge so they can be fetched on demand.
 */
private const val IN_SHOT_NETWORK_SNIFFER = """
    (function(){
        if(window.__salviaSnifferInstalled) return;
        window.__salviaSnifferInstalled = true;
        window.__salviaFound = window.__salviaFound || [];
        function isMediaUrl(u){
            if(!u) return false;
            if(u.indexOf('blob:')===0) return true;
            return /\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)/i.test(u) ||
                   /mime=video|mime=audio|video\/|audio\//i.test(u);
        }
        function pushUrl(u, src){
            if(!u || !isMediaUrl(u)) return;
            if(window.__salviaFound.indexOf(u)!==-1) return;
            window.__salviaFound.push(u);
            if(window.__salviaFound.length>40) window.__salviaFound.shift();
            if(u.indexOf('blob:')===0 && window.SalviaBridge){
                try{ window.SalviaBridge.onBlobUrlFound(location.href, u); }catch(e){}
            }
        }
        try{
            var origDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');
            Object.defineProperty(HTMLMediaElement.prototype,'src',{
                get: function(){ return origDescriptor.get.call(this); },
                set: function(v){ pushUrl(v,'media.src'); return origDescriptor.set.call(this,v); },
                configurable:true
            });
        }catch(e){}
        try{
            var openOrig = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method,url){
                this.__salviaUrl = url;
                return openOrig.apply(this, arguments);
            };
            var sendOrig = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function(){
                if(this.__salviaUrl) pushUrl(this.__salviaUrl,'xhr');
                return sendOrig.apply(this, arguments);
            };
        }catch(e){}
        try{
            var fetchOrig = window.fetch;
            window.fetch = function(input,init){
                var u = typeof input==='string'? input : (input && input.url);
                pushUrl(u,'fetch');
                return fetchOrig.apply(this, arguments);
            };
        }catch(e){}
        try{
            var obs = new MutationObserver(function(mutations){
                mutations.forEach(function(m){
                    m.addedNodes.forEach(function(n){
                        if(!n || !n.tagName) return;
                        var t=n.tagName.toLowerCase();
                        if(t==='video'||t==='audio'){
                            var s=n.getAttribute('src'); if(s) pushUrl(s,'mut');
                            n.querySelectorAll&&n.querySelectorAll('source').forEach(function(s2){ var u=s2.getAttribute('src'); if(u) pushUrl(u,'mut-src');});
                        } else if(t==='source'){
                            var su=n.getAttribute('src'); if(su) pushUrl(su,'mut-src');
                        } else if(t==='a'){
                            var hr=n.getAttribute('href'); if(hr) pushUrl(hr,'mut-a');
                        }
                    });
                });
            });
            obs.observe(document.documentElement,{childList:true,subtree:true});
        }catch(e){}
        setInterval(function(){
            try{
                document.querySelectorAll('video,audio').forEach(function(v){
                    var s=v.currentSrc||v.src; if(s) pushUrl(s,'poll');
                    if(v.src && v.src.indexOf('blob:')===0) pushUrl(v.src,'blob');
                });
            }catch(e){}
        },1500);
    })();
"""

private const val MEDIA_DETECTION_JAVASCRIPT = """
    (function () {
        if(window.__salviaFound && window.__salviaFound.length){
            var out='<html><body>';
            for(var i=0;i<window.__salviaFound.length;i++){
                var u=window.__salviaFound[i];
                var esc=u.replace(/&/g,'&amp;').replace(/"/g,'&quot;');
                out+='<a href="'+esc+'"></a>';
            }
            try{
                var nodes=document.querySelectorAll('video,audio,source,a[href]');
                var mediaPath=/\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)/i;
                for(var j=0;j<nodes.length&&out.length<58000;j++){
                    var n=nodes[j];
                    if(n.tagName==='A' && !mediaPath.test(n.getAttribute('href')||'')) continue;
                    var h=n.outerHTML;
                    if(out.length+h.length>62000) break;
                    out+=h;
                }
            }catch(e){}
            return out+'</body></html>';
        }
        var maxCharacters = 62000;
        var mediaPath = /\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)/i;
        var nodes = Array.prototype.slice.call(
            document.querySelectorAll('video, audio, source, a[href], [src]')
        );
        var html = '<html><body>';
        nodes.sort(function(a,b){
            var aP = (a.tagName==='VIDEO'||a.tagName==='AUDIO')?0:1;
            var bP = (b.tagName==='VIDEO'||b.tagName==='AUDIO')?0:1;
            return aP-bP;
        });
        for (var i = 0; i < nodes.length && html.length < maxCharacters; i++) {
            var node = nodes[i];
            if(node.tagName==='A' && !mediaPath.test(node.getAttribute('href')||'')) continue;
            var outer = node.outerHTML || '';
            if(!outer) continue;
            if(outer.length>8000) continue;
            if (html.length + outer.length > maxCharacters) break;
            html += outer;
        }
        return html + '</body></html>';
    })();
"""
