package com.rahmo.prayerwallpaper

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** أدوات مشتركة تُستخدم من كل مزوّدات الودجات الستة — بدون أي إنترنت. */
object WidgetRenderer {

    /**
     * الحجم الفعلي الحالي للودجة بالبكسل (بعد أي تكبير/تصغير يدوي من المستخدم) —
     * الحل الجذري لمشكلة تمطيط/مط النص: بدل ما نرسم دايماً بحجم ثابت مبرمج، نرسم
     * بالحجم الحقيقي الحالي في كل مرة، ونعيد الرسم عند onAppWidgetOptionsChanged.
     */
    fun currentSizePx(context: Context, mgr: AppWidgetManager, id: Int, fallbackWdp: Int, fallbackHdp: Int): Pair<Int, Int> {
        val opts = try { mgr.getAppWidgetOptions(id) } catch (e: Exception) { null }
        val wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: fallbackWdp
        val hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 } ?: fallbackHdp
        val density = context.resources.displayMetrics.density
        val wPx = (wDp * density).roundToInt().coerceAtLeast(60)
        val hPx = (hDp * density).roundToInt().coerceAtLeast(60)
        return wPx to hPx
    }

    fun buildCompassBitmap(context: Context, bearing: Double, sizePx: Int = 220) =
        CompassArt.staticBitmap(Settings.compassShape(context), Theme.current(context), bearing.toFloat(), sizePx)

    /** يرسم لوحة الصلوات الخمس كصورة واحدة (يدعم كل أشكال المواقيت والخطوط) ويضعها بالـ ImageView. */
    fun fillRows(context: Context, views: RemoteViews, day: PrayerData.Day, next: PrayerData.Next?, imageId: Int, wPx: Int, hPx: Int) {
        val pal = Theme.current(context)
        val bmp = WidgetArt.renderRows(context, day, next, pal, Settings.fontStyle(context), Settings.rowStyle(context), wPx, hPx)
        views.setImageViewBitmap(imageId, bmp)
    }

    /** يضبط تسمية الصلاة القادمة + إطار العدّاد (صورة) + الأرقام الحيّة (Chronometer يتحدث لحاله). */
    fun fillCountdown(
        context: Context, views: RemoteViews, next: PrayerData.Next?,
        chromeImageId: Int?, chromeW: Int, chromeH: Int, countdownId: Int
    ) {
        val now = System.currentTimeMillis()
        if (chromeImageId != null) {
            val pal = Theme.current(context)
            val bmp = WidgetArt.renderNextChrome(context, next, pal, Settings.fontStyle(context), Settings.counterStyle(context), chromeW, chromeH)
            views.setImageViewBitmap(chromeImageId, bmp)
            views.setTextColor(countdownId, Theme.current(context).text)
        }
        if (next != null) {
            val base = SystemClock.elapsedRealtime() + (next.atMillis - now)
            views.setChronometerCountDown(countdownId, true)
            views.setChronometer(countdownId, base, null, true)
        } else {
            views.setChronometer(countdownId, SystemClock.elapsedRealtime(), "--:--:--", false)
        }
    }

    fun fillQibla(context: Context, views: RemoteViews, loc: PrayerData.Loc, imageId: Int?, textId: Int?, sizePx: Int = 220) {
        val bearing = PrayerData.qiblaBearing(loc.lat, loc.lon)
        if (imageId != null) views.setImageViewBitmap(imageId, buildCompassBitmap(context, bearing, sizePx))
        if (textId != null) views.setTextViewText(textId, "${context.getString(R.string.qibla)} ${bearing.roundToInt()}°")
    }

    /** يضبط خلفية الودجة: صورة المستخدم إن اختارها (تُشفّف الخلفية الأساسية لتظهر الصورة)، وإلا يبقى ثيم التطبيق. */
    fun fillBackground(context: Context, views: RemoteViews, bgImageId: Int, rootId: Int, wPx: Int, hPx: Int) {
        if (Settings.useCustomBg(context)) {
            val uriStr = Settings.customBgUri(context)
            val bmp = uriStr?.let {
                try { WidgetArt.loadCustomBackground(context.contentResolver, Uri.parse(it), wPx, hPx) } catch (e: Exception) { null }
            }
            if (bmp != null) {
                views.setImageViewBitmap(bgImageId, bmp)
                views.setViewVisibility(bgImageId, android.view.View.VISIBLE)
                views.setInt(rootId, "setBackgroundColor", 0x00000000)
                return
            }
        }
        views.setViewVisibility(bgImageId, android.view.View.GONE)
        views.setInt(rootId, "setBackgroundResource", R.drawable.widget_bg)
    }
}
