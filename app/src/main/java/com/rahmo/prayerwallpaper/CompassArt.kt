package com.rahmo.prayerwallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun norm180(a: Float): Float {
    var x = a % 360f
    if (x > 180f) x -= 360f
    if (x < -180f) x += 360f
    return x
}

/**
 * رسم البوصلة بأربعة أشكال مختلفة — تُستخدم بالخلفية الحيّة (تدور لحظياً مع الهاتف)
 * وبالودجات (صورة ثابتة تُحسب بالنسبة للشمال الحقيقي).
 */
object CompassArt {

    private val path = Path()

    /** بوصلة حيّة تدور مع حركة الهاتف الفعلية — تُستخدم بالخلفية الحيّة فقط. */
    fun drawLive(
        c: Canvas, shape: Settings.CompassShape, pal: Theme.Palette, u: Float,
        cx: Float, cy: Float, r: Float, heading: Float, hasHeading: Boolean, qibla: Float,
        boldFace: Typeface, sQibla: String, sNoSensor: String
    ) {
        val hdg = if (hasHeading) heading else 0f
        val delta = norm180(qibla - hdg)
        val aligned = hasHeading && abs(delta) < 4f
        val needleColor = if (aligned) pal.ok else pal.accent
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = boldFace }

        when (shape) {
            Settings.CompassShape.CLASSIC -> drawClassicDial(c, p, tp, pal, u, cx, cy, r, hdg, delta, needleColor)
            Settings.CompassShape.MODERN_RING -> drawRingDial(c, p, tp, pal, u, cx, cy, r, hdg, delta, needleColor)
            Settings.CompassShape.MINIMAL_ARROW -> drawMinimalDial(c, p, tp, pal, u, cx, cy, r, hdg, delta, needleColor)
            Settings.CompassShape.ARC_GAUGE -> drawArcDial(c, p, tp, pal, u, cx, cy, r, hdg, delta, needleColor)
            Settings.CompassShape.TRIANGLE_POINTER -> drawMinimalDial(c, p, tp, pal, u, cx, cy, r, hdg, delta, needleColor)
        }

