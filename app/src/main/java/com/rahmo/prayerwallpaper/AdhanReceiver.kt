package com.rahmo.prayerwallpaper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/** يستقبل تنبيه دخول وقت كل صلاة ويقرر: إشعار عادي أم منبّه مستمر، حسب الإعدادات. */
class AdhanReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADHAN = "com.rahmo.prayerwallpaper.ACTION_ADHAN"
        const val ACTION_ADHAN_REMINDER = "com.rahmo.prayerwallpaper.ACTION_ADHAN_REMINDER"
        const val EXTRA_PRAYER_INDEX = "prayer_index"
        const val CHANNEL_ID = "adhan_channel"
        const val CHANNEL_ID_SILENT = "adhan_channel_silent"
        private const val NOTIF_ID = 7100
        private const val REMINDER_NOTIF_ID = 7200
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ADHAN_REMINDER) {
            if (!Settings.adhanNotifEnabled(context)) return
            val index = intent.getIntExtra(EXTRA_PRAYER_INDEX, 0)
            val names = context.resources.getStringArray(R.array.prayer_names)
            val prayerName = names.getOrElse(index) { "" }
            ensureChannels(context)
            showReminderNotification(context, prayerName, Settings.reminderMinutes(context))
            return
        }
        if (intent.action != ACTION_ADHAN) return
        if (!Settings.adhanNotifEnabled(context)) return

        val index = intent.getIntExtra(EXTRA_PRAYER_INDEX, 0)
        val names = context.resources.getStringArray(R.array.prayer_names)
        val prayerName = names.getOrElse(index) { "" }

        ensureChannels(context)
        vibrateOnce(context)

        if (Settings.persistentAlarm(context)) {
            val svc = Intent(context, AlarmRingService::class.java).apply {
                action = AlarmRingService.ACTION_START
                putExtra(AlarmRingService.EXTRA_PRAYER_NAME, prayerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } else {
            showSimpleNotification(context, prayerName)
            if (Settings.ringtoneEnabled(context)) {
                playOneShot(context)
            }
        }

        // إعادة جدولة كل الصلوات (يشمل اليوم التالي) — بدون إنترنت، حساب فوري
        AdhanScheduler.scheduleAll(context)
        PrayerWidgetHub.updateAllWidgets(context)
    }

    private fun vibrateOnce(context: Context) {
        if (!Settings.vibrationEnabled(context)) return
        val pattern = longArrayOf(0, 500, 250, 500, 250, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }

    private fun playOneShot(context: Context) {
        try {
            val uriStr = Settings.ringtoneUri(context)
            val uri = if (uriStr != null) android.net.Uri.parse(uriStr)
                else android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) { /* تجاهل — الإشعار نفسه يكفي */ }
    }

    private fun showSimpleNotification(context: Context, prayerName: String) {
        val openIntent = PendingIntent.getActivity(
            context, 9001, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channel = if (Settings.ringtoneEnabled(context)) CHANNEL_ID else CHANNEL_ID_SILENT

        // عند توسيع الإشعار (المزيد): كل المواقيت من الفجر للعشاء
        val names = context.resources.getStringArray(R.array.prayer_names)
        val day = PrayerData.cached(context)
        val expandedStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.adhan_title, prayerName))
        for (i in names.indices) {
            val time = day.times.getOrNull(i) ?: continue
            expandedStyle.addLine("${names[i]}   $time")
        }

        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.adhan_title, prayerName))
            .setContentText(context.getString(R.string.adhan_body))
            .setStyle(expandedStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
    }

    /** إشعار هادئ (بدون رنة أو اهتزاز) يظهر قبل دخول وقت الصلاة بعدد الدقائق المختار بالإعدادات. */
    private fun showReminderNotification(context: Context, prayerName: String, minutesBefore: Int) {
        val openIntent = PendingIntent.getActivity(
            context, 9002, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title, prayerName))
            .setContentText(context.getString(R.string.reminder_body, minutesBefore))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(REMINDER_NOTIF_ID, notif)
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_adhan), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            val ch = NotificationChannel(CHANNEL_ID_SILENT, context.getString(R.string.channel_adhan_silent), NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
    }
}
