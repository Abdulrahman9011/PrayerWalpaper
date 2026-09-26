package com.rahmo.prayerwallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.TextPaint
import android.text.TextUtils
import android.view.SurfaceHolder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PrayerWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PrayerEngine()

    inner class PrayerEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private var sensorManager: SensorManager? = null
        private var rotationSensor: Sensor? = null
        private var visible = false
        private var w = 0
        private var h = 0

        private var loc: PrayerData.Loc = PrayerData.location(applicationContext)
        private var day: PrayerData.Day? = null
        private var qibla = 0f
        private var declination = 0f
        private var lastCheck = 0L

        private var heading = 0f
        private var hasHeading = false
        private val rot = FloatArray(9)
        private val remapped = FloatArray(9)
        private val ori = FloatArray(3)

        private val sNames get() = resources.getStringArray(R.array.prayer_names)
        private val sTitle get() = getString(R.string.prayer_times)
        private val sRemaining get() = getString(R.string.remaining)
        private val sQibla get() = getString(R.string.qibla)
        private val sLoading get() = getString(R.string.loading)
        private val sNoSensor get() = getString(R.string.no_sensor)

        private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cityPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint()

        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) {
                    val interval = if (rotationSensor != null) Settings.compassSpeed(applicationContext).ms else 1000L
                    handler.postDelayed(this, interval)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            reloadData()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            val pal = Theme.of(Settings.WALLPAPER_THEME)
            val innerGlow = (0x14 shl 24) or (pal.accent and 0x00FFFFFF)
            val outerGlow = pal.accent and 0x00FFFFFF
            glow.shader = RadialGradient(
                width / 2f, 0f, height * 0.6f, innerGlow, outerGlow, Shader.TileMode.CLAMP
            )
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (isVisible) {
                reloadData()
                lastCheck = 0L
                rotationSensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                handler.post(frame)
            } else {
                sensorManager?.unregisterListener(this)
            }
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            sensorManager?.unregisterListener(this)
            super.onDestroy()
        }

        private fun reloadData() {
            val ctx = applicationContext
            loc = PrayerData.location(ctx)
            day = PrayerData.cached(ctx)
            qibla = PrayerData.qiblaBearing(loc.lat, loc.lon).toFloat()
            declination = GeomagneticField(
                loc.lat.toFloat(), loc.lon.toFloat(), 0f, System.currentTimeMillis()
            ).declination
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rot, event.values)
            val m = if (abs(rot[8]) > 0.6f) {
                rot
            } else {
                SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                remapped
            }
            SensorManager.getOrientation(m, ori)
            var az = Math.toDegrees(ori[0].toDouble()).toFloat() + declination
            az = ((az % 360f) + 360f) % 360f
            if (!hasHeading) {
                heading = az; hasHeading = true
            } else {
                val d = az - heading
                var nd = d % 360f
                if (nd > 180f) nd -= 360f
                if (nd < -180f) nd += 360f
                heading = (heading + nd * 0.25f + 360f) % 360f
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun draw() {
            if (w == 0 || h == 0) return
            val now = System.currentTimeMillis()
            if (now - lastCheck > 60_000L) {
                lastCheck = now
                reloadData()
                AdhanScheduler.scheduleAll(applicationContext)
            }
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) render(canvas, now)
            } catch (e: Exception) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
            }
        }

        private fun fill(color: Int): Paint { sp.style = Paint.Style.FILL; sp.color = color; return sp }
        private fun stroke(color: Int, width: Float): Paint {
            sp.style = Paint.Style.STROKE; sp.color = color; sp.strokeWidth = width; sp.strokeCap = Paint.Cap.ROUND; return sp
        }

        private fun text(
            c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
            bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER
        ) {
            val font = Settings.WALLPAPER_FONT
            tp.textSize = size; tp.color = color
            tp.typeface = if (bold) Theme.typefaceBold(font) else Theme.typeface(font)
            tp.textAlign = align
            tp.fontFeatureSettings = "tnum"
            c.drawText(s, x, y, tp)
        }

        private fun countdown(ms: Long): String {
            val s = max(0L, ms / 1000L)
            return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        }

        private fun render(c: Canvas, now: Long) {
            val wf = w.toFloat()
            val u = wf / 1080f
            val pal = Theme.of(Settings.WALLPAPER_THEME)
            val font = Settings.WALLPAPER_FONT

            c.drawColor(pal.bg)
            c.drawRect(0f, 0f, wf, h.toFloat(), glow)

            val d = day
            val next = d?.let { PrayerData.next(it, now) }
            val names = sNames

            val top = h * 0.12f
            val margin = 48f * u
            val leftL = margin
            val leftR = wf * 0.42f
            val rightL = wf * 0.44f
            val rightR = wf - margin
            val cx = (rightL + rightR) / 2f

            text(c, sTitle, leftR, top + 28f * u, 30f * u, pal.dim, false, Paint.Align.RIGHT)
            cityPaint.textSize = 28f * u
            cityPaint.typeface = Theme.typeface(font)
            val city = TextUtils.ellipsize(loc.city, cityPaint, leftR - leftL, TextUtils.TruncateAt.END)
            text(c, city.toString(), leftR, top + 68f * u, 28f * u, pal.accent, false, Paint.Align.RIGHT)

            val rowH = 116f * u
            val rowsTop = top + 100f * u
            for (i in 0 until 5) {
                val rt = rowsTop + i * rowH
                val isNext = next?.index == i
                val rect = RectF(leftL - 16f * u, rt, leftR + 16f * u, rt + rowH - 16f * u)
                if (isNext) {
                    c.drawRoundRect(rect, 24f * u, 24f * u, fill(pal.accentSoft))
                    c.drawRoundRect(rect, 24f * u, 24f * u, stroke(pal.border, 2f * u))
                }
                val base = rect.centerY() + 15f * u
                val col = if (isNext) pal.accent else pal.text
                text(c, names.getOrElse(i) { "" }, leftR, base, 42f * u, col, isNext, Paint.Align.RIGHT)
                text(c, d?.times?.get(i) ?: "--:--", leftL, base, 44f * u, col, true, Paint.Align.LEFT)
            }

            drawCounter(c, u, cx, top, next, names, now, pal, font)
            CompassArt.drawLive(
                c, Settings.WALLPAPER_COMPASS, pal, u, cx, top + 548f * u, 190f * u,
                heading, hasHeading, qibla, Theme.typefaceBold(font), sQibla, sNoSensor
            )
        }

        private fun drawCounter(
            c: Canvas, u: Float, cx: Float, top: Float, next: PrayerData.Next?,
            names: Array<String>, now: Long, pal: Theme.Palette, font: Settings.FontStyle
        ) {
            val style = Settings.WALLPAPER_COUNTER
            val nextName = if (next != null) names.getOrElse(next.index) { "" } else sLoading
            val remainingText = if (next != null) countdown(next.atMillis - now) else "--:--:--"

            when (style) {
                Settings.CounterStyle.RING_PROGRESS -> {
                    val ringCy = top + 190f * u
                    val ringR = 150f * u
                    val window = 6 * 3600 * 1000f
                    val remainingMs = if (next != null) (next.atMillis - now).coerceIn(0, window.toLong()) else window.toLong()
                    val progress = 1f - (remainingMs / window)
                    val rect = RectF(cx - ringR, ringCy - ringR, cx + ringR, ringCy + ringR)
                    c.drawArc(rect, -90f, 360f, false, stroke(pal.border, 16f * u))
                    c.drawArc(rect, -90f, 360f * progress, false, stroke(pal.accent, 16f * u))
                    text(c, sRemaining, cx, ringCy - 30f * u, 26f * u, pal.dim)
                    text(c, nextName, cx, ringCy + 14f * u, 44f * u, pal.accent, true)
                    text(c, remainingText, cx, ringCy + 66f * u, 46f * u, pal.text, true)
                }
                Settings.CounterStyle.PLAIN_TEXT -> {
                    text(c, sRemaining, cx, top + 30f * u, 30f * u, pal.dim)
                    text(c, nextName, cx, top + 112f * u, 66f * u, pal.accent, true)
                    text(c, remainingText, cx, top + 214f * u, 92f * u, pal.text, true)
                    if (next != null) {
                        text(c, next.time, cx, top + 280f * u, 34f * u, pal.dim, true)
                    }
                }
                Settings.CounterStyle.PILL -> {
                    text(c, sRemaining, cx, top + 30f * u, 30f * u, pal.dim)
                    text(c, nextName, cx, top + 112f * u, 66f * u, pal.accent, true)
                    text(c, remainingText, cx, top + 214f * u, 92f * u, pal.text, true)
                    if (next != null) {
                        tp.textSize = 34f * u; tp.typeface = Theme.typefaceBold(font)
                        val bw = tp.measureText(next.time) + 60f * u
                        val bt = top + 250f * u
                        val br = RectF(cx - bw / 2f, bt, cx + bw / 2f, bt + 58f * u)
                        c.drawRoundRect(br, 29f * u, 29f * u, fill(pal.accentSoft))
                        c.drawRoundRect(br, 29f * u, 29f * u, stroke(pal.border, 2f * u))
                        text(c, next.time, cx, br.centerY() + 12f * u, 34f * u, pal.accent, true)
                    }
                }
                Settings.CounterStyle.DIGITAL_CARD -> {
                    text(c, sRemaining, cx, top + 30f * u, 30f * u, pal.dim)
                    text(c, nextName, cx, top + 112f * u, 66f * u, pal.accent, true)
                    val cardRect = RectF(cx - 210f * u, top + 140f * u, cx + 210f * u, top + 240f * u)
                    c.drawRoundRect(cardRect, 20f * u, 20f * u, fill(pal.bgAlt))
                    c.drawRoundRect(cardRect, 20f * u, 20f * u, stroke(pal.border, 2f * u))
                    text(c, remainingText, cx, cardRect.centerY() + 16f * u, 60f * u, pal.text, true)
                    if (next != null) {
                        text(c, next.time, cx, top + 288f * u, 34f * u, pal.accent, true)
                    }
                }
                Settings.CounterStyle.OUTLINE -> {
                    // نفس تخطيط DIGITAL_CARD لكن بإطار بلا تعبئة (بنفس روح OUTLINE بالودجات في WidgetArt.kt)
                    text(c, sRemaining, cx, top + 30f * u, 30f * u, pal.dim)
                    text(c, nextName, cx, top + 112f * u, 66f * u, pal.accent, true)
                    val cardRect = RectF(cx - 210f * u, top + 140f * u, cx + 210f * u, top + 240f * u)
                    c.drawRoundRect(cardRect, 20f * u, 20f * u, stroke(pal.accent, 3f * u))
                    text(c, remainingText, cx, cardRect.centerY() + 16f * u, 60f * u, pal.text, true)
                    if (next != null) {
                        text(c, next.time, cx, top + 288f * u, 34f * u, pal.accent, true)
                    }
                }
            }
        }
    }
}
