package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.BrowserSettings
import com.example.engine.AdBlockerEngine
import com.example.engine.WebViewConfigurator
import com.example.ui.BrowserTab
import com.example.ui.BrowserViewModel

private data class PendingSslWarning(
    val url: String,
    val handler: SslErrorHandler,
)

@Composable
fun WebViewContainer(
    tab: BrowserTab,
    settings: BrowserSettings,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var sslWarning by remember { mutableStateOf<PendingSslWarning?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var popupTitle by remember { mutableStateOf("ورود امن / پنجره بازشو") }

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
                    onEnterFullscreen = { view, cb ->
                        fullscreenView = view
                        fullscreenCallback = cb
                    },
                    onExitFullscreen = {
                        fullscreenView = null
                        fullscreenCallback?.onCustomViewHidden()
                        fullscreenCallback = null
                    },
                    onOpenPopup = { resultMsg ->
                        val popup = createPopupWebView(
                            context = context,
                            tab = tab,
                            settings = settings,
                            viewModel = viewModel,
                            onTitleChanged = { t -> popupTitle = t },
                            onClosePopup = {
                                popupWebView?.destroy()
                                popupWebView = null
                            },
                        )
                        popupWebView = popup
                        val transport = resultMsg.obj as? WebView.WebViewTransport
                        transport?.webView = popup
                        resultMsg.sendToTarget()
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
                onDismissRequest = {
                    popup.destroy()
                    popupWebView = null
                },
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
                                    contentDescription = "ورود امن",
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
                                onClick = {
                                    popup.destroy()
                                    popupWebView = null
                                },
                                modifier = Modifier.testTag("close_popup_button"),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "بستن پنجره")
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
                        contentDescription = "هشدار امنیتی گواهی SSL",
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = { Text("هشدار گواهی امنیتی SSL") },
                text = {
                    Text(
                        "گواهی امنیتی وب‌سایت زیر معتبر نیست یا منقضی شده است:\n${warning.url}\n\nآیا از ادامه دادن مطمئن هستید؟",
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
                        Text("ادامه با ریسک")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            warning.handler.cancel()
                            sslWarning = null
                        },
                    ) {
                        Text("بازگشت به امنیت")
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
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit,
    onOpenPopup: (Message) -> Unit,
): WebView {
    val tabId = tab.id
    return WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        WebViewConfigurator.configure(
            webView = this,
            isIncognito = tab.isIncognito,
            desktopMode = tab.isDesktopMode,
            javaScriptEnabled = settings.javaScriptEnabled,
            blockThirdPartyCookiesIncognito = settings.blockThirdPartyCookiesIncognito,
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
                    onOpenPopup(resultMsg)
                    return true
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
                request?.grant(request.resources)
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
        WebViewConfigurator.configure(
            webView = this,
            isIncognito = tab.isIncognito,
            desktopMode = tab.isDesktopMode,
            javaScriptEnabled = settings.javaScriptEnabled,
            blockThirdPartyCookiesIncognito = settings.blockThirdPartyCookiesIncognito,
        )
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
