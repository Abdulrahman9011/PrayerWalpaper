package com.rahmo.prayerwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri

/** يرسم تصميم الـwidget الحر بالحجم الفعلي المطلوب — يُستخدم بالمعاينة داخل التطبيق وبالودجة الحقيقية معاً. */
object CustomWidgetRenderer {

    fun render(ctx: Context, design: CustomWidgetStore.Design, wPx: Int, hPx: Int): Bitmap {
        val w = wPx.coerceAtLeast(30); val h = hPx.coerceAtLeast(30)
        val bgBmp = design.bgUri?.let {
            try { WidgetArt.loadCustomBackground(ctx.contentResolver, Uri.parse(it), w, h) } catch (e: Exception) { null }
        }
        // الصورة (لو موجودة) بتتعتّم أصلاً بالتحميل، فمنستخدم لوحة فاتحة-على-غامق ثابتة تناسبها دايماً
        val pal = if (bgBmp != null) Theme.of(Settings.BgTheme.NIGHT_GOLD) else Theme.fromFlatColor(design.bgColor)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (bgBmp != null) c.drawBitmap(bgBmp, 0f, 0f, null) else c.drawColor(design.bgColor)

        val day = PrayerData.cached(ctx)
        val next = PrayerData.next(day, System.currentTimeMillis())
        val loc = PrayerData.location(ctx)
        val font = Settings.fontStyle(ctx)

        for (el in design.elements) {
            val cx = el.xFrac * w
            val cy = el.yFrac * h
            when (el.type) {
                CustomWidgetStore.ElementType.TIMES -> {
                    val ew = (w * 0.62f).toInt().coerceAtLeast(60)
                    val eh = (h * 0.62f).toInt().coerceAtLeast(60)
                    val bmp = WidgetArt.renderRows(ctx, day, next, pal, font, Settings.rowStyle(ctx), ew, eh)
                    c.drawBitmap(bmp, cx - ew / 2f, cy - eh / 2f, null)
                }
                CustomWidgetStore.ElementType.COUNTDOWN -> {
                    val ew = (w * 0.7f).toInt().coerceAtLeast(60)
                    val eh = (h * 0.36f).toInt().coerceAtLeast(40)
                    val bmp = WidgetArt.renderNextChrome(ctx, next, pal, font, Settings.counterStyle(ctx), ew, eh)
                    c.drawBitmap(bmp, cx - ew / 2f, cy - eh / 2f, null)
                    val remain = (next.atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                    val txt = String.format(
                        java.util.Locale.US, "%02d:%02d:%02d",
                        remain / 3_600_000, (remain % 3_600_000) / 60_000, (remain % 60_000) / 1_000
                    )
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = pal.text; textAlign = Paint.Align.CENTER
                        typeface = Theme.typefaceBold(font); textSize = eh * 0.3f
                    }
                    c.drawText(txt, cx, cy + eh * 0.32f, p)
                }
                CustomWidgetStore.ElementType.COMPASS -> {
                    val size = (minOf(w, h) * 0.42f).toInt().coerceAtLeast(40)
                    val bmp = WidgetRenderer.buildCompassBitmap(ctx, PrayerData.qiblaBearing(loc.lat, loc.lon), size)
                    c.drawBitmap(bmp, cx - size / 2f, cy - size / 2f, null)
                }
                CustomWidgetStore.ElementType.CITY -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = pal.dim; textAlign = Paint.Align.CENTER
                        typeface = Theme.typeface(font); textSize = h * 0.09f
                    }
                    c.drawText(loc.city, cx, cy, p)
                }
            }
        }
        return out
    }
}
