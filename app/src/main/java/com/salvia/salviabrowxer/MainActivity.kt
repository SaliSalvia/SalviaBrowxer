package com.salvia.salviabrowxer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.salvia.salviabrowxer.feature.browser.BrowserScreen
import com.salvia.salviabrowxer.feature.downloads.DownloadsScreen
import com.salvia.salviabrowxer.feature.settings.SettingsScreen
import com.salvia.salviabrowxer.feature.settings.SettingsViewModel
import com.salvia.salviabrowxer.ui.theme.SalviaBrowxerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var openDownloadsRequested by mutableStateOf(false)
    private var pendingNotificationNavigation by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Downloads still work without this permission; the notification is simply not shown.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        openDownloadsRequested = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
        pendingNotificationNavigation = openDownloadsRequested

        setContent {
            // The theme switch is owned by a root ViewModel so changing it in SettingsScreen
            // re-composes the whole app instead of only the settings destination.
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            SalviaBrowxerTheme(darkTheme = settingsState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SalviaBrowxerAppContent(
                        openDownloads = pendingNotificationNavigation,
                        onOpenDownloadsConsumed = { pendingNotificationNavigation = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDownloadsRequested = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
        if (openDownloadsRequested) {
            pendingNotificationNavigation = true
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** Set by the download notification so tapping it lands on the queue. */
        const val EXTRA_OPEN_DOWNLOADS = "com.salvia.salviabrowxer.OPEN_DOWNLOADS"
    }
}

@Composable
fun SalviaBrowxerAppContent(
    openDownloads: Boolean = false,
    onOpenDownloadsConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            if (navController.currentDestination?.route != ROUTE_DOWNLOADS) {
                navController.navigate(ROUTE_DOWNLOADS)
            }
            onOpenDownloadsConsumed()
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
