package com.salvia.salviabrowxer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SalviaBrowxerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SalviaBrowxerAppContent()
                }
            }
        }
    }
}

@Composable
fun SalviaBrowxerAppContent() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "browser"
    ) {
        composable("browser") {
            BrowserScreen(
                onNavigateToDownloads = { navController.navigate("downloads") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("downloads") {
            DownloadsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}