package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
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
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.BrowserSettings
import com.example.engine.AdBlockerEngine
import com.example.engine.BridgeGuard
import com.example.engine.ExternalIntentPolicy
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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

private data class PendingHttpAuth(
    val host: String,
    val realm: String?,
    val handler: HttpAuthHandler,
)

private data class PendingJsDialog(
    val kind: String,
    val origin: String,
    val message: String,
    val defaultValue: String,
    val result: JsResult,
    val promptResult: JsPromptResult? = null,
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
    var pendingHttpAuth by remember(tab.id) { mutableStateOf<PendingHttpAuth?>(null) }
    var authUsername by remember(tab.id) { mutableStateOf("") }
    var authPassword by remember(tab.id) { mutableStateOf("") }
    var pendingJsDialog by remember(tab.id) { mutableStateOf<PendingJsDialog?>(null) }
    var jsDialogsSuppressedFor by remember(tab.id) { mutableStateOf<String?>(null) }
    var pendingExternalIntent by remember(tab.id) { mutableStateOf<Pair<Intent, String>?>(null) }
    var pageContextTarget by remember(tab.id) { mutableStateOf<PageContextTarget?>(null) }

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
                val frame = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                // Pull-to-refresh like mobile Chrome. A gesture only starts when the page's root
                // scroller is at the very top, so in-page scrolling is never hijacked.
                SwipeRefreshLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    addView(frame)
                    setOnChildScrollUpCallback { _, _ ->
                        val web = frame.getChildAt(0) as? WebView
                        web == null || web.scrollY > 0 || web.canScrollVertically(-1)
                    }
                    setOnRefreshListener {
                        viewModel.pullToRefresh()
                        postDelayed({ isRefreshing = false }, 700L)
                    }
                }
            },
            update = { swipe ->
                swipe.isEnabled = !tab.isStartPage && settings.pullToRefreshEnabled
                val container = swipe.getChildAt(0) as FrameLayout
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
                    onRequestHttpAuth = { pending ->
                        authUsername = ""
                        authPassword = ""
                        pendingHttpAuth?.handler?.cancel()
                        pendingHttpAuth = pending
                    },
                    onRequestJsDialog = { pending ->
                        val host = runCatching { Uri.parse(pending.origin).host }.getOrNull()
                        if (host != null && host == jsDialogsSuppressedFor) {
                            pending.result.cancel()
                        } else {
                            // Never stack dialogs: a newer one replaces (and cancels) the older.
                            pendingJsDialog?.takeIf { it !== pending }?.result?.cancel()
                            pendingJsDialog = pending
                        }
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
                                        if (!tab.isIncognito) sitePermissions.rememberGrant(origin, granted)
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
                    onExternalIntent = { intent, label -> pendingExternalIntent = intent to label },
                    onPageContextMenu = { target -> pageContextTarget = target },
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
                            onExternalIntent = { intent, label -> pendingExternalIntent = intent to label },
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
                        viewModel.loadUrlWithPrivacy(created, tab.url)
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

        tab.loadError?.let { error ->
            BrowserErrorPage(
                error = error,
                onRetry = { viewModel.retryAfterLoadError() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        pendingExternalIntent?.let { (intent, label) ->
            ExternalAppDialog(
                targetLabel = label,
                onOpen = {
                    pendingExternalIntent = null
                    runCatching { context.startActivity(intent) }
                        .onFailure { viewModel.showToast(s.externalAppFailed) }
                },
                onDismiss = { pendingExternalIntent = null },
            )
        }

        pageContextTarget?.let { target ->
            PageContextMenuDialog(
                target = target,
                isIncognito = tab.isIncognito,
                onOpenInNewTab = { url ->
                    pageContextTarget = null
                    viewModel.createNewTab(isIncognito = false, initialUrl = url)
                },
                onOpenInIncognitoTab = { url ->
                    pageContextTarget = null
                    viewModel.createNewTab(isIncognito = true, initialUrl = url)
                },
                onCopy = { url ->
                    pageContextTarget = null
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("URL", url))
                    viewModel.showToast(s.toastLinkCopied)
                },
                onShare = { url ->
                    pageContextTarget = null
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    runCatching { context.startActivity(Intent.createChooser(share, s.ctxShareLink)) }
                },
                onDownload = { url ->
                    pageContextTarget = null
                    viewModel.downloadGenericUrl(url, "", null)
                },
                onDismiss = { pageContextTarget = null },
            )
        }

        // Native Android print pipeline; the system print UI also exposes "Save as PDF".
        IconButton(
            onClick = {
                val activity = context.findActivity()
                val activeWebView = viewModel.getWebView(tab.id)
                val printManager = activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (activity == null || activeWebView == null || printManager == null) {
                    Toast.makeText(context, s.printUnavailable, Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        printManager.print(
                            "SalviaBrowser-${tab.id.take(8)}",
                            activeWebView.createPrintDocumentAdapter("SalviaBrowser"),
                            PrintAttributes.Builder().build(),
                        )
                    }.onFailure {
                        Toast.makeText(context, s.printFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(24.dp),
            ),
        ) {
            Icon(Icons.Default.Print, contentDescription = s.printContentDesc)
        }

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

        pendingHttpAuth?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    pending.handler.cancel()
                    authUsername = ""
                    authPassword = ""
                    pendingHttpAuth = null
                },
                title = { Text(String.format(s.httpAuthTitleFormat, pending.host)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pending.realm.orEmpty())
                        OutlinedTextField(value = authUsername, onValueChange = { authUsername = it }, label = { Text(s.httpAuthUsername) }, singleLine = true)
                        OutlinedTextField(
                            value = authPassword,
                            onValueChange = { authPassword = it },
                            label = { Text(s.httpAuthPassword) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        pending.handler.proceed(authUsername, authPassword)
                        authUsername = ""; authPassword = ""; pendingHttpAuth = null
                    }) { Text(s.httpAuthSignIn) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pending.handler.cancel(); authUsername = ""; authPassword = ""; pendingHttpAuth = null }) { Text(s.dialogCancel) }
                },
            )
        }

        pendingJsDialog?.let { pending ->
            var promptValue by remember(pending) { mutableStateOf(pending.defaultValue) }
            AlertDialog(
                onDismissRequest = { pending.result.cancel(); pendingJsDialog = null },
                title = {
                    val originHost = runCatching { Uri.parse(pending.origin).host }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: pending.origin.take(60)
                    Text(String.format(s.jsAlertTitleFormat, originHost))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Pages cannot spoof long fake-UI messages: the text is capped and scrolls.
                        Text(pending.message.take(4_000), modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
                        if (pending.kind == "prompt") {
                            OutlinedTextField(value = promptValue, onValueChange = { promptValue = it }, singleLine = true)
                        }
                        // Dialog-loop protection (as in Chrome): stop this page's further dialogs.
                        androidx.compose.material3.TextButton(onClick = {
                            jsDialogsSuppressedFor = runCatching { Uri.parse(pending.origin).host }.getOrNull()
                            pending.result.cancel()
                            pendingJsDialog = null
                        }) { Text(s.jsDialogSuppress, style = MaterialTheme.typography.labelMedium) }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (pending.kind == "prompt") pending.promptResult?.confirm(promptValue) else pending.result.confirm()
                        pendingJsDialog = null
                    }) { Text(s.jsDialogOk) }
                },
                dismissButton = {
                    if (pending.kind != "alert") {
                        OutlinedButton(onClick = { pending.result.cancel(); pendingJsDialog = null }) { Text(s.dialogCancel) }
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
                // "Back to safety" is the primary action; proceeding is a de-emphasized,
                // explicitly destructive choice (as in Chrome's interstitial).
                confirmButton = {
                    Button(
                        onClick = {
                            warning.handler.cancel()
                            sslWarning = null
                        },
                    ) {
                        Text(s.sslBackToSafetyButton)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            warning.handler.proceed()
                            sslWarning = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(s.sslProceedButton)
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
    onRequestHttpAuth: (PendingHttpAuth) -> Unit,
    onRequestJsDialog: (PendingJsDialog) -> Unit,
    onRequestWebPermission: (PermissionRequest, String?) -> Unit,
    onRequestGeolocation: (String, String?, GeolocationPermissions.Callback) -> Unit,
    onCancelWebPermission: (PermissionRequest) -> Unit,
    onCancelGeolocation: () -> Unit,
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit,
    onExternalIntent: (Intent, String) -> Unit,
    onPageContextMenu: (PageContextTarget) -> Unit,
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

        // Register the media hook (and privacy signals) to run before any page script in every
        // frame, so early MSE/fetch/XHR media requests are never missed. The scripts are
        // idempotent, so the onPageStarted evaluateJavascript fallback stays harmless.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            viewModel.documentStartScripts().forEach { script ->
                runCatching { WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*")) }
            }
        }

        // Long-press context menu for links and images.
        setOnLongClickListener {
            val hit = hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    val link = hit.extra?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    if (link == null) {
                        false
                    } else {
                        onPageContextMenu(PageContextTarget(linkUrl = link, imageUrl = null))
                        true
                    }
                }
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val image = hit.extra?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    val link = if (hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                        runCatching {
                            val message = Handler(Looper.getMainLooper()).obtainMessage()
                            requestFocusNodeHref(message)
                            message.data.getString("url")
                        }.getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    } else {
                        null
                    }
                    if (image == null && link == null) {
                        false
                    } else {
                        onPageContextMenu(PageContextTarget(linkUrl = link, imageUrl = image))
                        true
                    }
                }
                else -> false
            }
        }

        addJavascriptInterface(
            object {
                // Every bridge call is size-capped and rate-limited per tab; downstream
                // verification (probe/MIME/DRM checks) still applies to every payload.
                @JavascriptInterface
                fun onMediaFound(json: String?) {
                    if (json.isNullOrEmpty() || json.length > BridgeGuard.MAX_JSON_CHARS) return
                    if (!viewModel.allowBridgeCall(tabId, "dom")) return
                    viewModel.onDomMediaSniffed(tabId, json)
                }

                @JavascriptInterface
                fun onHookedUrl(url: String?, titleHint: String?) {
                    if (!BridgeGuard.isAcceptableUrl(url)) return
                    if (!viewModel.allowBridgeCall(tabId, "hook")) return
                    viewModel.onHookedMediaUrl(tabId, url!!.trim(), BridgeGuard.clip(titleHint, BridgeGuard.MAX_TITLE_CHARS))
                }

                @JavascriptInterface
                fun onHookedResource(url: String?, titleHint: String?, contentType: String?) {
                    if (!BridgeGuard.isAcceptableUrl(url)) return
                    if (!viewModel.allowBridgeCall(tabId, "hook")) return
                    viewModel.onHookedResource(
                        tabId,
                        url!!.trim(),
                        BridgeGuard.clip(titleHint, BridgeGuard.MAX_TITLE_CHARS),
                        BridgeGuard.clip(contentType, 256),
                    )
                }

                @JavascriptInterface
                fun onMseBufferObserved(
                    mimeType: String,
                    byteLength: Int,
                    sourceUrl: String,
                    responseContentType: String,
                    responseStatus: Int,
                    contentRange: String,
                ) {
                    if (!BridgeGuard.isAcceptableUrl(sourceUrl)) return
                    if (!viewModel.allowBridgeCall(tabId, "mse")) return
                    viewModel.onMseNetworkSourceObserved(
                        tabId = tabId,
                        sourceBufferMimeType = BridgeGuard.clip(mimeType, 256),
                        responseContentType = BridgeGuard.clip(responseContentType, 256),
                        sourceUrl = sourceUrl.trim(),
                        byteLength = byteLength,
                        responseStatus = responseStatus,
                        contentRange = BridgeGuard.clip(contentRange, 128),
                    )
                }

            },
            "NavaMediaBridge",
        )

        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onLog(level: String?, msg: String?, source: String?) {
                    if (!viewModel.allowBridgeCall(tabId, "console")) return
                    viewModel.onConsoleLog(
                        tabId,
                        BridgeGuard.clip(level, 16),
                        BridgeGuard.clip(msg, BridgeGuard.MAX_LOG_CHARS),
                        BridgeGuard.clip(source, 256),
                    )
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
                    if (request.isForMainFrame) {
                        val upgraded = viewModel.httpsUpgradeFor(tabId, url)
                        if (upgraded != null && view != null) {
                            viewModel.loadUrlWithPrivacy(view, upgraded)
                            return true
                        }
                    }
                    return false
                }
                // Internal schemes are handled by the WebView itself.
                if (url.startsWith("about:") || url.startsWith("blob:")) return false
                if (!request.isForMainFrame && url.startsWith("data:")) return false
                // External apps: only from a user gesture or a server redirect (OAuth app
                // links), always sanitized (BROWSABLE only, no component/flags) and confirmed.
                if (!request.hasGesture() && !request.isRedirect) return true
                val pm = context.packageManager
                val decision = ExternalIntentPolicy.resolve(url) { candidate ->
                    runCatching { candidate.resolveActivity(pm) != null }.getOrDefault(false)
                }
                when (decision) {
                    is ExternalIntentPolicy.Decision.LoadInBrowser -> view?.let { viewModel.loadUrlWithPrivacy(it, decision.url) }
                    is ExternalIntentPolicy.Decision.LaunchExternal -> onExternalIntent(decision.intent, decision.targetLabel)
                    ExternalIntentPolicy.Decision.Block -> Unit
                }
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request == null || !request.isForMainFrame) return
                val description = error?.description?.toString().orEmpty()
                // Cancelled navigations (stop button, download hand-off) are not errors.
                if (description.contains("ERR_ABORTED", ignoreCase = true)) return
                val fallback = viewModel.onMainFrameError(tabId, request.url.toString(), description)
                if (fallback != null && view != null) viewModel.loadUrlWithPrivacy(view, fallback)
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // Returning true keeps the app alive; the dead WebView is destroyed and the tab
                // shows a crash page that recreates it on retry.
                if (view == null) return false
                viewModel.onRendererGone(tabId, view, crashed = detail?.didCrash() == true)
                return true
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

            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O_MR1)
            override fun onSafeBrowsingHit(
                view: WebView?,
                request: WebResourceRequest?,
                threatType: Int,
                callback: android.webkit.SafeBrowsingResponse?,
            ) {
                // Chromium's interstitial with "Back to safety" as the primary action; reporting
                // is opt-in by the user from the interstitial itself.
                callback?.showInterstitial(true)
                if (request?.isForMainFrame == true) {
                    viewModel.onSafeBrowsingBlocked(tabId, request.url?.toString().orEmpty())
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && errorResponse != null) {
                    android.util.Log.i("SalviaWebView", "HTTP ${errorResponse.statusCode} for main frame of tab $tabId")
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler,
                host: String,
                realm: String?,
            ) {
                onRequestHttpAuth(PendingHttpAuth(host, realm, handler))
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
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                onRequestJsDialog(PendingJsDialog("alert", url.orEmpty(), message.orEmpty(), "", result))
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                onRequestJsDialog(PendingJsDialog("confirm", url.orEmpty(), message.orEmpty(), "", result))
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?,
            ): Boolean {
                if (result == null) return false
                onRequestJsDialog(PendingJsDialog("prompt", url.orEmpty(), message.orEmpty(), defaultValue.orEmpty(), result, result))
                return true
            }

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
    onExternalIntent: (Intent, String) -> Unit,
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
                val scheme = request.url?.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                // Popups (OAuth, payment) often finish by redirecting to an app scheme or an
                // intent:// link: apply the same hardened external-intent policy as tabs.
                val pm = context.packageManager
                when (val decision = ExternalIntentPolicy.resolve(url) { candidate ->
                    runCatching { candidate.resolveActivity(pm) != null }.getOrDefault(false)
                }) {
                    is ExternalIntentPolicy.Decision.LoadInBrowser -> view?.loadUrl(decision.url)
                    is ExternalIntentPolicy.Decision.LaunchExternal -> {
                        if (request.hasGesture() || request.isRedirect) {
                            onExternalIntent(decision.intent, decision.targetLabel)
                        }
                        if (view?.url.isNullOrBlank() || view?.url == "about:blank") onClosePopup()
                    }
                    ExternalIntentPolicy.Decision.Block -> Unit
                }
                return true
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

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                onClosePopup()
                return true
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
