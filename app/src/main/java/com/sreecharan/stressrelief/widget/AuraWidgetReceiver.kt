package com.sreecharan.stressrelief.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Host entry point for the Aura home-screen widget. Declared in the manifest. */
class AuraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraWidget()
}
