package com.sreecharan.stressrelief

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.navigation.AppNavHost
import com.sreecharan.stressrelief.ui.theme.AppThemeUiState
import com.sreecharan.stressrelief.ui.theme.AppThemeViewModel
import com.sreecharan.stressrelief.ui.theme.AuraTheme
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity, edge-to-edge. Applies the Aurora theme from
 * [AppThemeViewModel] and hosts the root [AppNavHost]. Deep-link intents
 * (aura://mood, aura://breathe) are forwarded to the NavController both on
 * cold start and while the activity is alive (singleTask).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navControllerRef: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
            val onboardingCompleted by themeViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val darkTheme =
                when (val state = themeState) {
                    is AppThemeUiState.Ready -> {
                        when (state.themeMode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> systemDark
                        }
                    }

                    AppThemeUiState.Loading -> {
                        systemDark
                    }
                }
            val dynamicColor = (themeState as? AppThemeUiState.Ready)?.dynamicColor ?: false

            AuraTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                CompositionLocalProvider(LocalAuroraDark provides darkTheme) {
                    val navController = rememberNavController()
                    LaunchedEffect(navController) {
                        navControllerRef = navController
                        navController.handleDeepLink(resolveDeepLink(intent))
                    }
                    AppNavHost(
                        navController = navController,
                        onboardingCompleted = onboardingCompleted,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navControllerRef?.handleDeepLink(resolveDeepLink(intent))
    }

    /**
     * The Glance widget cannot put a data URI on its launch intent, so it
     * passes the deep link as an action-parameter extra ([DeepLinks.EXTRA_DEEP_LINK]).
     * Rehydrate it into [Intent.data] so NavController deep-link matching works.
     */
    private fun resolveDeepLink(intent: Intent): Intent {
        if (intent.data == null) {
            intent.getStringExtra(DeepLinks.EXTRA_DEEP_LINK)?.toUri()?.let {
                intent.data = it
            }
        }
        return intent
    }
}
