package com.rahmo.prayerwallpaper

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * طبقة البيانات: الموقع + أوقات الصلاة. كل الحساب يتم محلياً على الجهاز
 * (PrayerCalculator) — لا حاجة لإنترنت إطلاقاً لحساب المواقيت أو اتجاه القبلة.
 * الإنترنت (إن وُجد) يُستخدم فقط كتحسين اختياري لعرض اسم المدينة تلقائياً.
 */
object PrayerData {

    data class Loc(val lat: Double, val lon: Double, val city: String)
    data class Day(
        val date: String,
        val times: List<String>,      // الفجر، الظهر، العصر، المغرب، العشاء (HH:mm)
        val fajrTomorrow: String,
        val lat: Double,
        val lon: Double
    )
    data class Next(val index: Int, val time: String, val atMillis: Long)

    private const val PREFS = "prayer_prefs"
    private const val DEFAULT_LAT = 40.1826
    private const val DEFAULT_LON = 29.0665

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- location

    fun hasLocation(ctx: Context): Boolean = Settings.manualLocationEnabled(ctx) || prefs(ctx).contains("lat")

    fun location(ctx: Context): Loc {
        if (Settings.manualLocationEnabled(ctx)) {
            return Loc(Settings.manualLat(ctx), Settings.manualLon(ctx), Settings.manualCity(ctx))
        }
        val p = prefs(ctx)
        val lat = p.getString("lat", null)?.toDoubleOrNull()
        val lon = p.getString("lon", null)?.toDoubleOrNull()
        return if (lat != null && lon != null) {
            Loc(lat, lon, p.getString("city", "") ?: "")
        } else {
            Loc(DEFAULT_LAT, DEFAULT_LON, ctx.getString(R.string.default_city))
        }
    }

    fun saveLocation(ctx: Context, lat: Double, lon: Double, city: String) {
        prefs(ctx).edit()
            .putString("lat", lat.toString())
            .putString("lon", lon.toString())
            .putString("city", city)
            .apply()
        invalidateCache(ctx)
    }

    private fun invalidateCache(ctx: Context) {
        prefs(ctx).edit().remove("day_date").apply()
    }

    // ------------------------------------------------------------------- حساب محلي

    /** يحسب (أو يرجّع من الذاكرة المؤقتة) أوقات اليوم الحالي — عملية فورية بدون شبكة. */
    fun cached(ctx: Context): Day {
        val loc = location(ctx)
        val p = prefs(ctx)
        val today = dateKey(0)
        val cachedDate = p.getString("day_date", null)
        val cachedLat = p.getString("day_lat", null)?.toDoubleOrNull()
        val cachedLon = p.getString("day_lon", null)?.toDoubleOrNull()
        val stillValid = cachedDate == today &&
            cachedLat != null && cachedLon != null &&
            kotlin.math.abs(cachedLat - loc.lat) < 0.001 && kotlin.math.abs(cachedLon - loc.lon) < 0.001

        if (stillValid) {
            val times = p.getString("day_times", null)?.split(",")
            val fajr2 = p.getString("day_fajr2", null)
            if (times != null && times.size == 5 && fajr2 != null) {
                return Day(today, times, fajr2, loc.lat, loc.lon)
            }
        }
        return computeAndStore(ctx, loc)
    }

    private fun computeAndStore(ctx: Context, loc: Loc): Day {
        val method = Settings.calcMethod(ctx)
        val asr = Settings.asrMethod(ctx)

        val todayCal = Calendar.getInstance()
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        val today = PrayerCalculator.calculate(
            loc.lat, loc.lon, todayCal, method.fajrAngle, method.ishaAngle, method.ishaMinutesAfterMaghrib, asr.factor,
            method.dhuhrAdjMin, method.asrAdjMin, method.maghribAdjMin
        )
        val tomorrow = PrayerCalculator.calculate(
            loc.lat, loc.lon, tomorrowCal, method.fajrAngle, method.ishaAngle, method.ishaMinutesAfterMaghrib, asr.factor,
            method.dhuhrAdjMin, method.asrAdjMin, method.maghribAdjMin
        )

        prefs(ctx).edit()
            .putString("day_date", dateKey(0))
            .putString("day_times", today.joinToString(","))
            .putString("day_fajr2", tomorrow[0])
            .putString("day_lat", loc.lat.toString())
            .putString("day_lon", loc.lon.toString())
            .apply()

        return Day(dateKey(0), today, tomorrow[0], loc.lat, loc.lon)
    }

    /** يُستدعى بعد تغيير الموقع أو إعدادات الحساب لإجبار إعادة الحساب فوراً. */
    fun recompute(ctx: Context): Day {
        invalidateCache(ctx)
        return cached(ctx)
    }

    // ------------------------------------------------------------ next prayer

    fun next(day: Day, now: Long): Next {
        for (i in 0 until 5) {
            val at = millisOf(day.times[i], 0)
            if (at > now) return Next(i, day.times[i], at)
        }
        return Next(0, day.fajrTomorrow, millisOf(day.fajrTomorrow, 1))
    }

    private fun millisOf(hhmm: String, plusDays: Int): Long {
        val parts = hhmm.split(":")
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, plusDays)
        cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        cal.set(Calendar.MINUTE, parts[1].toInt())
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ---------------------------------------------------------- تحسين اختياري (يحتاج إنترنت)

    /** اسم المدينة فقط — تحسين تجميلي اختياري. لا شيء بالتطبيق يعتمد عليه. */
    fun reverseGeocode(lat: Double, lon: Double): String? = try {
        val url = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
            "?latitude=${fmt(lat)}&longitude=${fmt(lon)}&localityLanguage=ar"
        val d = JSONObject(httpGet(url))
        val city = d.optString("city")
            .ifEmpty { d.optString("locality") }
            .ifEmpty { d.optString("principalSubdivision") }
        val country = d.optString("countryName")
        listOf(city, country).filter { it.isNotEmpty() }.joinToString("، ").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 6_000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)

    /** الاتجاه الابتدائي (بالنسبة للشمال الحقيقي) من موقعك إلى الكعبة المشرّفة، بالدرجات (0-360). */
    fun qiblaBearing(latDeg: Double, lonDeg: Double): Double {
        val kaabaLat = Math.toRadians(21.422487)
        val kaabaLon = Math.toRadians(39.826206)
        val phi = Math.toRadians(latDeg)
        val dLon = kaabaLon - Math.toRadians(lonDeg)
        val y = kotlin.math.sin(dLon)
        val x = kotlin.math.cos(phi) * kotlin.math.tan(kaabaLat) - kotlin.math.sin(phi) * kotlin.math.cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    private fun dateKey(plusDays: Int): String = formatDate("yyyy-MM-dd", plusDays)

    private fun formatDate(pattern: String, plusDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, plusDays)
        return SimpleDateFormat(pattern, Locale.US).format(cal.time)
    }
}
