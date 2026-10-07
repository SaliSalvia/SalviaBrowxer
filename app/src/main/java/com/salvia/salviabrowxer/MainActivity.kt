package com.salvia.salviabrowxer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.salvia.salviabrowxer.feature.bookmarks.BookmarksScreen
import com.salvia.salviabrowxer.feature.browser.BrowserScreen
import com.salvia.salviabrowxer.feature.downloads.DownloadsScreen
import com.salvia.salviabrowxer.feature.history.HistoryScreen
import com.salvia.salviabrowxer.feature.home.HomeScreen
import com.salvia.salviabrowxer.feature.player.MediaPlayerScreen
import com.salvia.salviabrowxer.feature.settings.SettingsScreen
import com.salvia.salviabrowxer.ui.theme.SalviaBrowxerTheme
import com.salvia.salviabrowxer.ui.utils.SharedLinkParser
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var openDownloadsRequested by mutableStateOf(false)

    /** A link the app was launched with, or received by share. Consumed by the browser. */
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDownloadsRequested = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
        incomingUrl = sharedUrlFrom(intent)

        setContent {
            SalviaBrowxerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SalviaBrowxerAppContent(
                        openDownloads = openDownloadsRequested,
                        incomingUrl = incomingUrl,
                        onIncomingUrlConsumed = { incomingUrl = null }
                    )
                }
            }
        }
    }

    // androidx.activity 1.9 exposes a non-null onNewIntent(Intent); matching that
    // signature is what keeps this override (and the notification tap path) working.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDownloadsRequested = intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)
        sharedUrlFrom(intent)?.let { incomingUrl = it }
    }

    companion object {
        /** Set by the download notification so tapping it lands on the queue. */
        const val EXTRA_OPEN_DOWNLOADS = "com.salvia.salviabrowxer.OPEN_DOWNLOADS"
    }
}

/** ACTION_VIEW data, or the first link inside ACTION_SEND text. Null when the intent carries none. */
private fun sharedUrlFrom(intent: Intent?): String? = when (intent?.action) {
    Intent.ACTION_VIEW -> SharedLinkParser.fromViewData(intent.dataString)
    Intent.ACTION_SEND -> SharedLinkParser.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
    else -> null
}

@Composable
fun SalviaBrowxerAppContent(
    openDownloads: Boolean = false,
    incomingUrl: String? = null,
    onIncomingUrlConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(openDownloads) {
        if (openDownloads && navController.currentDestination?.route != ROUTE_DOWNLOADS) {
            navController.navigate(ROUTE_DOWNLOADS)
        }
    }

    // A shared link always lands in the browser, from wherever the user was.
    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null && navController.currentDestination?.route != ROUTE_BROWSER) {
            navController.navigate(ROUTE_BROWSER) {
                popUpTo(ROUTE_BROWSER) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        // The app is a browser first, so the browser is what the user meets on launch: the media
        // tray and its pill are what turn browsing into downloading, and the paste field stays one
        // tap away in the overflow menu ("Start screen") for when a link is already in hand.
        startDestination = ROUTE_BROWSER
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                // A submitted link is handed to the browser that is already on the back stack, so
                // its tabs and WebViews survive. Whether it becomes a download or a page load is
                // decided in the browser's view model, so only the URL travels from here.
                onOpenLink = { url -> deliverToBrowser(navController, KEY_PASTED_LINK, url) },
                onNavigateToBrowser = { navController.returnToBrowser() },
                onNavigateToDownloads = { navController.navigate(ROUTE_DOWNLOADS) }
            )
        }
        composable(ROUTE_BROWSER) { entry ->
            val requestedUrl by entry.savedStateHandle
                .getStateFlow<String?>(KEY_OPEN_URL, null)
                .collectAsStateWithLifecycle()
            val pastedUrl by entry.savedStateHandle
                .getStateFlow<String?>(KEY_PASTED_LINK, null)
                .collectAsStateWithLifecycle()
            BrowserScreen(
                onNavigateToDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                onNavigateToBookmarks = { navController.navigate(ROUTE_BOOKMARKS) },
                onNavigateToHistory = { navController.navigate(ROUTE_HISTORY) },
                onNavigateToHome = { navController.navigate(ROUTE_HOME) { launchSingleTop = true } },
                submittedLink = pastedUrl,
                onSubmittedLinkConsumed = { entry.savedStateHandle[KEY_PASTED_LINK] = null },
                inAppUrl = requestedUrl,
                onInAppUrlConsumed = { entry.savedStateHandle[KEY_OPEN_URL] = null },
                externalUrl = incomingUrl,
                onExternalUrlConsumed = onIncomingUrlConsumed
            )
        }
        composable(ROUTE_DOWNLOADS) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlayInApp = { path, title -> navigateToPlayer(navController, path, title) }
            )
        }
        composable(ROUTE_BOOKMARKS) {
            BookmarksScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url -> openUrlInBrowser(navController, url) }
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url -> openUrlInBrowser(navController, url) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = ROUTE_PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { entry ->
            val url = entry.arguments?.getString("url").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            MediaPlayerScreen(
                mediaUrl = URLDecoder.decode(url, "UTF-8"),
                mediaTitle = URLDecoder.decode(title, "UTF-8"),
                onBack = { navController.popBackStack() },
                // Deleting a dead file from the player returns to the queue it came from.
                onDeleted = { navController.popBackStack() }
            )
        }
    }
}

/** Returns to the browser and asks it to load [url] in the tab the user was already on. */
private fun openUrlInBrowser(navController: NavController, url: String) {
    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_OPEN_URL, url)
    navController.popBackStack()
}

/**
 * Drops everything above the browser and returns to it, keeping its tabs alive.
 *
 * The browser is the app's first screen, so a pushed screen can never leave a second, empty
 * browser on top of the real one: the user comes back to the page they were reading.
 */
private fun NavController.returnToBrowser() {
    if (!popBackStack(ROUTE_BROWSER, inclusive = false)) {
        navigate(ROUTE_BROWSER) { launchSingleTop = true }
    }
}

/**
 * Hands [url] to the browser that is already on the back stack under [key], then returns to it.
 *
 * Only the URL travels: the browser's view model is the single place that decides whether a pasted
 * link becomes a download or a page load, and reusing the live entry keeps the open tabs.
 */
private fun deliverToBrowser(navController: NavController, key: String, url: String) {
    val entry = runCatching { navController.getBackStackEntry(ROUTE_BROWSER) }.getOrNull()
    if (entry == null) {
        navController.navigate(ROUTE_BROWSER) { launchSingleTop = true }
        return
    }
    entry.savedStateHandle[key] = url
    navController.popBackStack(ROUTE_BROWSER, inclusive = false)
}

/** Navigates to the player for one media file (playlist of one). */
fun navigateToPlayer(navController: NavController, url: String, title: String) {
    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
    val encodedTitle = java.net.URLEncoder.encode(title.ifBlank { "Media" }, "UTF-8")
    navController.navigate("player/$encoded/$encodedTitle")
}

private const val ROUTE_HOME = "home"
private const val ROUTE_BROWSER = "browser"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_BOOKMARKS = "bookmarks"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PLAYER = "player/{url}/{title}"
private const val KEY_OPEN_URL = "open_url"
private const val KEY_PASTED_LINK = "pasted_link"