        tp.textSize = 34f * u
        tp.color = if (aligned) pal.ok else pal.text
        c.drawText("$sQibla  ${qibla.roundToInt()}°", cx, cy + r + 62f * u, tp)
        if (!hasHeading) {
            tp.textSize = 24f * u
            tp.color = pal.dim
            c.drawText(sNoSensor, cx, cy + r + 102f * u, tp)
        }
    }

    private fun drawClassicDial(c: Canvas, p: Paint, tp: Paint, pal: Theme.Palette, u: Float, cx: Float, cy: Float, r: Float, hdg: Float, delta: Float, needleColor: Int) {
        fill(p, pal.bgAlt); c.drawCircle(cx, cy, r, p)
        stroke(p, pal.border, 3f * u); c.drawCircle(cx, cy, r, p)

        c.save(); c.rotate(-hdg, cx, cy)
        for (i in 0 until 24) {
            c.save(); c.rotate(i * 15f, cx, cy)
            val cardinal = i % 6 == 0
            val len = if (cardinal) 26f * u else 13f * u
            stroke(p, if (cardinal) pal.accent else pal.dimSoft, if (cardinal) 3.5f * u else 2f * u)
            c.drawLine(cx, cy - r + 8f * u, cx, cy - r + 8f * u + len, p)
            c.restore()
        }
        tp.textSize = 30f * u; tp.color = pal.accent
        c.drawText("N", cx, cy - 138f * u, tp)
        c.restore()

        c.save(); c.rotate(delta, cx, cy)
        path.reset(); path.moveTo(cx, cy - 105f * u); path.lineTo(cx - 17f * u, cy); path.lineTo(cx + 17f * u, cy); path.close()
        fill(p, needleColor); c.drawPath(path, p)
        path.reset(); path.moveTo(cx - 17f * u, cy); path.lineTo(cx + 17f * u, cy); path.lineTo(cx, cy + 52f * u); path.close()
        fill(p, pal.dimSoft); c.drawPath(path, p)

        val ky = cy - 122f * u
        val kr = RectF(cx - 13f * u, ky - 13f * u, cx + 13f * u, ky + 13f * u)
        fill(p, Color.BLACK); c.drawRoundRect(kr, 4f * u, 4f * u, p)
        stroke(p, needleColor, 2.5f * u); c.drawRoundRect(kr, 4f * u, 4f * u, p)
        stroke(p, pal.accent, 3f * u); c.drawLine(kr.left, ky - 4f * u, kr.right, ky - 4f * u, p)
        c.restore()

        fill(p, pal.bg); c.drawCircle(cx, cy, 9f * u, p)
        stroke(p, needleColor, 3f * u); c.drawCircle(cx, cy, 9f * u, p)
    }

    private fun drawRingDial(c: Canvas, p: Paint, tp: Paint, pal: Theme.Palette, u: Float, cx: Float, cy: Float, r: Float, hdg: Float, delta: Float, needleColor: Int) {
        fill(p, pal.bgAlt); c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 18f * u; p.color = pal.border
        c.drawCircle(cx, cy, r - 12f * u, p)

        c.save(); c.rotate(-hdg, cx, cy)
        for (i in 0 until 4) {
            c.save(); c.rotate(i * 90f, cx, cy)
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            p.style = Paint.Style.STROKE; p.strokeWidth = 18f * u; p.strokeCap = Paint.Cap.BUTT; p.color = pal.accent
            c.drawArc(rect, -12f, 24f, false, p)
            c.restore()
        }
        tp.textSize = 26f * u; tp.color = pal.accent
        c.drawText("N", cx, cy - r + 30f * u, tp)
        c.restore()

        c.save(); c.rotate(delta, cx, cy)
        fill(p, needleColor)
        path.reset(); path.moveTo(cx, cy - r + 26f * u); path.lineTo(cx - 10f * u, cy - r + 50f * u); path.lineTo(cx + 10f * u, cy - r + 50f * u); path.close()
        c.drawPath(path, p)
        c.restore()

        fill(p, pal.bg); c.drawCircle(cx, cy, 22f * u, p)
        stroke(p, needleColor, 4f * u); c.drawCircle(cx, cy, 22f * u, p)
    }

    private fun drawMinimalDial(c: Canvas, p: Paint, tp: Paint, pal: Theme.Palette, u: Float, cx: Float, cy: Float, r: Float, hdg: Float, delta: Float, needleColor: Int) {
        stroke(p, pal.dimSoft, 1.5f * u); c.drawCircle(cx, cy, r, p)

        c.save(); c.rotate(-hdg, cx, cy)
        tp.textSize = 22f * u; tp.color = pal.dim
        c.drawText("N", cx, cy - r + 26f * u, tp)
        c.restore()

        c.save(); c.rotate(delta, cx, cy)
        stroke(p, needleColor, 5f * u)
        c.drawLine(cx, cy + r * 0.7f, cx, cy - r * 0.7f, p)
        path.reset(); path.moveTo(cx, cy - r * 0.85f); path.lineTo(cx - 12f * u, cy - r * 0.55f); path.lineTo(cx + 12f * u, cy - r * 0.55f); path.close()
        fill(p, needleColor); c.drawPath(path, p)
        c.restore()

        fill(p, needleColor); c.drawCircle(cx, cy, 6f * u, p)
    }

    private fun drawArcDial(c: Canvas, p: Paint, tp: Paint, pal: Theme.Palette, u: Float, cx: Float, cy: Float, r: Float, hdg: Float, delta: Float, needleColor: Int) {
        fill(p, pal.bgAlt); c.drawCircle(cx, cy, r, p)
        val rect = RectF(cx - r + 14f * u, cy - r + 14f * u, cx + r - 14f * u, cy + r - 14f * u)
        p.style = Paint.Style.STROKE; p.strokeWidth = 14f * u; p.strokeCap = Paint.Cap.ROUND
        p.color = pal.border
        c.drawArc(rect, 0f, 360f, false, p)

        // قوس أخضر عند منطقة المحاذاة (± 4 درجات حول القبلة، ثابت بالنسبة لدوران الهاتف)
        c.save(); c.rotate(-hdg, cx, cy)
        p.color = pal.ok
        c.drawArc(rect, qiblaArcStart(delta, hdg), 8f, false, p)
        c.restore()

        c.save(); c.rotate(delta, cx, cy)
        fill(p, needleColor)
        path.reset(); path.moveTo(cx, cy - r + 26f * u); path.lineTo(cx - 14f * u, cy); path.lineTo(cx, cy - 10f * u); path.lineTo(cx + 14f * u, cy); path.close()
        c.drawPath(path, p)
        c.restore()

        tp.textSize = 24f * u; tp.color = pal.dim
        c.drawText("N", cx, cy - r + 34f * u, tp)
        fill(p, pal.bg); c.drawCircle(cx, cy, 10f * u, p)
        stroke(p, needleColor, 3f * u); c.drawCircle(cx, cy, 10f * u, p)
    }

    private fun qiblaArcStart(delta: Float, hdg: Float): Float {
        // زاوية بداية القوس الأخضر بالنسبة لأعلى (-90 = الأعلى بمقياس drawArc)
        return -90f + (hdg + delta) - 4f
    }

    /** صورة ثابتة (بدون حساس حي) للودجات — سهم بالنسبة للشمال الحقيقي، N دائماً للأعلى. كل شكل له مظهر مختلف فعلياً. */
    fun staticBitmap(shape: Settings.CompassShape, pal: Theme.Palette, bearing: Float, sizePx: Int = 220): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = sizePx / 2f - 10f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        val rad = Math.toRadians(bearing.toDouble())

        fun arrowPath(tipR: Float, baseR: Float, spreadDeg: Double): Path {
            val tipX = cx + r * tipR * sin(rad).toFloat()
            val tipY = cy - r * tipR * cos(rad).toFloat()
            val b1x = cx + r * baseR * sin(rad + Math.PI * spreadDeg).toFloat()
            val b1y = cy - r * baseR * cos(rad + Math.PI * spreadDeg).toFloat()
            val b2x = cx + r * baseR * sin(rad - Math.PI * spreadDeg).toFloat()
            val b2y = cy - r * baseR * cos(rad - Math.PI * spreadDeg).toFloat()
            return Path().apply { moveTo(tipX, tipY); lineTo(b1x, b1y); lineTo(b2x, b2y); close() }
        }

        when (shape) {
            Settings.CompassShape.CLASSIC -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = pal.border
                c.drawCircle(cx, cy, r, p)
                tp.textSize = 20f; tp.color = pal.accent
                c.drawText("N", cx, cy - r + 22f, tp)
                p.style = Paint.Style.FILL; p.color = pal.accent
                c.drawPath(arrowPath(0.72f, 0.18f, 0.82), p)
                p.color = pal.bg; c.drawCircle(cx, cy, 8f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = pal.accent
                c.drawCircle(cx, cy, 8f, p)
            }
            Settings.CompassShape.MODERN_RING -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 12f; p.color = pal.border
                c.drawCircle(cx, cy, r - 8f, p)
                tp.textSize = 18f; tp.color = pal.accent
                c.drawText("N", cx, cy - r + 26f, tp)
                p.style = Paint.Style.FILL; p.color = pal.accent
                c.drawPath(arrowPath(0.6f, 0.22f, 0.85), p)
                p.color = pal.bg; c.drawCircle(cx, cy, 16f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = pal.accent
                c.drawCircle(cx, cy, 16f, p)
            }
            Settings.CompassShape.MINIMAL_ARROW -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = pal.dimSoft
                c.drawCircle(cx, cy, r, p)
                tp.textSize = 15f; tp.color = pal.dim
                c.drawText("N", cx, cy - r + 18f, tp)
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = pal.accent
                val tailX = cx - r * 0.55f * sin(rad).toFloat()
                val tailY = cy + r * 0.55f * cos(rad).toFloat()
                val tipX = cx + r * 0.55f * sin(rad).toFloat()
                val tipY = cy - r * 0.55f * cos(rad).toFloat()
                c.drawLine(tailX, tailY, tipX, tipY, p)
                p.style = Paint.Style.FILL
                c.drawPath(arrowPath(0.75f, 0.4f, 0.75), p)
            }
            Settings.CompassShape.ARC_GAUGE -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 10f; p.strokeCap = Paint.Cap.ROUND; p.color = pal.border
                val rect = RectF(cx - r + 6f, cy - r + 6f, cx + r - 6f, cy + r - 6f)
                c.drawArc(rect, 0f, 360f, false, p)
                p.color = pal.ok
                c.drawArc(rect, bearing - 94f, 8f, false, p)
                tp.textSize = 16f; tp.color = pal.dim
                c.drawText("N", cx, cy - r + 30f, tp)
                p.style = Paint.Style.FILL; p.color = pal.accent
                c.drawPath(arrowPath(0.55f, 0.16f, 0.85), p)
                p.color = pal.bg; c.drawCircle(cx, cy, 10f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = pal.accent
                c.drawCircle(cx, cy, 10f, p)
            }
            Settings.CompassShape.TRIANGLE_POINTER -> {
                // بدون دائرة إطلاقاً — مثلث عريض جريء يشير للقبلة فقط، أكثر الأشكال بساطة
                tp.textSize = 14f; tp.color = pal.dim
                c.drawText("N", cx, cy - r + 14f, tp)
                p.style = Paint.Style.FILL; p.color = pal.accent
                c.drawPath(arrowPath(0.85f, 0.55f, 0.62), p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = pal.border
                c.drawPath(arrowPath(0.85f, 0.55f, 0.62), p)
            }
        }
        return bmp
    }

    private fun fill(p: Paint, color: Int) { p.style = Paint.Style.FILL; p.color = color }
    private fun stroke(p: Paint, color: Int, width: Float) {
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND
    }
}
