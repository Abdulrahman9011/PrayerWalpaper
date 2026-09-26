package com.rahmo.prayerwallpaper

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.TextUtils

/**
 * كل عناصر الودجة القابلة للتصميم (الصفوف، تسمية الصلاة القادمة، الخلفية المخصّصة)
 * تُرسم كصورة (Bitmap) بنفس دالة الرسم المستخدمة بمعاينة شاشة "تصميم الودجة" —
 * فالمعاينة تُطابق شكل الودجة الحقيقي على الشاشة الرئيسية تماماً.
 */
object WidgetArt {

    /** لوحة الصلوات الخمس — يدعم كل أشكال "المواقيت" الخمسة وكل الخطوط. */
    fun renderRows(
        context: Context, day: PrayerData.Day, next: PrayerData.Next?,
        pal: Theme.Palette, font: Settings.FontStyle, style: Settings.RowStyle,
        w: Int, h: Int, rtl: Boolean = true
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val names = context.resources.getStringArray(R.array.prayer_names)
        val rowH = h / 5f
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Theme.typeface(font); textSize = rowH * 0.34f
            textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Theme.typefaceBold(font); textSize = rowH * 0.36f
            textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = h * 0.006f; color = pal.border }
        val pad = w * 0.04f

        for (i in 0 until 5) {
            val top = i * rowH
            val highlighted = next?.index == i
            val nameX = if (rtl) w - pad else pad
            val timeX = if (rtl) pad else w - pad
            val gapV = when (style) {
                Settings.RowStyle.COMPACT -> rowH * 0.04f
                else -> rowH * 0.12f
            }
            val rowRect = RectF(pad * 0.3f, top + gapV, w - pad * 0.3f, top + rowH - gapV)

            when (style) {
                Settings.RowStyle.CARD -> {
                    bgPaint.style = Paint.Style.FILL
                    bgPaint.color = if (highlighted) pal.accentSoft else 0x00000000
                    c.drawRoundRect(rowRect, rowH * 0.22f, rowH * 0.22f, bgPaint)
                    if (highlighted) {
                        bgPaint.style = Paint.Style.STROKE; bgPaint.strokeWidth = h * 0.006f; bgPaint.color = pal.border
                        c.drawRoundRect(rowRect, rowH * 0.22f, rowH * 0.22f, bgPaint)
                    }
                }
                Settings.RowStyle.BORDERED -> {
                    bgPaint.style = Paint.Style.STROKE; bgPaint.strokeWidth = h * 0.006f
                    bgPaint.color = if (highlighted) pal.accent else pal.dimSoft
                    c.drawRoundRect(rowRect, rowH * 0.16f, rowH * 0.16f, bgPaint)
                }
                Settings.RowStyle.DIVIDED -> {
                    if (i > 0) c.drawLine(pad, top, w - pad, top, linePaint)
                }
                Settings.RowStyle.COMPACT, Settings.RowStyle.MINIMAL -> { /* بدون أي زخرفة */ }
            }

            val baseline = top + rowH / 2f + rowH * 0.12f
            val col = if (highlighted) pal.accent else pal.text
            namePaint.color = col
            timePaint.color = col
            c.drawText(names.getOrElse(i) { "" }, nameX, baseline, namePaint)
            c.drawText(day.times.getOrElse(i) { "--:--" }, timeX, baseline, timePaint)
        }
        return bmp
    }

    /** تسمية الصلاة القادمة + إطار "التوقيت" — الأرقام الحيّة تُرسم فوقها بعنصر Chronometer منفصل. */
    fun renderNextChrome(
        context: Context, next: PrayerData.Next?, pal: Theme.Palette,
        font: Settings.FontStyle, style: Settings.CounterStyle, w: Int, h: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val names = context.resources.getStringArray(R.array.prayer_names)
        val nextName = if (next != null) names.getOrElse(next.index) { "" } else context.getString(R.string.loading)
        val cx = w / 2f

        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Theme.typeface(font); textAlign = Paint.Align.CENTER; color = pal.dim; textSize = h * 0.13f
        }
        val nameP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Theme.typefaceBold(font); textAlign = Paint.Align.CENTER; color = pal.accent; textSize = h * 0.22f
        }
        val chromeP = Paint(Paint.ANTI_ALIAS_FLAG)

        val labelY = h * 0.24f
        val nameY = h * 0.5f
        c.drawText(context.getString(R.string.remaining), cx, labelY, labelP)
        c.drawText(nextName, cx, nameY, nameP)

        val chromeRect = RectF(w * 0.08f, h * 0.62f, w * 0.92f, h * 0.94f)
        when (style) {
            Settings.CounterStyle.DIGITAL_CARD -> {
                chromeP.style = Paint.Style.FILL; chromeP.color = pal.bgAlt
                c.drawRoundRect(chromeRect, h * 0.06f, h * 0.06f, chromeP)
                chromeP.style = Paint.Style.STROKE; chromeP.strokeWidth = h * 0.008f; chromeP.color = pal.border
                c.drawRoundRect(chromeRect, h * 0.06f, h * 0.06f, chromeP)
            }
            Settings.CounterStyle.PILL -> {
                chromeP.style = Paint.Style.FILL; chromeP.color = pal.accentSoft
                c.drawRoundRect(chromeRect, chromeRect.height() / 2f, chromeRect.height() / 2f, chromeP)
                chromeP.style = Paint.Style.STROKE; chromeP.strokeWidth = h * 0.008f; chromeP.color = pal.border
                c.drawRoundRect(chromeRect, chromeRect.height() / 2f, chromeRect.height() / 2f, chromeP)
            }
            Settings.CounterStyle.OUTLINE -> {
                chromeP.style = Paint.Style.STROKE; chromeP.strokeWidth = h * 0.014f; chromeP.color = pal.accent
                c.drawRoundRect(chromeRect, h * 0.05f, h * 0.05f, chromeP)
            }
            Settings.CounterStyle.RING_PROGRESS -> {
                chromeP.style = Paint.Style.STROKE; chromeP.strokeWidth = h * 0.03f; chromeP.color = pal.border
                c.drawArc(chromeRect, -90f, 360f, false, chromeP)
                chromeP.color = pal.accent
                c.drawArc(chromeRect, -90f, 110f, false, chromeP)
            }
            Settings.CounterStyle.PLAIN_TEXT -> { /* بدون أي إطار */ }
        }
        return bmp
    }

    /** يحمّل صورة المستخدم المخصّصة كخلفية للودجة، مع تعتيم خفيف لوضوح النص فوقها. */
    fun loadCustomBackground(resolver: ContentResolver, uri: Uri, w: Int, h: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val input = resolver.openInputStream(uri) ?: return null
            val src = input.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
            val sw = (src.width * scale).toInt().coerceAtLeast(1)
            val sh = (src.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
            val x = ((sw - w) / 2).coerceAtLeast(0)
            val y = ((sh - h) / 2).coerceAtLeast(0)
            val cropped = Bitmap.createBitmap(scaled, x, y, w.coerceAtMost(sw), h.coerceAtMost(sh))
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawBitmap(cropped, 0f, 0f, null)
            val scrim = Paint().apply { color = 0x66000000 }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrim)
            out
        } catch (e: Exception) { null }
    }
}
