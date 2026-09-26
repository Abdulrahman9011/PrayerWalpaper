package com.rahmo.prayerwallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/** الودجة السابعة "المخصّص" — تعرض تصميم المستخدم الحر (سحب العناصر بمكانها) المحفوظ بـ CustomWidgetStore. */
class PrayerWidgetCustomProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateIds(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions)
        updateIds(context, mgr, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            PrayerWidgetHub.ACTION_REFRESH, PrayerWidgetHub.ACTION_TICK -> {
                PrayerData.recompute(context)
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, PrayerWidgetCustomProvider::class.java))
                updateIds(context, mgr, ids)
            }
        }
    }

    fun updateIds(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val design = CustomWidgetStore.load(context)
        for (id in ids) {
            val (wPx, hPx) = WidgetRenderer.currentSizePx(context, mgr, id, design.widthDp, design.heightDp)
            val bmp = CustomWidgetRenderer.render(context, design, wPx, hPx)
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_custom)
            views.setImageViewBitmap(R.id.widget_custom_image, bmp)
            val openAppPending = PendingIntent.getActivity(
                context, 4350, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_custom_image, openAppPending)
            mgr.updateAppWidget(id, views)
        }
    }
}
