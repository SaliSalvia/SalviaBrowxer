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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.salvia.salviabrowxer.feature.browser.BrowserScreen
import com.salvia.salviabrowxer.feature.downloads.DownloadsScreen
import com.salvia.salviabrowxer.feature.player.MediaPlayerScreen
import com.salvia.salviabrowxer.feature.player.PlaylistEntry
import com.salvia.salviabrowxer.feature.settings.SettingsScreen
import com.salvia.salviabrowxer.ui.theme.SalviaBrowxerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var openDownloadsRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDownloadsRequested = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true

        setContent {
            SalviaBrowxerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SalviaBrowxerAppContent(openDownloadsRequested)
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
    }

    companion object {
        /** Set by the download notification so tapping it lands on the queue. */
        const val EXTRA_OPEN_DOWNLOADS = "com.salvia.salviabrowxer.OPEN_DOWNLOADS"
    }
}

@Composable
fun SalviaBrowxerAppContent(openDownloads: Boolean = false) {
    val navController = rememberNavController()

    LaunchedEffect(openDownloads) {
        if (openDownloads && navController.currentDestination?.route != ROUTE_DOWNLOADS) {
            navController.navigate(ROUTE_DOWNLOADS)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_BROWSER
    ) {
        composable(ROUTE_BROWSER) {
            BrowserScreen(
                onNavigateToDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_DOWNLOADS) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlayInApp = { path, title -> navigateToPlayer(navController, path, title) }
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** Navigates to the player for one media file (playlist of one). */
fun navigateToPlayer(navController: androidx.navigation.NavController, url: String, title: String) {
    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
    val encodedTitle = java.net.URLEncoder.encode(title.ifBlank { "Media" }, "UTF-8")
    navController.navigate("player/$encoded/$encodedTitle")
}

private const val ROUTE_BROWSER = "browser"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PLAYER = "player/{url}/{title}"
