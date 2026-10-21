package com.suidvck.any.features.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.suidvck.any.MainActivity
import com.suidvck.any.R

/**
 * 4x2 home-screen widget: live clock + date and three shortcuts.
 *
 * The clock and date are [android.widget.TextClock]s, so the widget needs no
 * service or alarm; it only refreshes its PendingIntents on APPWIDGET_UPDATE.
 */
class AnyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, AnyWidgetProvider::class.java)
        for (id in manager.getAppWidgetIds(component)) {
            manager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_any)

        val chat = Intent(context, MainActivity::class.java).apply {
            putExtra("openChat", true)
        }
        val apps = Intent(context, MainActivity::class.java).apply {
            putExtra("openLibrary", true)
        }
        val youtube = Intent(context, MainActivity::class.java).apply {
            putExtra("openUrl", "https://www.youtube.com")
        }

        views.setOnClickPendingIntent(R.id.widget_chat, pendingIntent(context, 5101, chat))
        views.setOnClickPendingIntent(R.id.widget_apps, pendingIntent(context, 5102, apps))
        views.setOnClickPendingIntent(R.id.widget_play, pendingIntent(context, 5103, youtube))

        views.setContentDescription(R.id.widget_chat, context.getString(R.string.widget_cd_chat))
        views.setContentDescription(R.id.widget_apps, context.getString(R.string.widget_cd_apps))
        views.setContentDescription(R.id.widget_play, context.getString(R.string.widget_cd_play))

        return views
    }

    private fun pendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
