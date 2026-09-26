package com.rahmo.prayerwallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** يجدول تنبيه دخول وقت كل صلاة (منبّه دقيق يعمل حتى والهاتف نائم — بدون إنترنت). */
object AdhanScheduler {

    private const val REQ_BASE = 5300
    // مفتاح طلب منفصل تماماً عن مفاتيح الصلوات الخمس (5300..5304) حتى لا تُلغى
    // صلاة فجر اليوم الحالي عن طريق الخطأ عند جدولة فجر الغد بنفس الرقم
    private const val REQ_FAJR_TOMORROW = 5305
    // مفاتيح طلب مستقلة تماماً للتذكير المبكر (5400..5404) — لا تتقاطع مع منبّهات الأذان نفسها
    private const val REQ_REMINDER_BASE = 5400

    fun scheduleAll(context: Context) {
        if (!Settings.adhanNotifEnabled(context)) {
            cancelAll(context)
            return
        }
        val day = PrayerData.cached(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        val reminderMin = Settings.reminderMinutes(context)
        for (i in 0 until 5) {
            val at = timeMillis(day.times[i])
            // للصلوات اللي فاتت اليوم لا نجدولها اليوم — ستُعاد جدولتها غداً تلقائياً
            // بعد أول تحديث (فتح التطبيق، الإقلاع، أو رنين أي صلاة أخرى بنفس اليوم)
            if (at > now) schedule(context, am, i, at)

            if (reminderMin > 0) {
                val reminderAt = at - reminderMin * 60_000L
                if (reminderAt > now) scheduleReminder(context, am, i, reminderAt)
            }
        }
        // نجدول أيضاً فجر الغد صراحة (بمفتاح طلب مستقل) حتى يُحدَّث المنبّه فور دخول
        // اليوم الجديد، دون أن يُلغي هذا فجر اليوم الحالي إن كان لا يزال قادماً
        val pendingFajr = Intent(context, AdhanReceiver::class.java).apply {
            action = AdhanReceiver.ACTION_ADHAN
            putExtra(AdhanReceiver.EXTRA_PRAYER_INDEX, 0)
        }
        val fajrTomorrowPending = PendingIntent.getBroadcast(
            context, REQ_FAJR_TOMORROW, pendingFajr,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fajrTomorrowAt = timeMillis(day.fajrTomorrow, plusDays = 1)
        scheduleAsSystemAlarm(context, am, REQ_FAJR_TOMORROW, fajrTomorrowAt, fajrTomorrowPending)
    }

    private fun schedule(context: Context, am: AlarmManager, prayerIndex: Int, atMillis: Long) {
        val intent = Intent(context, AdhanReceiver::class.java).apply {
            action = AdhanReceiver.ACTION_ADHAN
            putExtra(AdhanReceiver.EXTRA_PRAYER_INDEX, prayerIndex)
        }
        val pending = PendingIntent.getBroadcast(
            context, REQ_BASE + prayerIndex, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAsSystemAlarm(context, am, REQ_BASE + prayerIndex, atMillis, pending)
    }

    /**
     * يسجّل المنبّه كـ "منبّه حقيقي" لدى نظام أندرويد (نفس آلية منبّه الساعة الافتراضي)،
     * فيظهر بأيقونة المنبّه بشريط الحالة، ويصير مرئياً لأي تطبيق أو شاشة قفل تعرض "المنبّه القادم" —
     * مو بس جوّا تطبيقنا. لو الجهاز ما يدعم هذا (نادر جداً)، نرجع تلقائياً لمنبّه دقيق عادي.
     */
    private fun scheduleAsSystemAlarm(context: Context, am: AlarmManager, requestCode: Int, atMillis: Long, operation: PendingIntent) {
        val showIntent = PendingIntent.getActivity(
            context, 9500 + requestCode, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showIntent), operation)
        } catch (e: SecurityException) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            } catch (e2: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, atMillis, operation)
            }
        }
    }

    private fun scheduleReminder(context: Context, am: AlarmManager, prayerIndex: Int, atMillis: Long) {
        val intent = Intent(context, AdhanReceiver::class.java).apply {
            action = AdhanReceiver.ACTION_ADHAN_REMINDER
            putExtra(AdhanReceiver.EXTRA_PRAYER_INDEX, prayerIndex)
        }
        val pending = PendingIntent.getBroadcast(
            context, REQ_REMINDER_BASE + prayerIndex, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until 5) {
            val intent = Intent(context, AdhanReceiver::class.java).apply { action = AdhanReceiver.ACTION_ADHAN }
            val pending = PendingIntent.getBroadcast(
                context, REQ_BASE + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pending)
            val reminderIntent = Intent(context, AdhanReceiver::class.java).apply { action = AdhanReceiver.ACTION_ADHAN_REMINDER }
            val reminderPending = PendingIntent.getBroadcast(
                context, REQ_REMINDER_BASE + i, reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(reminderPending)
        }
        val fajrTomorrow = Intent(context, AdhanReceiver::class.java).apply { action = AdhanReceiver.ACTION_ADHAN }
        val fajrTomorrowPending = PendingIntent.getBroadcast(
            context, REQ_FAJR_TOMORROW, fajrTomorrow,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(fajrTomorrowPending)
    }

    private fun timeMillis(hhmm: String, plusDays: Int = 0): Long {
        val parts = hhmm.split(":")
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, plusDays)
        cal.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
        cal.set(java.util.Calendar.MINUTE, parts[1].toInt())
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
