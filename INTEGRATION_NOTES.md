# Integration fixes (apply after all builders finish)

1. **DispatchersModule package mismatch**: `di/DispatchersModule.kt` sits at
   `com/sreecharan/stressrelief/DispatchersModule.kt` but still declares
   `package com.sreecharan.apptemplate.di`. Builder 2 imports from the old
   package and it compiles, but rename to `com.sreecharan.stressrelief.di`,
   move to `di/` subdir, and update all imports. (Do NOT do this while builders
   are still running — they may also import the old package.)
2. **Health Connect manifest entries** (Builder 2 flag): add to AndroidManifest.xml:
   - `<uses-permission android:name="android.permission.health.READ_HEART_RATE"/>`
   - `<uses-permission android:name="android.permission.health.READ_SLEEP"/>`
   - the VIEW_PERMISSION_USAGE activity-alias intent filter required on Android 14+.
3. **Launcher icon**: wire `icon-art/` assets per `icon-art/ICON_README.md`
   (density PNGs → res/mipmap-*, anydpi-v26 adaptive XML, bg color #022C34).
4. Delete Builder 1 placeholder composables once real screens land; rewire
   AppNavHost destinations (Builder 3 + Builder 5 snippets):
   - BreatheRoute -> BreatheScreen(onNavigateToSession = { goal, patternId ->
       navController.navigate(BreathingSessionRoute(goal, patternId)) })
   - ProgramsRoute -> ProgramsScreen(onStartProgramSession =
       { goal, patternId, programId, day -> save programId/day in
         currentBackStackEntry.savedStateHandle; navigate(BreathingSessionRoute(goal, patternId)) })
   - BreathingSessionRoute -> BreathingSessionScreen(programId/programDay from
     previousBackStackEntry.savedStateHandle, onFinished = { popBackStack() })
   - SleepRoute -> SleepScreen(onNavigateToSession = { navigate(SleepSessionRoute) },
     onOpenPaywall = { navigate(PaywallRoute) })
   - SleepSessionRoute -> SleepSessionScreen(onFinished = { popBackStack() })
   - PaywallRoute -> PaywallScreen(onDismiss = { popBackStack() })
   - Home/Journal tabs -> Builder 4's HomeScreen/JournalScreen/JournalEditorScreen
   - MoodCheckInRoute -> bottom-sheet dialog destination (MoodCheckInSheet)
   - InsightsRoute -> wrap with ProGate (Builder 5's BillingGate.kt)
5. Manifest receivers: .notifications.AuraReminderReceiver (exported=false),
   .notifications.AuraBootReceiver (exported=false, BOOT_COMPLETED filter),
   .widget.AuraWidgetReceiver (exported=false, APPWIDGET_UPDATE filter +
   android.appwidget.provider meta-data -> @xml/aura_widget_info).
6. StressReliefApp.onCreate: init RevenueCat if BuildConfig.REVENUECAT_API_KEY
   is not blank (Builder 5 snippet).
7. Call AuraReminderScheduler.scheduleAll() after onboarding completes and when
   reminder/quiet-hour settings change. Request POST_NOTIFICATIONS at runtime.
8. Widget refresh: call AuraWidgetUpdater.refresh() after breathing sessions
   and mood check-ins.
9. Settings paywall entry: paywallLaunchContext.launchFromSettings() before
   navigating to PaywallRoute.
5. Copy 4 signing secrets to repo via GitHub API (series-release.jks is PKCS#12,
   alias `release`, key pw = store pw — see ~/workspace/secrets/series-key.env).
