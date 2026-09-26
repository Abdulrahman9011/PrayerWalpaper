package com.rahmo.prayerwallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** نقطة مركزية تحدّث كل أنواع الودجات السبعة معاً وتجدول التحديث القادم. */
object PrayerWidgetHub {

    const val ACTION_REFRESH = "com.rahmo.prayerwallpaper.ACTION_REFRESH"
    const val ACTION_TICK = "com.rahmo.prayerwallpaper.ACTION_TICK"
    private const val REQ_TICK = 4210

    private val providerClasses = listOf(
        PrayerWidgetProvider::class.java,
        PrayerWidgetFullProvider::class.java,
        PrayerWidgetCompactProvider::class.java,
        PrayerWidgetCircleProvider::class.java,
        PrayerWidgetListProvider::class.java,
        PrayerWidgetMinimalProvider::class.java,
        PrayerWidgetCustomProvider::class.java
    )

    fun updateAllWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        for (cls in providerClasses) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) continue
            when (cls) {
                PrayerWidgetProvider::class.java -> PrayerWidgetProvider().updateIds(context, mgr, ids)
                PrayerWidgetFullProvider::class.java -> PrayerWidgetFullProvider().updateIds(context, mgr, ids)
                PrayerWidgetCompactProvider::class.java -> PrayerWidgetCompactProvider().updateIds(context, mgr, ids)
                PrayerWidgetCircleProvider::class.java -> PrayerWidgetCircleProvider().updateIds(context, mgr, ids)
                PrayerWidgetListProvider::class.java -> PrayerWidgetListProvider().updateIds(context, mgr, ids)
                PrayerWidgetMinimalProvider::class.java -> PrayerWidgetMinimalProvider().updateIds(context, mgr, ids)
                PrayerWidgetCustomProvider::class.java -> PrayerWidgetCustomProvider().updateIds(context, mgr, ids)
            }
        }
        scheduleNextTick(context)
    }

    fun requestUpdate(context: Context) {
        var any = false
        val mgr = AppWidgetManager.getInstance(context)
        for (cls in providerClasses) {
            if (mgr.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()) { any = true; break }
        }
        if (any) updateAllWidgets(context)
    }

    fun scheduleNextTick(context: Context) {
        val day = PrayerData.cached(context)
        val next = PrayerData.next(day, System.currentTimeMillis())
        val intent = Intent(context, PrayerWidgetProvider::class.java).setAction(ACTION_TICK)
        val pending = PendingIntent.getBroadcast(
            context, REQ_TICK, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis + 500L, pending)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, next.atMillis + 500L, pending)
        }
    }
}
