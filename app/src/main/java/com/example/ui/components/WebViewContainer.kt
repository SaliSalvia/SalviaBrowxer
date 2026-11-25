package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.GeolocationPermissions
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.BrowserSettings
import com.example.engine.AdBlockerEngine
import com.example.engine.WebViewConfigurator
import com.example.engine.WebPermissionPolicy
import com.example.engine.SitePermissionStore
import com.example.R
import com.example.ui.BrowserTab
import com.example.ui.BrowserViewModel
import com.example.ui.i18n.LocalAppStrings

private data class PendingSslWarning(
    val url: String,
    val handler: SslErrorHandler,
)

private data class PendingWebPermission(
    val tabId: String,
    val origin: String,
    val description: String,
    val androidPermissions: List<String>,
    val resources: List<String> = emptyList(),
    val permissionRequest: PermissionRequest? = null,
    val onGrant: (Set<String>) -> Unit,
    val onDeny: () -> Unit,
)

@Composable
fun WebViewContainer(
    tab: BrowserTab,
    settings: BrowserSettings,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var sslWarning by remember { mutableStateOf<PendingSslWarning?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var popupTitle by remember { mutableStateOf(s.sslPopupDefaultTitle) }
    var pendingWebPermission by remember(tab.id) { mutableStateOf<PendingWebPermission?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val pending = pendingWebPermission ?: return@rememberLauncherForActivityResult
        val currentUrl = viewModel.getWebView(pending.tabId)?.url.orEmpty()
        val originStillMatches = WebPermissionPolicy.isSameSecureOrigin(pending.origin, currentUrl)
        if (!originStillMatches) {
            pending.onDeny()
            pendingWebPermission = null
            return@rememberLauncherForActivityResult
        }

        val grantedPermissions = pending.androidPermissions.filter { permission ->
            results[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }.toSet()

        if (pending.resources.isNotEmpty()) {
            val grantedResources = pending.resources.filter { resource ->
                val required = WebPermissionPolicy.androidPermissionsForCapture(listOf(resource))
                required != null && required.all(grantedPermissions::contains)
            }.toSet()
            if (grantedResources.isNotEmpty()) pending.onGrant(grantedResources) else pending.onDeny()
        } else {
            val locationGranted =
                Manifest.permission.ACCESS_FINE_LOCATION in grantedPermissions ||
                    Manifest.permission.ACCESS_COARSE_LOCATION in grantedPermissions
            if (locationGranted) pending.onGrant(emptySet()) else pending.onDeny()
        }
        pendingWebPermission = null
    }

    val showPermissionPrompt: (PendingWebPermission) -> Unit = { next ->
        pendingWebPermission?.onDeny?.invoke()
        pendingWebPermission = next
    }

    val dismissPopup: () -> Unit = {
        popupWebView?.let { popup ->
            runCatching {
                popup.onPause()
                popup.stopLoading()
                popup.loadUrl("about:blank")
                popup.removeAllViews()
                popup.destroy()
            }
        }
        popupWebView = null
    }

    DisposableEffect(tab.id) {
        onDispose {
            dismissPopup()
            pendingWebPermission?.onDeny?.invoke()
            pendingWebPermission = null
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        pendingFileCallback?.onReceiveValue(uris.toTypedArray())
        pendingFileCallback = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { container ->
                val targetWebView = viewModel.getWebView(tab.id) ?: createConfiguredWebView(
                    context = context,
                    tab = tab,
                    settings = settings,
                    viewModel = viewModel,
                    onShowFileChooser = { callback ->
                        pendingFileCallback?.onReceiveValue(null)
                        pendingFileCallback = callback
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onSslWarning = { url, handler ->
                        sslWarning = PendingSslWarning(url, handler)
                    },
                    onRequestWebPermission = { request, currentUrl ->
                        val resources = request.resources.toList()
                        val androidPermissions = WebPermissionPolicy.androidPermissionsForCapture(resources)
                        val origin = request.origin.toString()
                        val sitePermissions = SitePermissionStore(context)
                        val rememberedGrant = !tab.isIncognito && resources.isNotEmpty() &&
                            resources.all { sitePermissions.isGranted(origin, it) } &&
                            androidPermissions?.all { permission ->
                                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                            } == true
                        if (
                            androidPermissions == null ||
                            !WebPermissionPolicy.isSameSecureOrigin(origin, currentUrl.orEmpty())
                        ) {
                            request.deny()
                        } else if (rememberedGrant) {
                            request.grant(resources.toTypedArray())
                        } else {
                            val description = when {
                                resources.contains(WebPermissionPolicy.CAMERA_RESOURCE) &&
                                    resources.contains(WebPermissionPolicy.MICROPHONE_RESOURCE) ->
                                    context.getString(R.string.web_permission_camera_microphone)
                                resources.contains(WebPermissionPolicy.CAMERA_RESOURCE) ->
                                    context.getString(R.string.web_permission_camera)
                                else -> context.getString(R.string.web_permission_microphone)
                            }
                            showPermissionPrompt(
                                PendingWebPermission(
                                    tabId = tab.id,
                                    origin = origin,
                                    description = description,
                                    androidPermissions = androidPermissions,
                                    resources = resources,
                                    permissionRequest = request,
                                    onGrant = { granted ->
                                        if (!tab.isIncognito) sitePermissions.rememberGrant(origin, resources)
                                        request.grant(granted.toTypedArray())
                                    },
                                    onDeny = { request.deny() },
                                ),
                            )
                        }
                    },
                    onRequestGeolocation = { origin, currentUrl, callback ->
                        val sitePermissions = SitePermissionStore(context)
                        val geoPermissionsAvailable = WebPermissionPolicy.locationAndroidPermissions.any { permission ->
                            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                        }
                        if (!WebPermissionPolicy.isSameSecureOrigin(origin, currentUrl.orEmpty())) {
                            callback.invoke(origin, false, false)
                        } else if (!tab.isIncognito && geoPermissionsAvailable &&
                            sitePermissions.isGranted(origin, "GEOLOCATION")
                        ) {
                            callback.invoke(origin, true, true)
                        } else {
                            showPermissionPrompt(
                                PendingWebPermission(
                                    tabId = tab.id,
                                    origin = origin,
                                    description = context.getString(R.string.web_permission_location),
                                    androidPermissions = WebPermissionPolicy.locationAndroidPermissions,
                                    onGrant = {
                                        if (!tab.isIncognito) sitePermissions.rememberGrant(origin, listOf("GEOLOCATION"))
                                        callback.invoke(origin, true, true)
                                    },
                                    onDeny = { callback.invoke(origin, false, false) },
                                ),
                            )
                        }
                    },
                    onCancelWebPermission = { request ->
                        val pending = pendingWebPermission
                        if (pending?.permissionRequest === request) {
                            pending.onDeny()
                            pendingWebPermission = null
                        }
                    },
                    onCancelGeolocation = {
                        val pending = pendingWebPermission
                        if (pending?.tabId == tab.id && pending.permissionRequest == null &&
                            pending.description == context.getString(R.string.web_permission_location)
                        ) {
                            pending.onDeny()
                            pendingWebPermission = null
                        }
                    },
                    onEnterFullscreen = { view, cb ->
                        fullscreenView = view
                        fullscreenCallback = cb
                    },
                    onExitFullscreen = {
                        fullscreenView = null
                        fullscreenCallback?.onCustomViewHidden()
                        fullscreenCallback = null
                    },
                    onOpenPopup = { resultMsg, isUserGesture ->
                        if (!isUserGesture) {
                            viewModel.onPopupBlocked()
                        } else {
                        val popup = createPopupWebView(
                            context = context,
                            tab = tab,
                            settings = settings,
                            viewModel = viewModel,
                            onTitleChanged = { t -> popupTitle = t },
                            onClosePopup = dismissPopup,
                        )
                        dismissPopup()
                        popupWebView = popup
                        val transport = resultMsg.obj as? WebView.WebViewTransport
                        transport?.webView = popup
                        resultMsg.sendToTarget()
                        }
                    },
                ).also { created ->
                    val savedBundle = viewModel.consumeSavedTabState(tab.id)
                    viewModel.registerWebView(tab.id, created)
                    val restored = savedBundle != null && created.restoreState(savedBundle) != null
                    if (!restored && tab.url.isNotBlank()) {
                        created.loadUrl(tab.url)
                    }
                }

                // Keep only the active tab's WebView attached to the FrameLayout
                if (container.childCount != 1 || container.getChildAt(0) !== targetWebView) {
                    container.removeAllViews()
                    (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                    container.addView(
                        targetWebView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }

                WebViewConfigurator.configure(
                    webView = targetWebView,
                    isIncognito = tab.isIncognito,
                    desktopMode = tab.isDesktopMode,
                    javaScriptEnabled = settings.javaScriptEnabled,
                    blockThirdPartyCookiesIncognito = settings.blockThirdPartyCookiesIncognito,
                )
            },
        )

        // OAuth / Google Sign-In / Multi-Window Popup Overlay
        popupWebView?.let { popup ->
            Dialog(
                onDismissRequest = dismissPopup,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = s.secureSignInDesc,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = popupTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = dismissPopup,
                                modifier = Modifier.testTag("close_popup_button"),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = s.closeWindowDesc)
                            }
                        }
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            factory = { popup },
                        )
                    }
                }
            }
        }

        // Fullscreen Video Overlay
        fullscreenView?.let { fsView ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { fsView },
                )
            }
        }

        pendingWebPermission?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    pending.onDeny()
                    pendingWebPermission = null
                },
                title = { Text(stringResource(R.string.web_permission_title)) },
                text = {
                    Text(
                        String.format(
                            stringResource(R.string.web_permission_body_format),
                            pending.description,
                            pending.origin,
                        ),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            permissionLauncher.launch(pending.androidPermissions.toTypedArray())
                        },
                    ) {
                        Text(stringResource(R.string.web_permission_allow))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            pending.onDeny()
                            pendingWebPermission = null
                        },
                    ) {
                        Text(stringResource(R.string.web_permission_deny))
                    }
                },
            )
        }

        // SSL Warning Dialog
        sslWarning?.let { warning ->
            AlertDialog(
                onDismissRequest = {
                    warning.handler.cancel()
                    sslWarning = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = s.sslDialogIconDesc,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = { Text(s.sslDialogTitle) },
                text = {
                    Text(
                        String.format(s.sslDialogBodyFormat, warning.url),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            warning.handler.proceed()
                            sslWarning = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(s.sslProceedButton)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            warning.handler.cancel()
                            sslWarning = null
                        },
                    ) {
                        Text(s.sslBackToSafetyButton)
                    }
                },
            )
        }
    }
}

