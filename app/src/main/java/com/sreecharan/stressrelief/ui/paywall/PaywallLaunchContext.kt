package com.sreecharan.stressrelief.ui.paywall

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers how the paywall was launched so it can decide whether to show a
 * close affordance.
 *
 * Contract rule: when launched from a premium feature there is NO free-tier
 * escape hatch on the paywall itself; the user converts or dismisses back
 * (system back). Only launches from Settings show an explicit close button.
 *
 * Set immediately before navigating to PaywallRoute; read by
 * [PaywallViewModel] at creation.
 */
@Singleton
class PaywallLaunchContext
    @Inject
    constructor() {
        @Volatile private var allowClose = true

        fun launchFromSettings() {
            allowClose = true
        }

        fun launchFromPremiumFeature() {
            allowClose = false
        }

        fun isCloseAllowed(): Boolean = allowClose
    }
