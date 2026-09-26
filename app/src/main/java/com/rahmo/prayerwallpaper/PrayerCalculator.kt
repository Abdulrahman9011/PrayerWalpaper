package com.rahmo.prayerwallpaper

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * حساب أوقات الصلاة فلكياً بشكل كامل على الجهاز، بدون أي طلب إنترنت.
 * يعتمد على معادلات وضع الشمس الفلكية القياسية (ميل الشمس، معادلة الزمن)
 * المستخدمة في حاسبات مواقيت الصلاة حول العالم.
 */
object PrayerCalculator {

    /** يرجع الأوقات الخمسة (الفجر، الظهر، العصر، المغرب، العشاء) كنص "HH:mm" بالتوقيت المحلي لليوم المحدد. */
    fun calculate(
        lat: Double,
        lon: Double,
        cal: Calendar,
        fajrAngle: Double,
        ishaAngle: Double,
        ishaMinutesAfterMaghrib: Int,
        asrFactor: Double,
        dhuhrAdjMin: Int = 0,
        asrAdjMin: Int = 0,
        maghribAdjMin: Int = 0
    ): List<String> {
        val jd = julianDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) -
            lon / (15.0 * 24.0)
        val tzHours = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0

        val (decl, eqTHours) = sunPosition(jd)
        val dhuhrHour = 12.0 + tzHours - lon / 15.0 - eqTHours

        val fajr = hourAngleTime(fajrAngle, lat, decl, dhuhrHour, afterNoon = false)
        val maghrib = hourAngleTime(0.833, lat, decl, dhuhrHour, afterNoon = true)
        val isha = if (ishaMinutesAfterMaghrib > 0) {
            maghrib + ishaMinutesAfterMaghrib / 60.0
        } else {
            hourAngleTime(ishaAngle, lat, decl, dhuhrHour, afterNoon = true)
        }
        val asr = asrTime(asrFactor, lat, decl, dhuhrHour)

        // تعديلات "الاحتياط" الرسمية بالدقائق (نفس القيم يلي بيعتمدها Rahmoo/ديانت فوق الحساب الفلكي)
        val dhuhrAdj = dhuhrHour + dhuhrAdjMin / 60.0
        val asrAdj = asr + asrAdjMin / 60.0
        val maghribAdj = maghrib + maghribAdjMin / 60.0
        val ishaAdj = if (ishaMinutesAfterMaghrib > 0) maghribAdj + ishaMinutesAfterMaghrib / 60.0 else isha

        return listOf(fajr, dhuhrAdj, asrAdj, maghribAdj, ishaAdj).map { fmtHour(it) }
    }

    // ------------------------------------------------------------------ فلك

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** يرجع (الميل بالدرجات, معادلة الزمن بالساعات) عند تاريخ يوليان معطى. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)          // زاوية الشذوذ المتوسط
        val q = fixAngle(280.459 + 0.98564736 * d)          // خط الطول المتوسط
        val bigL = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g)) // خط الطول الحقيقي
        val e = 23.439 - 0.00000036 * d                     // ميل مسار الأرض
        val ra = fixHour(datan2(dcos(e) * dsin(bigL), dcos(bigL)) / 15.0)
        val eqT = q / 15.0 - ra
        val decl = dasin(dsin(e) * dsin(bigL))
        return Pair(decl, eqT)
    }

    private fun hourAngleTime(angle: Double, lat: Double, decl: Double, dhuhrHour: Double, afterNoon: Boolean): Double {
        val term = (-dsin(angle) - dsin(lat) * dsin(decl)) / (dcos(lat) * dcos(decl))
        val clamped = term.coerceIn(-1.0, 1.0)
        val h = dacos(clamped) / 15.0
        return if (afterNoon) dhuhrHour + h else dhuhrHour - h
    }

    private fun asrTime(factor: Double, lat: Double, decl: Double, dhuhrHour: Double): Double {
        val altitude = datan(1.0 / (factor + dtan(abs(lat - decl))))
        val term = (dsin(altitude) - dsin(lat) * dsin(decl)) / (dcos(lat) * dcos(decl))
        val clamped = term.coerceIn(-1.0, 1.0)
        val h = dacos(clamped) / 15.0
        return dhuhrHour + h
    }

    private fun fmtHour(hourRaw: Double): String {
        var hour = hourRaw
        while (hour < 0) hour += 24.0
        while (hour >= 24.0) hour -= 24.0
        val totalMinutes = Math.round(hour * 60.0)
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }

    // ---------------------------------------------------------------- helpers بالدرجات
    private fun fixAngle(a: Double): Double { var x = a % 360.0; if (x < 0) x += 360.0; return x }
    private fun fixHour(a: Double): Double { var x = a % 24.0; if (x < 0) x += 24.0; return x }
    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun dasin(x: Double) = Math.toDegrees(Math.asin(x.coerceIn(-1.0, 1.0)))
    private fun dacos(x: Double) = Math.toDegrees(Math.acos(x.coerceIn(-1.0, 1.0)))
    private fun datan(x: Double) = Math.toDegrees(atan(x))
    private fun datan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
}
