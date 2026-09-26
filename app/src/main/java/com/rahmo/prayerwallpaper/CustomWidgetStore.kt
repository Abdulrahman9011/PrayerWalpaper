package com.rahmo.prayerwallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * تخزين تصميم الـwidget الحر (المخصّص بالسحب) — تصميم واحد محفوظ حالياً،
 * يُطبَّق على الودجة السابعة "المخصّص" (PrayerWidgetCustomProvider).
 * كل عنصر إحداثيّاته نسبة (0..1) من مساحة الودجة — فتُترجم لأي حجم فعلي عند الرسم.
 */
object CustomWidgetStore {

    enum class ElementType { TIMES, COUNTDOWN, COMPASS, CITY }

    data class Element(val type: ElementType, var xFrac: Float, var yFrac: Float)

    data class Design(
        var bgUri: String? = null,
        var bgColor: Int = Theme.FLAT_COLORS_16[0],
        var widthDp: Int = 250,
        var heightDp: Int = 140,
        var elements: MutableList<Element> = mutableListOf(
            Element(ElementType.COUNTDOWN, 0.5f, 0.26f),
            Element(ElementType.TIMES, 0.5f, 0.7f)
        )
    )

    private const val PREFS = "custom_widget_prefs"
    private const val KEY = "design_json"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): Design {
        val raw = prefs(ctx).getString(KEY, null) ?: return Design()
        return try {
            val o = JSONObject(raw)
            val els = mutableListOf<Element>()
            val arr = o.getJSONArray("elements")
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                els.add(Element(ElementType.valueOf(e.getString("type")), e.getDouble("x").toFloat(), e.getDouble("y").toFloat()))
            }
            Design(
                bgUri = if (o.isNull("bgUri")) null else o.optString("bgUri", null),
                bgColor = o.optInt("bgColor", Theme.FLAT_COLORS_16[0]),
                widthDp = o.optInt("widthDp", 250),
                heightDp = o.optInt("heightDp", 140),
                elements = if (els.isEmpty()) Design().elements else els
            )
        } catch (e: Exception) { Design() }
    }

    fun save(ctx: Context, d: Design) {
        val o = JSONObject()
        o.put("bgUri", d.bgUri)
        o.put("bgColor", d.bgColor)
        o.put("widthDp", d.widthDp)
        o.put("heightDp", d.heightDp)
        val arr = JSONArray()
        for (e in d.elements) {
            val eo = JSONObject()
            eo.put("type", e.type.name); eo.put("x", e.xFrac); eo.put("y", e.yFrac)
            arr.put(eo)
        }
        o.put("elements", arr)
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }
}
