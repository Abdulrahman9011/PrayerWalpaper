package com.rahmo.prayerwallpaper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * خدمة المنبّه: صوت (إن كانت الرنة مفعّلة) + اهتزاز متكرر.
 * - إذا "استمرار المنبه حتى أوقفه بنفسي" مفعّل: يستمر بحلقة كاملة بدون أي إيقاف تلقائي.
 * - غير هيك: إذا مدة الرنة أقل من 5 دقايق تُعاد بحلقة حتى تكتمل 5 دقايق ثم يتوقف تلقائياً؛
 *   الإيقاف اليدوي (من شاشة المنبّه أو الإشعار) يشتغل بأي لحظة بكل الحالات.
 */
class AlarmRingService : Service() {

    companion object {
        const val ACTION_START = "com.rahmo.prayerwallpaper.ALARM_START"
        const val ACTION_STOP = "com.rahmo.prayerwallpaper.ALARM_STOP"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val CHANNEL_ID = "adhan_alarm_channel"
        const val NOTIF_ID = 7200
        private const val MAX_RING_MS = 5 * 60 * 1000L
        @Volatile var isRinging = false
            private set
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var prayerName: String = ""
    private val autoStopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRinging(); return START_NOT_STICKY }
            else -> {
                prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: prayerName
                startRinging()
            }
        }
        return START_STICKY
    }

    private fun startRinging() {
        if (isRinging) return
        isRinging = true
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())

        val persistent = Settings.persistentAlarm(this)

        if (Settings.ringtoneEnabled(this)) {
            try {
                val uriStr = Settings.ringtoneUri(this)
                val uri: Uri = if (uriStr != null) Uri.parse(uriStr)
                    else RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmRingService, uri)
                    prepare()
                    // "استمرار حتى أوقفه بنفسي": حلقة دائمة. غير هيك: حلقة فقط إذا الرنة أقصر من 5 دقايق
                    // (لتكمل لغاية 5 دقايق)، أو تشتغل مرة وحدة إذا كانت أطول أصلاً.
                    isLooping = persistent || (duration in 1 until MAX_RING_MS.toInt())
                    start()
                }
            } catch (e: Exception) { /* تجاهل الصوت، الاهتزاز يستمر */ }
        }

        // سقف 5 دقايق (صوت + اهتزاز معاً) إلا إذا "استمرار حتى أوقفه بنفسي" مفعّل — عندها بدون أي سقف
        if (!persistent) {
            autoStopHandler.postDelayed(autoStopRunnable, MAX_RING_MS)
        }

        if (Settings.vibrationEnabled(this)) {
            val pattern = longArrayOf(0, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vm.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        }

        val full = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmActivity.EXTRA_PRAYER_NAME, prayerName)
        }
        startActivity(full)
    }

    /** الطريقة الوحيدة لإيقاف المنبّه — يدوياً من المستخدم، أو تلقائياً بعد 5 دقايق (إن لم يكن الاستمرار الدائم مفعّلاً). */
    private fun stopRinging() {
        if (!isRinging) return
        isRinging = false
        autoStopHandler.removeCallbacks(autoStopRunnable)
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        vibrator?.cancel()
        vibrator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 9300, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_PRAYER_NAME, prayerName)
        }
        val openPending = PendingIntent.getActivity(
            this, 9301, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.adhan_title, prayerName))
            .setContentText(getString(R.string.alarm_ongoing_body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(openPending, true)
            .addAction(0, getString(R.string.alarm_stop), stopPending)
            .setContentIntent(openPending)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_adhan_alarm), NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null) // الصوت يُدار يدوياً عبر MediaPlayer حتى يستمر بحلقة كاملة
            nm.createNotificationChannel(ch)
        }
    }
}
