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

    /**
     * Glance's actionStartActivity cannot carry an Intent data URI, so the
     * widget passes the deep link as an action parameter; Glance delivers it
     * as an intent extra keyed by this name. MainActivity rehydrates it into
     * [Intent.data] before handing the intent to NavController.
     */
    const val EXTRA_DEEP_LINK = "aura_deep_link"

    fun viewIntent(
        context: Context,
        uri: String,
    ): Intent = Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName)
}
