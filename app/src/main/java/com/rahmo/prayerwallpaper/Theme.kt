package com.rahmo.prayerwallpaper

import android.graphics.Color

/** لوحات الألوان لكل ثيم — تُستخدم بالخلفية الحيّة وبالودجات معاً. */
object Theme {
    data class Palette(
        val bg: Int, val bgAlt: Int, val accent: Int, val accentSoft: Int,
        val border: Int, val text: Int, val dim: Int, val dimSoft: Int, val ok: Int
    )

    /** ١٦ لون خلفية جاهز إضافي لتصميم الودجة (بالإضافة للثيمات الخمسة المصمَّمة). */
    val FLAT_COLORS_16: List<Int> = listOf(
        Color.parseColor("#0B1220"), Color.parseColor("#16213E"), Color.parseColor("#1B4332"),
        Color.parseColor("#283618"), Color.parseColor("#370617"), Color.parseColor("#3A0CA3"),
        Color.parseColor("#7209B7"), Color.parseColor("#0D1B2A"), Color.parseColor("#6A040F"),
        Color.parseColor("#023047"), Color.parseColor("#386641"), Color.parseColor("#4A4E69"),
        Color.parseColor("#22223B"), Color.parseColor("#6D6875"), Color.parseColor("#E07A5F"),
        Color.parseColor("#F2CC8F")
    )

    /** يبني لوحة ألوان كاملة تلقائياً من لون خلفية واحد (نص فاتح إذا الخلفية غامقة والعكس). */
    fun fromFlatColor(bg: Int): Palette {
        val r = Color.red(bg) / 255.0; val g = Color.green(bg) / 255.0; val b = Color.blue(bg) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val dark = luminance < 0.5
        val text = if (dark) Color.parseColor("#F2E9D8") else Color.parseColor("#14100A")
        val dim = if (dark) Color.parseColor("#B7C0D6") else Color.parseColor("#4A4640")
        val accent = if (dark) Color.parseColor("#E8B24C") else Color.parseColor("#8A4B00")
        return Palette(
            bg = bg,
            bgAlt = adjustLightness(bg, if (dark) 1.18f else 0.9f),
            accent = accent, accentSoft = (accent and 0x00FFFFFF) or 0x24000000,
            border = (accent and 0x00FFFFFF) or 0x38000000,
            text = text, dim = dim, dimSoft = (dim and 0x00FFFFFF) or 0x55000000,
            ok = Color.parseColor("#7FCB9E")
        )
    }

