package com.rahmo.prayerwallpaper

import android.content.Context

/**
 * كل إعدادات التصميم والتنبيهات والحساب — مخزّنة محلياً (SharedPreferences)،
 * لا شيء منها يحتاج إنترنت.
 */
object Settings {
    private const val PREFS = "app_settings"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ سرعة البوصلة
    enum class CompassSpeed(val ms: Long) {
        REALTIME(33L),   // ~30 مرة بالثانية
        FAST(150L),      // أسرع من مرة كل ثانية
        NORMAL(500L),
        SLOW(1000L)      // مرة كل ثانية بالضبط
    }

    fun compassSpeed(ctx: Context): CompassSpeed = try {
        CompassSpeed.valueOf(p(ctx).getString("compass_speed", CompassSpeed.FAST.name)!!)
    } catch (e: Exception) { CompassSpeed.FAST }

    fun setCompassSpeed(ctx: Context, v: CompassSpeed) {
        p(ctx).edit().putString("compass_speed", v.name).apply()
    }

    // ------------------------------------------------------------ طريقة الحساب (بدون إنترنت)
    enum class CalcMethod(
        val fajrAngle: Double,
        val ishaAngle: Double,
        val ishaMinutesAfterMaghrib: Int,
        val dhuhrAdjMin: Int = 0,
        val asrAdjMin: Int = 0,
        val maghribAdjMin: Int = 0
    ) {
        DIYANET(18.0, 17.0, 0, dhuhrAdjMin = 5, asrAdjMin = 4, maghribAdjMin = 7),
        MWL(18.0, 17.0, 0, dhuhrAdjMin = 1),
        ISNA(15.0, 15.0, 0, dhuhrAdjMin = 1),
        EGYPT(19.5, 17.5, 0, dhuhrAdjMin = 1),
        KARACHI(18.0, 18.0, 0, dhuhrAdjMin = 1),
        MAKKAH(18.5, 0.0, 90)
    }

    fun calcMethod(ctx: Context): CalcMethod = try {
        CalcMethod.valueOf(p(ctx).getString("calc_method", CalcMethod.DIYANET.name)!!)
    } catch (e: Exception) { CalcMethod.DIYANET }

    fun setCalcMethod(ctx: Context, v: CalcMethod) {
        p(ctx).edit().putString("calc_method", v.name).apply()
    }

    enum class AsrMethod(val factor: Double) { STANDARD(1.0), HANAFI(2.0) }

    fun asrMethod(ctx: Context): AsrMethod = try {
        AsrMethod.valueOf(p(ctx).getString("asr_method", AsrMethod.STANDARD.name)!!)
    } catch (e: Exception) { AsrMethod.STANDARD }

    fun setAsrMethod(ctx: Context, v: AsrMethod) {
        p(ctx).edit().putString("asr_method", v.name).apply()
    }

    // ------------------------------------------------------------ تصميم الودجة (حرية كاملة) — لا يمس الخلفية الحيّة
    enum class BgTheme { NIGHT_GOLD, DEEP_TEAL, ROYAL_PURPLE, EMERALD, CRIMSON_DUSK, OCEAN_BLUE, ROSE_QUARTZ }
    enum class CompassShape { CLASSIC, MODERN_RING, MINIMAL_ARROW, ARC_GAUGE, TRIANGLE_POINTER }
    enum class CounterStyle { DIGITAL_CARD, RING_PROGRESS, PLAIN_TEXT, PILL, OUTLINE }
    enum class FontStyle { DEFAULT, SERIF, MONOSPACE, CONDENSED, ROUNDED }
    enum class RowStyle { CARD, DIVIDED, COMPACT, BORDERED, MINIMAL }

    /** الثيم الثابت المستخدم بالخلفية الحيّة فقط — لا يتغيّر، تصميم الودجة منفصل تماماً. */
    val WALLPAPER_THEME = BgTheme.NIGHT_GOLD
    val WALLPAPER_COMPASS = CompassShape.CLASSIC
    val WALLPAPER_COUNTER = CounterStyle.DIGITAL_CARD
    val WALLPAPER_FONT = FontStyle.DEFAULT

    fun bgTheme(ctx: Context): BgTheme = try {
        BgTheme.valueOf(p(ctx).getString("w_bg_theme", BgTheme.NIGHT_GOLD.name)!!)
    } catch (e: Exception) { BgTheme.NIGHT_GOLD }
    fun setBgTheme(ctx: Context, v: BgTheme) {
        p(ctx).edit().putString("w_bg_theme", v.name).putBoolean("w_bg_custom", false).putBoolean("w_bg_flat", false).apply()
    }

    // لون خلفية مسطّح (أحد الـ١٦ لون الجاهز) — بديل عن الثيمات المصمَّمة أو الصورة
    fun useFlatColor(ctx: Context): Boolean = p(ctx).getBoolean("w_bg_flat", false)
    fun flatColor(ctx: Context): Int = p(ctx).getInt("w_bg_flat_color", Theme.FLAT_COLORS_16[0])
    fun setFlatColor(ctx: Context, color: Int) {
        p(ctx).edit().putInt("w_bg_flat_color", color).putBoolean("w_bg_flat", true).putBoolean("w_bg_custom", false).apply()
    }

    fun compassShape(ctx: Context): CompassShape = try {
        CompassShape.valueOf(p(ctx).getString("w_compass_shape", CompassShape.CLASSIC.name)!!)
    } catch (e: Exception) { CompassShape.CLASSIC }
    fun setCompassShape(ctx: Context, v: CompassShape) { p(ctx).edit().putString("w_compass_shape", v.name).apply() }

