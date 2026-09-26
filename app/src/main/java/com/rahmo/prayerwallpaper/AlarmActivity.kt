package com.rahmo.prayerwallpaper

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * شاشة المنبّه — تظهر فوق شاشة القفل كـ"بانر" زجاجي بأعلى الشاشة (بنفس أسلوب
 * تنبيه المنبّه بالآيفون) بدل تعبئة الشاشة بالكامل: زر الإيقاف على اليسار،
 * عداد الوقت بالمنتصف، وأيقونة التطبيق على اليمين. يبقى البانر في منتصف أعلى
 * الشاشة تماماً سواء كان الهاتف بوضع الطول أو العرض. الزر الوحيد الذي يوقف
 * المنبّه هو زر "إيقاف" هنا (أو من الإشعار) — لا يوجد أي إيقاف تلقائي آخر غيره
 * (باستثناء سقف الخمس دقائق المُدار من AlarmRingService حين لا يكون خيار
 * "استمرار حتى أوقفه بنفسي" مفعّلاً).
 */
class AlarmActivity : ComponentActivity() {

    companion object { const val EXTRA_PRAYER_NAME = "prayer_name" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val name = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: ""
        setContent { AlarmScreen(name, ::onStopClick) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    // زر الرجوع لا يوقف المنبّه — فقط زر "إيقاف" الصريح يفعل
    override fun onBackPressed() { /* متعمّد: تجاهل */ }

    private fun onStopClick() {
        val stop = Intent(this, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_STOP)
        startService(stop)
        finish()
    }
}

@Composable
private fun AlarmScreen(prayerName: String, onStop: () -> Unit) {
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSec++
        }
    }
    val mm = elapsedSec / 60
    val ss = elapsedSec % 60
    val elapsedText = String.format(java.util.Locale.US, "%02d:%02d", mm, ss)

    // نبضة هادئة خلف زر الإيقاف — تلميح بصري بأن المنبّه ما زال يرن
    val pulseTransition = rememberInfiniteTransition(label = "alarmPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    LiquidGlassBackground {
        // البانر يبقى في منتصف أعلى الشاشة بنفس الطريقة تماماً بوضعي الطول والعرض
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 22.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AlarmBanner(
                prayerName = prayerName,
                elapsedText = elapsedText,
                pulseAlpha = pulse,
                onStop = onStop
            )
        }
    }
}

/** البانر الزجاجي نفسه: زر الإيقاف يسار — العداد بالمنتصف — أيقونة التطبيق يمين. */
@Composable
private fun AlarmBanner(
    prayerName: String,
    elapsedText: String,
    pulseAlpha: Float,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(0.94f)
            .liquidGlass(shape = RoundedCornerShape(30.dp), strong = true)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // يسار: زر الإيقاف (بتوهج نابض خلفه)
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(LiquidGlass.Err.copy(alpha = 0.35f * pulseAlpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(LiquidGlass.Err)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        // المنتصف: عداد الوقت (العنصر الأبرز) + اسم الصلاة تحته
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                elapsedText,
                color = LiquidGlass.Gold,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.alarm_time_for_prayer) + " " + prayerName,
                color = LiquidGlass.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Spacer(Modifier.weight(1f))

        // يمين: أيقونة التطبيق
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1220)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