@SuppressLint("JavascriptInterface")
private fun createConfiguredWebView(
    context: android.content.Context,
    tab: BrowserTab,
    settings: BrowserSettings,
    viewModel: BrowserViewModel,
    onShowFileChooser: (ValueCallback<Array<Uri>>) -> Unit,
    onSslWarning: (String, SslErrorHandler) -> Unit,
    onRequestWebPermission: (PermissionRequest, String?) -> Unit,
    onRequestGeolocation: (String, String?, GeolocationPermissions.Callback) -> Unit,
    onCancelWebPermission: (PermissionRequest) -> Unit,
    onCancelGeolocation: () -> Unit,
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit,
    onOpenPopup: (Message, Boolean) -> Unit,
): WebView {
    val tabId = tab.id
    return WebView(context).apply {
        val hostWebView = this
        // AndroidX MULTI_PROFILE must be applied before any WebView API is touched.
        val privateProfileReady = WebViewConfigurator.configure(
            webView = this,
            isIncognito = tab.isIncognito,
            desktopMode = tab.isDesktopMode,
            javaScriptEnabled = settings.javaScriptEnabled,
            blockThirdPartyCookiesIncognito = settings.blockThirdPartyCookiesIncognito,
        )
        if (tab.isIncognito && !privateProfileReady) {
            viewModel.onIncognitoIsolationUnavailable()
        }
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onMediaFound(json: String) {
                    viewModel.onDomMediaSniffed(tabId, json)
                }

                @JavascriptInterface
                fun onHookedUrl(url: String, titleHint: String) {
                    viewModel.onHookedMediaUrl(tabId, url, titleHint)
                }

                @JavascriptInterface
                fun onHookedResource(url: String, titleHint: String, contentType: String) {
                    viewModel.onHookedResource(tabId, url, titleHint, contentType)
                }

                @JavascriptInterface
                fun onMseBufferObserved(mimeType: String, byteLength: Int) {
                    viewModel.onConsoleLog(
                        tabId = tabId,
                        level = "INFO",
                        message = "MSE SourceBuffer append observed: $mimeType, $byteLength bytes (payload not captured)",
                        source = "Media Intelligence",
                    )
                }

            },
            "NavaMediaBridge",
        )

        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onLog(level: String, msg: String, source: String) {
                    viewModel.onConsoleLog(tabId, level, msg, source)
                }
            },
            "NavaConsoleBridge",
        )

        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            viewModel.downloadGenericUrl(url, mimeType, contentDisposition)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val pageHost = runCatching { Uri.parse(view?.url.orEmpty()).host }.getOrNull()
                    if (AdBlockerEngine.shouldBlockAdRedirect(url, pageHost, settings.adBlockEnabled)) {
                        viewModel.incrementBlockedAd(tabId)
                        return true // Block off-domain ad redirect cleanly like Aloha
                    }
                    return false
                }
                // Handle mailto:, tel:, intent:// safely
                return runCatching {
                    val intent = if (url.startsWith("intent://")) {
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }.getOrDefault(true)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val decision = viewModel.evaluateRequest(
                    tabId = tabId,
                    requestUrl = reqUrl,
                    method = request.method ?: "GET",
                    isMainFrame = request.isForMainFrame,
                    headers = request.requestHeaders ?: emptyMap(),
                )
                return if (decision.isBlocked) AdBlockerEngine.emptyBlockedResponse() else null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    viewModel.onPageStarted(tabId, url)
                    viewModel.injectStartExtensions(tabId, url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank() && url != "about:blank") {
                    viewModel.onPageFinished(
                        tabId = tabId,
                        url = url,
                        title = view?.title,
                        canBack = view?.canGoBack() == true,
                        canForward = view?.canGoForward() == true,
                    )
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                if (handler != null) {
                    onSslWarning(error?.url ?: view?.url.orEmpty(), handler)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.onPageProgress(tabId, newProgress)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    viewModel.onConsoleLog(
                        tabId = tabId,
                        level = it.messageLevel().name,
                        message = it.message(),
                        source = "${it.sourceId().substringAfterLast('/')}:${it.lineNumber()}",
                    )
                }
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                if (filePathCallback == null) return false
                onShowFileChooser(filePathCallback)
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                if (resultMsg != null) {
                    onOpenPopup(resultMsg, isUserGesture)
                    return isUserGesture
                }
                return false
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onEnterFullscreen(view, callback)
                }
            }

            override fun onHideCustomView() {
                onExitFullscreen()
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                onRequestWebPermission(request, hostWebView.url)
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (request != null) onCancelWebPermission(request)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                if (origin.isNullOrBlank() || callback == null) {
                    callback?.invoke(origin.orEmpty(), false, false)
                    return
                }
                onRequestGeolocation(origin, hostWebView.url, callback)
            }

            override fun onGeolocationPermissionsHidePrompt() {
                onCancelGeolocation()
            }
        }
    }
}

