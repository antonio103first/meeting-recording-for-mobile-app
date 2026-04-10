package com.krunventures.meetingrecorder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.krunventures.meetingrecorder.R
import com.krunventures.meetingrecorder.MainActivity

class RecordingWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_QUICK_RECORD = "com.krunventures.meetingrecorder.QUICK_RECORD"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_QUICK_RECORD) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("quick_record", true)
            }
            context.startActivity(launchIntent)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_recording)

        // Quick record button
        val recordIntent = Intent(context, RecordingWidget::class.java).apply {
            action = ACTION_QUICK_RECORD
        }
        val recordPending = PendingIntent.getBroadcast(
            context, 0, recordIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_record, recordPending)

        // Open app button
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_open, openPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
