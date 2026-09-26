package com.rahmo.prayerwallpaper

import android.content.Context

/**
 * كود مشاركة التصميم: يحوّل اختيارات تصميم الودجة (٥ فئات × ٥ خيارات) إلى رقم من ٨ خانات
 * وبالعكس — بدون أي خادم أو إنترنت، الكود نفسه يحمل التصميم بالكامل.
 * ملاحظة: يعمل فقط مع التصاميم الجاهزة؛ خلفية الصورة المخصّصة تبقى محلية على جهاز صاحبها.
 */
object WidgetDesignCode {

    fun currentCode(ctx: Context): String {
        val bg = Settings.BgTheme.entries.indexOf(Settings.bgTheme(ctx))
        val row = Settings.RowStyle.entries.indexOf(Settings.rowStyle(ctx))
        val counter = Settings.CounterStyle.entries.indexOf(Settings.counterStyle(ctx))
        val font = Settings.FontStyle.entries.indexOf(Settings.fontStyle(ctx))
        val compass = Settings.CompassShape.entries.indexOf(Settings.compassShape(ctx))
        val value = (((bg * 5 + row) * 5 + counter) * 5 + font) * 5 + compass
        val checksum = (value * 7 + 13) % 10000
        return String.format(java.util.Locale.US, "%04d%04d", value, checksum)
    }

    /** يطبّق الكود على الإعدادات إن كان صالحاً؛ يرجّع true عند النجاح. */
    fun apply(ctx: Context, rawCode: String): Boolean {
        val digits = rawCode.filter { it.isDigit() }
        if (digits.length != 8) return false
        val value = digits.substring(0, 4).toIntOrNull() ?: return false
        val checksum = digits.substring(4, 8).toIntOrNull() ?: return false
        if ((value * 7 + 13) % 10000 != checksum) return false
        if (value !in 0..3124) return false

        var v = value
        val compass = v % 5; v /= 5
        val font = v % 5; v /= 5
        val counter = v % 5; v /= 5
        val row = v % 5; v /= 5
        val bg = v % 5

        Settings.setBgTheme(ctx, Settings.BgTheme.entries[bg])
        Settings.setRowStyle(ctx, Settings.RowStyle.entries[row])
        Settings.setCounterStyle(ctx, Settings.CounterStyle.entries[counter])
        Settings.setFontStyle(ctx, Settings.FontStyle.entries[font])
        Settings.setCompassShape(ctx, Settings.CompassShape.entries[compass])
        return true
    }
}
