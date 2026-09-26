package com.rahmo.prayerwallpaper
import androidx.activity.compose.setContent

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val BgColor = LiquidGlass.DeepSpace
private val CardColor = LiquidGlass.GlassFillStrong
private val AccentColor = LiquidGlass.Gold
private val TextColor = LiquidGlass.TextPrimary
private val DimColor = LiquidGlass.TextDim
private val OkColor = LiquidGlass.Ok

class MainViewModel : ViewModel() {
    // يُستخدم لإجبار إعادة رسم الشاشة الرئيسية بعد أي تغيير بالإعدادات (بعد الرجوع من شاشة الإعدادات مثلاً)
    var tick by mutableStateOf(0)
}

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdhanScheduler.scheduleAll(this)
        setContent {
            AppRoot(
                vm = vm,
                onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // نعيد التحديث دايماً عند الرجوع للشاشة الرئيسية (من الإعدادات أو تصميم الودجات)
        // حتى تنعكس أي تغييرات فوراً بدون ما يحتاج المستخدم يقفل التطبيق ويفتحه من جديد
        vm.tick++
    }
}

@Composable
fun AppRoot(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val refreshKey = vm.tick
    val decorative = Settings.decorativeAppBg(ctx)

    MaterialTheme(
        colorScheme = darkColorScheme(background = BgColor, surface = CardColor, primary = AccentColor, onBackground = TextColor)
    ) {
        LiquidGlassBackground(decorative = decorative) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource_(R.string.open_settings), tint = AccentColor)
                    }
                }
                Text(stringResource_(R.string.title_main), color = AccentColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource_(R.string.intro), color = DimColor, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))

                Spacer(Modifier.height(20.dp))
                Text(stringResource_(R.string.section_overview), color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(10.dp))
                OverviewCard(ctx, refreshKey)

                Spacer(Modifier.height(20.dp))
                Text(stringResource_(R.string.section_ready_widgets), color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(10.dp))
                ReadyWidgetsGallery(ctx, refreshKey)

                Spacer(Modifier.height(16.dp))
                var designMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { designMenu = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF14100A))
                    ) {
                        Text(stringResource_(R.string.design_menu_title), fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = designMenu, onDismissRequest = { designMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource_(R.string.open_widget_design)) },
                            onClick = {
                                designMenu = false
                                ctx.startActivity(Intent(ctx, WidgetDesignActivity::class.java))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource_(R.string.open_custom_editor)) },
                            onClick = {
                                designMenu = false
                                ctx.startActivity(Intent(ctx, CustomWidgetEditorActivity::class.java))
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(stringResource_(R.string.offline_note), color = OkColor, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Fajr · Dhuhr · Asr · Maghrib · Isha", color = DimColor.copy(alpha = 0.5f), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun OverviewCard(ctx: android.content.Context, refreshKey: Int) {
    val pal = remember(refreshKey) { Theme.current(ctx) }
    val day = remember(refreshKey) { PrayerData.cached(ctx) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val next = remember(now, refreshKey) { PrayerData.next(day, now) }
    val loc = remember(refreshKey) { PrayerData.location(ctx) }

    val chromeBmp = remember(next.index, refreshKey) {
        WidgetArt.renderNextChrome(ctx, next, pal, Settings.fontStyle(ctx), Settings.counterStyle(ctx), 520, 170)
    }
    val rowsBmp = remember(refreshKey) {
        WidgetArt.renderRows(ctx, day, next, pal, Settings.fontStyle(ctx), Settings.rowStyle(ctx), 260, 300)
    }
    val compassBmp = remember(refreshKey) {
        CompassArt.staticBitmap(Settings.compassShape(ctx), pal, PrayerData.qiblaBearing(loc.lat, loc.lon).toFloat(), 190)
    }
    val remainMs = (next.atMillis - now).coerceAtLeast(0L)
    val countdown = String.format(
        java.util.Locale.US, "%02d:%02d:%02d",
        remainMs / 3_600_000, (remainMs % 3_600_000) / 60_000, (remainMs % 60_000) / 1_000
    )

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, LiquidGlass.borderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // ١) توقيت الأذان القادم فوق — اسم الصلاة مرسوم أصلاً داخل الصورة، هون فقط نضيف أرقام العدّاد فوق إطاره
            Box(Modifier.fillMaxWidth().height(100.dp)) {
                Image(chromeBmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                Text(
                    countdown, color = Color(pal.text), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            // ٢) تحته: بوصلة القبلة (يمين) وعلى يسارها مواقيت الصلاة
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.width(96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(compassBmp.asImageBitmap(), null, modifier = Modifier.size(90.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${stringResource_(R.string.qibla)} ${PrayerData.qiblaBearing(loc.lat, loc.lon).roundToInt()}°",
                        color = DimColor, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Image(rowsBmp.asImageBitmap(), null, modifier = Modifier.weight(1f).height(150.dp))
            }
        }
    }
}

private data class ReadyWidget(val labelRes: Int, val provider: Class<*>)

private val READY_WIDGETS = listOf(
    ReadyWidget(R.string.widget_kind_classic, PrayerWidgetProvider::class.java),
    ReadyWidget(R.string.widget_kind_full, PrayerWidgetFullProvider::class.java),
    ReadyWidget(R.string.widget_kind_compact, PrayerWidgetCompactProvider::class.java),
    ReadyWidget(R.string.widget_kind_circle, PrayerWidgetCircleProvider::class.java),
    ReadyWidget(R.string.widget_kind_list, PrayerWidgetListProvider::class.java),
    ReadyWidget(R.string.widget_kind_minimal, PrayerWidgetMinimalProvider::class.java)
)

@Composable
private fun ReadyWidgetsGallery(ctx: android.content.Context, refreshKey: Int) {
    val pal = remember(refreshKey) { Theme.current(ctx) }
    val day = remember(refreshKey) { PrayerData.cached(ctx) }
    val next = remember(refreshKey) { PrayerData.next(day, System.currentTimeMillis()) }
    val loc = remember(refreshKey) { PrayerData.location(ctx) }
    val rowsBmp = remember(refreshKey) { WidgetArt.renderRows(ctx, day, next, pal, Settings.fontStyle(ctx), Settings.rowStyle(ctx), 160, 190) }
    val chromeBmp = remember(refreshKey) { WidgetArt.renderNextChrome(ctx, next, pal, Settings.fontStyle(ctx), Settings.counterStyle(ctx), 220, 110) }
    val compassBmp = remember(refreshKey) { CompassArt.staticBitmap(Settings.compassShape(ctx), pal, PrayerData.qiblaBearing(loc.lat, loc.lon).toFloat(), 140) }

    Row(Modifier.fillMaxWidth().rtlAwareHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (kind in READY_WIDGETS) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                border = BorderStroke(1.dp, LiquidGlass.borderBrush()),
                modifier = Modifier.width(128.dp)
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(108.dp, 74.dp), contentAlignment = Alignment.Center) {
                        when (kind.provider) {
                            PrayerWidgetCircleProvider::class.java ->
                                Image(compassBmp.asImageBitmap(), null, modifier = Modifier.size(64.dp))
                            PrayerWidgetListProvider::class.java ->
                                Image(rowsBmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                            PrayerWidgetCompactProvider::class.java, PrayerWidgetMinimalProvider::class.java ->
                                Image(chromeBmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().height(50.dp))
                            else -> Row {
                                Image(rowsBmp.asImageBitmap(), null, modifier = Modifier.width(56.dp).fillMaxHeight())
                                Spacer(Modifier.width(4.dp))
                                Image(chromeBmp.asImageBitmap(), null, modifier = Modifier.width(46.dp).fillMaxHeight())
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource_(kind.labelRes), color = TextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { requestPinWidget(ctx, kind.provider) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource_(R.string.add_to_home), color = AccentColor, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun requestPinWidget(ctx: android.content.Context, providerClass: Class<*>) {
    val mgr = ctx.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
    val provider = ComponentName(ctx, providerClass)
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(ctx, ctx.getString(R.string.pin_widget_unsupported), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun stringResource_(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringResource_(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)
