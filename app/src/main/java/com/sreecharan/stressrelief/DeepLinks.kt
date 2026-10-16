package com.sreecharan.stressrelief

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Deep links handled by MainActivity (intent filters owned by Builder 1):
 * aura://mood -> MoodCheckInRoute, aura://breathe -> BreatheRoute.
 * Fired by notifications and the Glance widget.
 */
object DeepLinks {
    const val MOOD = "aura://mood"
    const val BREATHE = "aura://breathe"

    fun viewIntent(context: Context, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName)
}
