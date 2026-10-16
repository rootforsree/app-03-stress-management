package com.sreecharan.stressrelief.ui.paywall

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.billing.BillingPlan
import com.sreecharan.stressrelief.data.billing.BillingRepository
import com.sreecharan.stressrelief.data.billing.EntitlementState
import com.sreecharan.stressrelief.data.billing.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface PaywallUiState {
    data object Loading : PaywallUiState

    data class Content(
        /** Null while offerings load. */
        val plans: List<BillingPlan>? = null,
        val selectedPlanId: String? = null,
        val isPurchasing: Boolean = false,
        val errorMessage: String? = null,
        val isRestoring: Boolean = false,
        /** String resource for the restore result, or null. */
        val restoreMessageRes: Int? = null,
        val billingUnavailable: Boolean = false,
        /** False when launched from a premium feature: no close button. */
        val allowClose: Boolean = true,
    ) : PaywallUiState
}

sealed interface PaywallEvent {
    /** The user is premium now (purchase or restore): close the paywall. */
    data object Dismiss : PaywallEvent
}

/**
 * Freemium paywall. Annual is shown first and preselected, then lifetime,
 * then monthly; subscriptions carry a 3-day trial. When billing is not
 * configured the screen degrades to an "unavailable" card and the free tier
 * keeps working.
 */
@HiltViewModel
class PaywallViewModel
    @Inject
    constructor(
        private val billing: BillingRepository,
        launchContext: PaywallLaunchContext,
    ) : ViewModel() {
        private val allowClose = launchContext.isCloseAllowed()
        private var billingUnavailable = false

        private val _uiState = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading)
        val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

        private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        /**
         * Applies the transform to the Content branch, creating it from
         * Loading when a collector fires before the first plans emission.
         */
        private fun updateContent(transform: (PaywallUiState.Content) -> PaywallUiState.Content) {
            val current = _uiState.value as? PaywallUiState.Content
            _uiState.value =
                transform(
                    current
                        ?: PaywallUiState.Content(
                            allowClose = allowClose,
                            billingUnavailable = billingUnavailable,
                        ),
                )
        }

        init {
            viewModelScope.launch {
                billing.entitlement.collect { state ->
                    when (state) {
                        is EntitlementState.Ready -> {
                            if (state.isPremium) {
                                _events.send(PaywallEvent.Dismiss)
                            }
                        }

                        EntitlementState.Unavailable -> {
                            billingUnavailable = true
                            updateContent { it.copy(billingUnavailable = true) }
                        }

                        EntitlementState.Loading -> {
                            Unit
                        }
                    }
                }
            }
            viewModelScope.launch {
                billing.plans.collect { plans ->
                    updateContent { state ->
                        val selected =
                            state.selectedPlanId
                                ?: plans
                                    ?.firstOrNull { it.isAnnual }
                                    ?.packageId
                                ?: plans
                                    ?.firstOrNull { it.isLifetime }
                                    ?.packageId
                                ?: plans?.firstOrNull()?.packageId
                        state.copy(plans = plans, selectedPlanId = selected)
                    }
                }
            }
            viewModelScope.launch { billing.refresh() }
        }

        fun onPlanSelected(planId: String) {
            updateContent {
                it.copy(selectedPlanId = planId, errorMessage = null)
            }
        }

        fun onPurchaseClicked(activity: Activity) {
            val content = _uiState.value as? PaywallUiState.Content ?: return
            val plan =
                content.plans?.firstOrNull {
                    it.packageId == content.selectedPlanId
                } ?: return
            if (content.isPurchasing) return
            _uiState.value = content.copy(isPurchasing = true, errorMessage = null)
            viewModelScope.launch {
                when (val result = billing.purchase(activity, plan)) {
                    is PurchaseResult.Success -> {
                        // Dismiss fires via the entitlement listener (the single
                        // sender); just release the button here.
                        updateContent { it.copy(isPurchasing = false) }
                    }

                    PurchaseResult.Cancelled -> {
                        // Normal: user dismissed the Play sheet. Stay on paywall.
                        updateContent { it.copy(isPurchasing = false) }
                    }

                    is PurchaseResult.Failed -> {
                        updateContent {
                            it.copy(
                                isPurchasing = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }

        fun onRestoreClicked() {
            val content = _uiState.value as? PaywallUiState.Content ?: return
            if (content.isRestoring) return
            updateContent { it.copy(isRestoring = true, restoreMessageRes = null) }
            viewModelScope.launch {
                val found = billing.restore()
                updateContent {
                    it.copy(
                        isRestoring = false,
                        restoreMessageRes =
                            if (found) {
                                null // Dismiss fires via entitlement listener.
                            } else {
                                R.string.paywall_restore_none
                            },
                    )
                }
            }
        }

        fun onRetryPlans() {
            viewModelScope.launch { billing.refresh() }
        }

        fun onErrorDismissed() {
            updateContent { it.copy(errorMessage = null, restoreMessageRes = null) }
        }
    }