    private fun adjustLightness(color: Int, factor: Float): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] * factor).coerceIn(0f, 1f)
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }

    /** اللوحة الحالية الفعلية: لون مسطّح إن كان مفعّلاً، وإلا الثيم المختار. */
    fun current(ctx: android.content.Context): Palette =
        if (Settings.useFlatColor(ctx)) fromFlatColor(Settings.flatColor(ctx)) else of(Settings.bgTheme(ctx))

    fun of(theme: Settings.BgTheme): Palette = when (theme) {
        Settings.BgTheme.NIGHT_GOLD -> Palette(
            bg = Color.parseColor("#0B1220"), bgAlt = Color.parseColor("#121B2E"),
            accent = Color.parseColor("#E8B24C"), accentSoft = Color.parseColor("#24E8B24C"),
            border = Color.parseColor("#38E8B24C"), text = Color.parseColor("#F2E9D8"),
            dim = Color.parseColor("#9AA6BE"), dimSoft = Color.parseColor("#559AA6BE"),
            ok = Color.parseColor("#7FCB9E")
        )
        Settings.BgTheme.DEEP_TEAL -> Palette(
            bg = Color.parseColor("#08191A"), bgAlt = Color.parseColor("#0E2426"),
            accent = Color.parseColor("#4FD9C4"), accentSoft = Color.parseColor("#244FD9C4"),
            border = Color.parseColor("#384FD9C4"), text = Color.parseColor("#E7FBF7"),
            dim = Color.parseColor("#8FB3AF"), dimSoft = Color.parseColor("#558FB3AF"),
            ok = Color.parseColor("#A6E27A")
        )
        Settings.BgTheme.ROYAL_PURPLE -> Palette(
            bg = Color.parseColor("#150B26"), bgAlt = Color.parseColor("#1F1236"),
            accent = Color.parseColor("#C79CFF"), accentSoft = Color.parseColor("#24C79CFF"),
            border = Color.parseColor("#38C79CFF"), text = Color.parseColor("#F1E9FF"),
            dim = Color.parseColor("#A79ACB"), dimSoft = Color.parseColor("#55A79ACB"),
            ok = Color.parseColor("#8FE3B0")
        )
        Settings.BgTheme.EMERALD -> Palette(
            bg = Color.parseColor("#07201A"), bgAlt = Color.parseColor("#0C2E24"),
            accent = Color.parseColor("#63D9A0"), accentSoft = Color.parseColor("#2463D9A0"),
            border = Color.parseColor("#3863D9A0"), text = Color.parseColor("#E8FBF2"),
            dim = Color.parseColor("#8FBBA9"), dimSoft = Color.parseColor("#558FBBA9"),
            ok = Color.parseColor("#C7E27A")
        )
        Settings.BgTheme.CRIMSON_DUSK -> Palette(
            bg = Color.parseColor("#210B0F"), bgAlt = Color.parseColor("#2E1116"),
            accent = Color.parseColor("#E86B6B"), accentSoft = Color.parseColor("#24E86B6B"),
            border = Color.parseColor("#38E86B6B"), text = Color.parseColor("#FBE8E8"),
            dim = Color.parseColor("#C79A9A"), dimSoft = Color.parseColor("#55C79A9A"),
            ok = Color.parseColor("#8FE0A0")
        )
        Settings.BgTheme.OCEAN_BLUE -> Palette(
            bg = Color.parseColor("#071A2E"), bgAlt = Color.parseColor("#0D2740"),
            accent = Color.parseColor("#4FA8E8"), accentSoft = Color.parseColor("#244FA8E8"),
            border = Color.parseColor("#384FA8E8"), text = Color.parseColor("#E8F3FB"),
            dim = Color.parseColor("#8FAFC7"), dimSoft = Color.parseColor("#558FAFC7"),
            ok = Color.parseColor("#7FCB9E")
        )
        Settings.BgTheme.ROSE_QUARTZ -> Palette(
            bg = Color.parseColor("#240B18"), bgAlt = Color.parseColor("#331227"),
            accent = Color.parseColor("#F2A6C9"), accentSoft = Color.parseColor("#24F2A6C9"),
            border = Color.parseColor("#38F2A6C9"), text = Color.parseColor("#FCE9F2"),
            dim = Color.parseColor("#C79AB2"), dimSoft = Color.parseColor("#55C79AB2"),
            ok = Color.parseColor("#8FE0B8")
        )
    }

    fun typeface(font: Settings.FontStyle): android.graphics.Typeface = when (font) {
        Settings.FontStyle.DEFAULT -> android.graphics.Typeface.DEFAULT
        Settings.FontStyle.SERIF -> android.graphics.Typeface.SERIF
        Settings.FontStyle.MONOSPACE -> android.graphics.Typeface.MONOSPACE
        Settings.FontStyle.CONDENSED -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        Settings.FontStyle.ROUNDED -> android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL)
    }

    fun typefaceBold(font: Settings.FontStyle): android.graphics.Typeface = when (font) {
        Settings.FontStyle.DEFAULT -> android.graphics.Typeface.DEFAULT_BOLD
        Settings.FontStyle.SERIF -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        Settings.FontStyle.MONOSPACE -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        Settings.FontStyle.CONDENSED -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        Settings.FontStyle.ROUNDED -> android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.BOLD)
    }
}
