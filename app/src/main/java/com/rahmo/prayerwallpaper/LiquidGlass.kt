package com.rahmo.prayerwallpaper

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize

/**
 * نظام تصميم "الزجاج السائل" (Liquid Glass) — يحاكي أسلوب iOS الحديث: خلفية
 * عميقة بفقاعات ألوان ضبابية ناعمة، وفوقها ألواح زجاجية شبه شفافة بحواف مضيئة
 * رقيقة. يُستخدم هذا الملف كأساس موحّد لكل شاشات التطبيق (الرئيسية، شاشة
 * المنبّه، الإعدادات...) حتى يكون شكل التطبيق متّسقاً بالكامل.
 */
object LiquidGlass {
    // خلفية عميقة داكنة تحل محل الأسود المسطّح
    val DeepSpace = Color(0xFF06070C)
    val DeepSpace2 = Color(0xFF10142A)

    // لون العلامة التجارية (ذهبي) + ألوان الفقاعات الضبابية المرافقة
    val Gold = Color(0xFFE8B24C)
    val Teal = Color(0xFF4FD9C4)
    val Violet = Color(0xFF8F7BFF)

    val TextPrimary = Color(0xFFF5F1E8)
    val TextDim = Color(0xFFA9B2C8)
    val Ok = Color(0xFF7FCB9E)
    val Err = Color(0xFFE8746C)
    val OnAccent = Color(0xFF1A1206)

    // تعبئة الزجاج وحدوده
    val GlassFill = Color(0x1FFFFFFF)
    val GlassFillStrong = Color(0x33FFFFFF)
    val GlassBorder = Color(0x4DFFFFFF)
    val GlassHighlight = Color(0x73FFFFFF)

    fun glassBrush(strong: Boolean = false): Brush = Brush.verticalGradient(
        listOf(
            if (strong) GlassFillStrong else GlassFill,
            if (strong) GlassFill else Color(0x0FFFFFFF)
        )
    )

    fun borderBrush(): Brush = Brush.linearGradient(
        listOf(GlassHighlight, GlassBorder, Color(0x14FFFFFF))
    )
}

/** يضيف مظهر "الزجاج السائل" لأي عنصر: ظل ناعم + تعبئة شفافة متدرّجة + حد مضيء. */
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(26.dp), strong: Boolean = false): Modifier = this
    .shadow(14.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
    .clip(shape)
    .background(LiquidGlass.glassBrush(strong), shape)
    .border(1.dp, LiquidGlass.borderBrush(), shape)

/**
 * تمرير أفقي يعكس اتجاه اللمس تلقائياً عند اللغات اللي تُكتب من اليمين لليسار (متل
 * العربي)، حتى يكون السحب لليمين ولليسار مطابقاً لما يتوقعه المستخدم، بدل ما يصير
 * معكوساً (تسحب يمين فينزاح المحتوى يسار والعكس).
 */
@Composable
fun Modifier.rtlAwareHorizontalScroll(state: ScrollState): Modifier {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this.horizontalScroll(state, reverseScrolling = isRtl)
}

/**
 * خلفية عامة عميقة بفقاعات ألوان ضبابية — أساس "الزجاج السائل" الموحّد لكل
 * شاشات التطبيق. توضع كأول عنصر بجذر الشاشة ثم توضع بقية الواجهة فوقها.
 */
@Composable
fun LiquidGlassBackground(decorative: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LiquidGlass.DeepSpace2, LiquidGlass.DeepSpace)))
    ) {
        if (decorative) {
            Box(
                Modifier
                    .size(320.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 90.dp, y = (-70).dp)
                    .blur(120.dp)
                    .background(LiquidGlass.Gold.copy(alpha = 0.35f), CircleShape)
            )
            Box(
                Modifier
                    .size(280.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (-100).dp)
                    .blur(120.dp)
                    .background(LiquidGlass.Violet.copy(alpha = 0.28f), CircleShape)
            )
            Box(
                Modifier
                    .size(260.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 60.dp, y = 70.dp)
                    .blur(120.dp)
                    .background(LiquidGlass.Teal.copy(alpha = 0.22f), CircleShape)
            )
        }
        content()
    }
}
