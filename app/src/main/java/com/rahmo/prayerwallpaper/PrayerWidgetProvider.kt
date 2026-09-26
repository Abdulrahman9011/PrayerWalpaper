package com.rahmo.prayerwallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** التصميم الكلاسيكي: ٥ أوقات على اليسار + عدّاد وبوصلة على اليمين. */
open class PrayerWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val REQ_CODE = 4200
    }

    open val layoutRes: Int get() = R.layout.widget_prayer
    open val fallbackWdp: Int get() = 250
    open val fallbackHdp: Int get() = 140

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateIds(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions)
        updateIds(context, mgr, intArrayOf(id)) // إعادة رسم بالحجم الجديد الفعلي فور تكبير/تصغير الودجة يدوياً
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            PrayerWidgetHub.ACTION_REFRESH, PrayerWidgetHub.ACTION_TICK -> {
                PrayerData.recompute(context)
                PrayerWidgetHub.updateAllWidgets(context)
            }
        }
    }

    open fun updateIds(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val loc = PrayerData.location(context)
        val day = PrayerData.cached(context)
        val next = PrayerData.next(day, System.currentTimeMillis())
        for (id in ids) {
            val (bgW, bgH) = WidgetRenderer.currentSizePx(context, mgr, id, fallbackWdp, fallbackHdp)
            val rowsW = (bgW * 0.667f).roundToInt(); val rowsH = (bgH * 0.733f).roundToInt()
            val chromeW = (bgW * 0.625f).roundToInt(); val chromeH = (bgH * 0.633f).roundToInt()
            val qiblaPx = (bgW * 0.2f).roundToInt().coerceAtLeast(50)

            val views = RemoteViews(context.packageName, layoutRes)
            views.setTextViewText(R.id.widget_city, loc.city)
            WidgetRenderer.fillBackground(context, views, R.id.widget_bg_image, R.id.widget_root, bgW, bgH)
            WidgetRenderer.fillRows(context, views, day, next, R.id.widget_rows_image, rowsW, rowsH)
            WidgetRenderer.fillCountdown(context, views, next, R.id.widget_next_chrome_image, chromeW, chromeH, R.id.widget_countdown)
            WidgetRenderer.fillQibla(context, views, loc, R.id.widget_qibla_image, R.id.widget_qibla_text, qiblaPx)

            val refreshPending = PendingIntent.getBroadcast(
                context, REQ_CODE, Intent(context, PrayerWidgetProvider::class.java).setAction(PrayerWidgetHub.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            val openAppPending = PendingIntent.getActivity(
                context, REQ_CODE + 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown, openAppPending)

            mgr.updateAppWidget(id, views)
        }
    }
}
