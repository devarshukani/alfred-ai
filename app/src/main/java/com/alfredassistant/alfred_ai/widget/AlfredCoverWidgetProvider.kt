package com.alfredassistant.alfred_ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.alfredassistant.alfred_ai.R

/**
 * AppWidgetProvider for the Alfred Flex Window cover screen widget.
 * Tapping the widget directly launches Alfred in listening mode on the cover screen.
 */
class AlfredCoverWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_cover_wave)

            val intent = Intent(context, CoverWaveActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
