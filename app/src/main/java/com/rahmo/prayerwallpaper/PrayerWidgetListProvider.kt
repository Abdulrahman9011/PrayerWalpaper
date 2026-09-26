package com.rahmo.prayerwallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** تصميم قائمة عمودية: الأوقات الخمسة فقط بشكل واضح وبسيط، بدون بوصلة أو عدّاد. */
class PrayerWidgetListProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateIds(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions)
        updateIds(context, mgr, intArrayOf(id))
    }

    fun updateIds(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val loc = PrayerData.location(context)
        val day = PrayerData.cached(context)
        val next = PrayerData.next(day, System.currentTimeMillis())
        for (id in ids) {
            val (bgW, bgH) = WidgetRenderer.currentSizePx(context, mgr, id, 180, 180)
            val rowsW = (bgW * 0.944f).roundToInt(); val rowsH = (bgH * 0.889f).roundToInt()

            val views = RemoteViews(context.packageName, R.layout.widget_prayer_list)
            views.setTextViewText(R.id.widget_city, loc.city)
            WidgetRenderer.fillBackground(context, views, R.id.widget_bg_image, R.id.widget_root, bgW, bgH)
            WidgetRenderer.fillRows(context, views, day, next, R.id.widget_rows_image, rowsW, rowsH)
            val openAppPending = PendingIntent.getActivity(
                context, 4330, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_city, openAppPending)
            mgr.updateAppWidget(id, views)
        }
    }
}
