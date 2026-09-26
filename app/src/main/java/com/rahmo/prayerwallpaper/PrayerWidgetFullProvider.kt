package com.rahmo.prayerwallpaper

/** تصميم "الكامل" — نفس بيانات الكلاسيكي بمظهر أكبر وأغنى (٤×٢). */
class PrayerWidgetFullProvider : PrayerWidgetProvider() {
    override val layoutRes: Int get() = R.layout.widget_prayer_full
    override val fallbackWdp: Int get() = 280
    override val fallbackHdp: Int get() = 170
}