private fun createPopupWebView(
    context: android.content.Context,
    tab: BrowserTab,
    settings: BrowserSettings,
    viewModel: BrowserViewModel,
    onTitleChanged: (String) -> Unit,
    onClosePopup: () -> Unit,
): WebView {
    val parentHost = runCatching { Uri.parse(tab.url).host }.getOrNull()
    return WebView(context).apply {
        val privateProfileReady = WebViewConfigurator.configure(
            webView = this,
            isIncognito = tab.isIncognito,
            desktopMode = tab.isDesktopMode,
            javaScriptEnabled = settings.javaScriptEnabled,
            blockThirdPartyCookiesIncognito = settings.blockThirdPartyCookiesIncognito,
        )
        if (tab.isIncognito && !privateProfileReady) {
            viewModel.onIncognitoIsolationUnavailable()
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (AdBlockerEngine.shouldBlockAdRedirect(url, parentHost, settings.adBlockEnabled)) {
                    viewModel.incrementBlockedAd(tab.id)
                    onClosePopup()
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val decision = viewModel.evaluateRequest(
                    tabId = tab.id,
                    requestUrl = url,
                    method = request.method ?: "GET",
                    isMainFrame = request.isForMainFrame,
                    headers = request.requestHeaders ?: emptyMap(),
                )
                return if (decision.isBlocked) AdBlockerEngine.emptyBlockedResponse() else null
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) onTitleChanged(title)
            }

            override fun onCloseWindow(window: WebView?) {
                onClosePopup()
            }
        }
    }
}
