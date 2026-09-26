package com.rahmo.prayerwallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/** تصميم أدنى حد: رقم العدّاد فقط بخط كبير جداً — لمن يريد فقط "كم باقي". */
class PrayerWidgetMinimalProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateIds(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions)
        updateIds(context, mgr, intArrayOf(id))
    }

    fun updateIds(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val day = PrayerData.cached(context)
        val next = PrayerData.next(day, System.currentTimeMillis())
        for (id in ids) {
            val (bgW, bgH) = WidgetRenderer.currentSizePx(context, mgr, id, 90, 60)
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_minimal)
            WidgetRenderer.fillBackground(context, views, R.id.widget_bg_image, R.id.widget_root, bgW, bgH)
            WidgetRenderer.fillCountdown(context, views, next, R.id.widget_next_chrome_image, bgW, bgH, R.id.widget_countdown)
            val openAppPending = PendingIntent.getActivity(
                context, 4340, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown, openAppPending)
            mgr.updateAppWidget(id, views)
        }
    }
}
