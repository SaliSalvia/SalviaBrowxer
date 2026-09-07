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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.salvia.salviabrowxer.feature.browser.BrowserScreen
import com.salvia.salviabrowxer.feature.downloads.DownloadsScreen
import com.salvia.salviabrowxer.feature.settings.SettingsScreen
import com.salvia.salviabrowxer.ui.theme.SalviaBrowxerTheme
import dagger.hilt.android.AndroidEntryPoint

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDownloadsRequested = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
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
                onBack = { navController.popBackStack() }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private const val ROUTE_BROWSER = "browser"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_SETTINGS = "settings"
