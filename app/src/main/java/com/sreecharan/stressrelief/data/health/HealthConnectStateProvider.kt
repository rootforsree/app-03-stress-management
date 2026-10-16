package com.sreecharan.stressrelief.data.health

import androidx.activity.result.contract.ActivityResultContract

/**
 * The Health Connect surface the UI layer is allowed to touch: availability
 * and permission state plus the permission-request contract. Implemented by
 * [HealthConnectClient]; the raw read API stays behind the repository.
 */
interface HealthConnectStateProvider {
    suspend fun isAvailable(): Boolean

    suspend fun hasAllPermissions(): Boolean

    /** Permissions the app needs for stress signals; request via [permissionContract]. */
    fun requiredPermissions(): Set<String>

    /** Contract for the Health Connect permission flow; register it in an Activity. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>
}
