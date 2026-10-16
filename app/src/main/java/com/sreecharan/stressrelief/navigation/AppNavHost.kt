package com.sreecharan.stressrelief.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.breathe.BreatheScreen
import com.sreecharan.stressrelief.ui.breathe.BreathingSessionScreen
import com.sreecharan.stressrelief.ui.breathe.ProgramsScreen
import com.sreecharan.stressrelief.ui.home.HomeScreen
import com.sreecharan.stressrelief.ui.insights.InsightsScreen
import com.sreecharan.stressrelief.ui.journal.JournalEditorScreen
import com.sreecharan.stressrelief.ui.journal.JournalScreen
import com.sreecharan.stressrelief.ui.mood.MoodCheckInSheet
import com.sreecharan.stressrelief.ui.onboarding.OnboardingScreen
import com.sreecharan.stressrelief.ui.paywall.PaywallLaunchContext
import com.sreecharan.stressrelief.ui.paywall.PaywallScreen
import com.sreecharan.stressrelief.ui.settings.SettingsScreen
import com.sreecharan.stressrelief.ui.sleep.SleepScreen
import com.sreecharan.stressrelief.ui.sleep.SleepSessionScreen
import kotlin.reflect.KClass

private const val KEY_PROGRAM_ID = "programId"
private const val KEY_PROGRAM_DAY = "programDay"

/**
 * The single root NavHost (contract section 3). Four tabs ride on nested
 * graphs so each tab keeps its own back stack with saveState/restoreState.
 * Every route in [Routes.kt] has a real destination; the Builder 1
 * placeholders are gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    onboardingCompleted: Boolean,
    paywallLaunchContext: PaywallLaunchContext,
    modifier: Modifier = Modifier,
) {
    val startDestination: Any = if (onboardingCompleted) HomeTabGraph else OnboardingRoute
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(HomeTabGraph) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }

        navigation<HomeTabGraph>(startDestination = HomeRoute) {
            composable<HomeRoute> {
                MainTabScaffold(navController) {
                    HomeScreen(onNavigate = { navController.navigate(it) })
                }
            }
            dialog<MoodCheckInRoute>(
                deepLinks = listOf(navDeepLink<MoodCheckInRoute>(basePath = "aura://mood")),
            ) {
                ModalBottomSheet(onDismissRequest = { navController.popBackStack() }) {
                    MoodCheckInSheet(onDismiss = { navController.popBackStack() })
                }
            }
            composable<InsightsRoute> {
                // Premium gating lives inside InsightsScreen (Builder 5's ProGate);
                // this lambda only makes sure a paywall launch from the upsell
                // is tagged as a premium-feature launch (no close affordance).
                InsightsScreen(
                    onNavigate = {
                        navController.navigateWithPaywallContext(paywallLaunchContext, it)
                    },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = {
                        paywallLaunchContext.launchFromSettings()
                        navController.navigate(PaywallRoute)
                    },
                )
            }
        }

        navigation<BreatheTabGraph>(startDestination = BreatheRoute) {
            composable<BreatheRoute>(
                deepLinks = listOf(navDeepLink<BreatheRoute>(basePath = "aura://breathe")),
            ) {
                MainTabScaffold(navController) {
                    BreatheScreen(
                        onNavigateToSession = { goal, patternId ->
                            navController.navigate(BreathingSessionRoute(goal, patternId))
                        },
                    )
                }
            }
            composable<BreathingSessionRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<BreathingSessionRoute>()
                val programHandle = navController.previousBackStackEntry?.savedStateHandle
                BreathingSessionScreen(
                    programId = programHandle?.get<String>(KEY_PROGRAM_ID),
                    programDay = programHandle?.get<Int>(KEY_PROGRAM_DAY),
                    onFinished = { navController.popBackStack() },
                )
            }
            composable<ProgramsRoute> {
                ProgramsScreen(
                    onStartProgramSession = { goal, patternId, programId, day ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_PROGRAM_ID, programId)
                            set(KEY_PROGRAM_DAY, day)
                        }
                        navController.navigate(BreathingSessionRoute(goal, patternId))
                    },
                )
            }
        }

        navigation<JournalTabGraph>(startDestination = JournalRoute) {
            composable<JournalRoute> {
                MainTabScaffold(navController) {
                    JournalScreen(onNavigate = { navController.navigate(it) })
                }
            }
            composable<JournalEditorRoute> {
                JournalEditorScreen(onBack = { navController.popBackStack() })
            }
        }

        navigation<SleepTabGraph>(startDestination = SleepRoute) {
            composable<SleepRoute> {
                MainTabScaffold(navController) {
                    SleepScreen(
                        onNavigateToSession = { navController.navigate(SleepSessionRoute) },
                        onOpenPaywall = {
                            paywallLaunchContext.launchFromPremiumFeature()
                            navController.navigate(PaywallRoute)
                        },
                    )
                }
            }
            composable<SleepSessionRoute> {
                SleepSessionScreen(onFinished = { navController.popBackStack() })
            }
        }

        composable<PaywallRoute> {
            PaywallScreen(onDismiss = { navController.popBackStack() })
        }
    }
}

/**
 * Navigates, tagging paywall launches from premium features so the paywall
 * hides its close affordance per the PaywallLaunchContext contract.
 */
private fun NavHostController.navigateWithPaywallContext(
    launchContext: PaywallLaunchContext,
    route: Any,
) {
    if (route == PaywallRoute) launchContext.launchFromPremiumFeature()
    navigate(route)
}

private enum class TabDestination(
    val routeClass: KClass<*>,
    val startRoute: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(HomeRoute::class, HomeRoute, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Breathe(BreatheRoute::class, BreatheRoute, R.string.tab_breathe, Icons.Filled.Air, Icons.Outlined.Air),
    Journal(
        JournalRoute::class,
        JournalRoute,
        R.string.tab_journal,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    Sleep(SleepRoute::class, SleepRoute, R.string.tab_sleep, Icons.Filled.Bedtime, Icons.Outlined.Bedtime),
}

/**
 * Scaffold with the M3 Expressive NavigationBar. The bar shows only on the
 * four tab routes; pushed screens (settings, editors, sessions) go full-bleed.
 */
@Composable
private fun MainTabScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = TabDestination.entries.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    TabDestination.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Navigate to the tab's start destination directly,
                                // not the graph object: navigating to the graph
                                // silently no-ops when returning to Home from
                                // another tab (launchSingleTop sees the graph
                                // already on the stack and skips the switch).
                                navController.navigate(tab.startRoute) {
                                    // HomeTabGraph is always on the stack when tabs are visible.
                                    popUpTo(HomeTabGraph) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes),
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) { content() }
    }
}