    fun counterStyle(ctx: Context): CounterStyle = try {
        CounterStyle.valueOf(p(ctx).getString("w_counter_style", CounterStyle.DIGITAL_CARD.name)!!)
    } catch (e: Exception) { CounterStyle.DIGITAL_CARD }
    fun setCounterStyle(ctx: Context, v: CounterStyle) { p(ctx).edit().putString("w_counter_style", v.name).apply() }

    fun fontStyle(ctx: Context): FontStyle = try {
        FontStyle.valueOf(p(ctx).getString("w_font_style", FontStyle.DEFAULT.name)!!)
    } catch (e: Exception) { FontStyle.DEFAULT }
    fun setFontStyle(ctx: Context, v: FontStyle) { p(ctx).edit().putString("w_font_style", v.name).apply() }

    fun rowStyle(ctx: Context): RowStyle = try {
        RowStyle.valueOf(p(ctx).getString("w_row_style", RowStyle.CARD.name)!!)
    } catch (e: Exception) { RowStyle.CARD }
    fun setRowStyle(ctx: Context, v: RowStyle) { p(ctx).edit().putString("w_row_style", v.name).apply() }

    // خلفية مخصّصة (صورة من الألبوم) لتصميم الودجة فقط
    fun useCustomBg(ctx: Context): Boolean = p(ctx).getBoolean("w_bg_custom", false)
    fun customBgUri(ctx: Context): String? = p(ctx).getString("w_bg_uri", null)
    fun setCustomBg(ctx: Context, uri: String) {
        p(ctx).edit().putString("w_bg_uri", uri).putBoolean("w_bg_custom", true).putBoolean("w_bg_flat", false).apply()
    }
    fun clearCustomBg(ctx: Context) { p(ctx).edit().putBoolean("w_bg_custom", false).apply() }

    // ------------------------------------------------------------ إشعار الأذان
    fun adhanNotifEnabled(ctx: Context): Boolean = p(ctx).getBoolean("adhan_notif", true)
    fun setAdhanNotifEnabled(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("adhan_notif", v).apply() }

    fun vibrationEnabled(ctx: Context): Boolean = p(ctx).getBoolean("adhan_vibrate", true)
    fun setVibrationEnabled(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("adhan_vibrate", v).apply() }

    /** رنة عند دخول وقت الأذان — مطفأة افتراضياً، المستخدم يشغّلها متى ما بدّه. لا موسيقى إطلاقاً. */
    fun ringtoneEnabled(ctx: Context): Boolean = p(ctx).getBoolean("adhan_ringtone_on", false)
    fun setRingtoneEnabled(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("adhan_ringtone_on", v).apply() }

    fun ringtoneUri(ctx: Context): String? = p(ctx).getString("adhan_ringtone_uri", null)
    fun setRingtoneUri(ctx: Context, uri: String?) { p(ctx).edit().putString("adhan_ringtone_uri", uri).apply() }

    /** إذا مفعّل: المنبه يستمر (صوت + اهتزاز) ولا ينطفي تلقائياً أبداً — فقط المستخدم يقدر يطفيه. */
    fun persistentAlarm(ctx: Context): Boolean = p(ctx).getBoolean("adhan_persistent", false)
    fun setPersistentAlarm(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("adhan_persistent", v).apply() }

    // ------------------------------------------------------------ موقع يدوي (اختياري، للعمل بدون أي إنترنت)
    fun manualLocationEnabled(ctx: Context): Boolean = p(ctx).getBoolean("manual_loc", false)
    fun setManualLocation(ctx: Context, lat: Double, lon: Double, city: String) {
        p(ctx).edit()
            .putBoolean("manual_loc", true)
            .putString("manual_lat", lat.toString())
            .putString("manual_lon", lon.toString())
            .putString("manual_city", city)
            .apply()
    }
    fun clearManualLocation(ctx: Context) { p(ctx).edit().putBoolean("manual_loc", false).apply() }
    fun manualLat(ctx: Context): Double = p(ctx).getString("manual_lat", "0")?.toDoubleOrNull() ?: 0.0
    fun manualLon(ctx: Context): Double = p(ctx).getString("manual_lon", "0")?.toDoubleOrNull() ?: 0.0
    fun manualCity(ctx: Context): String = p(ctx).getString("manual_city", "") ?: ""

    // ------------------------------------------------------------ تذكير مسبق قبل الأذان (إشعار خفيف، بدون رنة أو اهتزاز)
    /** بالدقائق: 0 يعني مطفأ. القيم المسموحة: 0, 5, 10, 15, 20. */
    fun reminderMinutes(ctx: Context): Int = p(ctx).getInt("reminder_minutes", 0)
    fun setReminderMinutes(ctx: Context, minutes: Int) { p(ctx).edit().putInt("reminder_minutes", minutes).apply() }

    // ------------------------------------------------------------ مظهر شاشة التطبيق نفسها (لا يمس الودجات ولا الخلفية الحيّة)
    /** فقاعات الزجاج الضبابية بخلفية شاشة الإعدادات — يمكن إيقافها لمظهر أبسط. */
    fun decorativeAppBg(ctx: Context): Boolean = p(ctx).getBoolean("app_bg_decorative", true)
    fun setDecorativeAppBg(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("app_bg_decorative", v).apply() }
}
