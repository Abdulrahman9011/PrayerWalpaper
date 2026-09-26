package com.rahmo.prayerwallpaper

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val BgColor = Color(0xFF0B1220)
private val CardColor = Color(0xFF16213A)
private val AccentColor = Color(0xFFE8B24C)
private val TextColor = Color(0xFFF2E9D8)
private val DimColor = Color(0xFF9AA6BE)

/** محرر السحب الحر: يبدأ بصورة خلفية أو لون، بعدين المستخدم يضيف عناصر (مواقيت/عداد/بوصلة/مدينة) ويسحبها بأي مكان. */
class CustomWidgetEditorActivity : ComponentActivity() {

    private var pendingPick: ((Uri) -> Unit)? = null
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            pendingPick?.invoke(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EditorRoot(
                ctx = this,
                onPickImage = { cb -> pendingPick = cb; pickImage.launch("image/*") },
                onDone = { finish() }
            )
        }
    }
}

/** إحدى قياسات الودجة الجاهزة يقدر المستخدم يختار عليها تصميمو (مطابقة لأحجام الودجات الحقيقية). */
private data class SizePreset(val labelRes: Int, val wDp: Int, val hDp: Int)
private val SIZE_PRESETS = listOf(
    SizePreset(R.string.widget_kind_compact, 180, 110),
    SizePreset(R.string.widget_kind_circle, 150, 150),
    SizePreset(R.string.widget_kind_classic, 250, 140),
    SizePreset(R.string.widget_kind_list, 200, 200),
    SizePreset(R.string.widget_kind_full, 280, 170)
)

@Composable
private fun EditorRoot(ctx: Context, onPickImage: ((Uri) -> Unit) -> Unit, onDone: () -> Unit) {
    val design = remember { CustomWidgetStore.load(ctx) }
    var version by remember { mutableStateOf(0) } // نزيدها لإجبار إعادة الرسم بعد أي تغيير هيكلي (إضافة/حذف/خلفية/أبعاد)
    var selected by remember { mutableStateOf<Int?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(background = BgColor, surface = CardColor, primary = AccentColor)) {
        Surface(Modifier.fillMaxSize(), color = BgColor) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(stringResource_(R.string.custom_editor_title), color = AccentColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource_(R.string.custom_editor_hint), color = DimColor, fontSize = 13.sp)

                Spacer(Modifier.height(18.dp))
                Text(stringResource_(R.string.custom_editor_bg), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.rtlAwareHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // "+" لاختيار صورة من المعرض — عند الاختيار تفعيل قسم الأبعاد تلقائياً (يظهر تحت مباشرة)
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(CardColor)
                            .border(1.dp, AccentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                onPickImage { uri ->
                                    design.bgUri = uri.toString()
                                    version++
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("+", color = AccentColor, fontSize = 24.sp, fontWeight = FontWeight.Bold) }

                    for (color in Theme.FLAT_COLORS_16) {
                        val isSel = design.bgUri == null && design.bgColor == color
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(color))
                                .border(2.dp, if (isSel) AccentColor else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { design.bgUri = null; design.bgColor = color; version++ }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(stringResource_(R.string.custom_editor_size), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.rtlAwareHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in SIZE_PRESETS) {
                        val isSel = design.widthDp == preset.wDp && design.heightDp == preset.hDp
                        Button(
                            onClick = { design.widthDp = preset.wDp; design.heightDp = preset.hDp; version++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) AccentColor else CardColor,
                                contentColor = if (isSel) Color(0xFF14100A) else TextColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(stringResource_(preset.labelRes), fontSize = 12.sp) }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(stringResource_(R.string.custom_editor_elements), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource_(R.string.custom_editor_drag_hint), color = DimColor, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))

                key(version) {
                    DesignCanvas(ctx, design, selected, onSelect = { selected = it }, onStructuralChange = { version++ })
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.rtlAwareHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddChip(stringResource_(R.string.el_times)) { design.elements.add(CustomWidgetStore.Element(CustomWidgetStore.ElementType.TIMES, 0.5f, 0.5f)); version++ }
                    AddChip(stringResource_(R.string.el_countdown)) { design.elements.add(CustomWidgetStore.Element(CustomWidgetStore.ElementType.COUNTDOWN, 0.5f, 0.3f)); version++ }
                    AddChip(stringResource_(R.string.el_compass)) { design.elements.add(CustomWidgetStore.Element(CustomWidgetStore.ElementType.COMPASS, 0.5f, 0.5f)); version++ }
                    AddChip(stringResource_(R.string.el_city)) { design.elements.add(CustomWidgetStore.Element(CustomWidgetStore.ElementType.CITY, 0.5f, 0.9f)); version++ }
                }

                if (selected != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            selected?.let { idx -> if (idx in design.elements.indices) design.elements.removeAt(idx) }
                            selected = null; version++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource_(R.string.el_remove_selected), color = Color(0xFFE8746C)) }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        CustomWidgetStore.save(ctx, design)
                        PrayerWidgetHub.updateAllWidgets(ctx)
                        val mgr = ctx.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
                        val provider = ComponentName(ctx, PrayerWidgetCustomProvider::class.java)
                        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(provider, null, null)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF14100A))
                ) { Text(stringResource_(R.string.custom_editor_save), fontWeight = FontWeight.Bold, fontSize = 16.sp) }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text("+ $label", color = AccentColor, fontSize = 12.sp)
    }
}

@Composable
private fun DesignCanvas(
    ctx: Context,
    design: CustomWidgetStore.Design,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onStructuralChange: () -> Unit
) {
    val density = LocalDensity.current
    val aspect = design.widthDp.toFloat() / design.heightDp.toFloat()

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(18.dp))
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val wPxI = wPx.toInt().coerceAtLeast(10); val hPxI = hPx.toInt().coerceAtLeast(10)

        val bgBmp = remember(design.bgUri, wPxI, hPxI) {
            design.bgUri?.let {
                try { WidgetArt.loadCustomBackground(ctx.contentResolver, Uri.parse(it), wPxI, hPxI) } catch (e: Exception) { null }
            }
        }
        if (bgBmp != null) {
            Image(bgBmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(design.bgColor)))
        }

        val pal = if (bgBmp != null) Theme.of(Settings.BgTheme.NIGHT_GOLD) else Theme.fromFlatColor(design.bgColor)
        val day = remember { PrayerData.cached(ctx) }
        val next = remember { PrayerData.next(day, System.currentTimeMillis()) }
        val loc = remember { PrayerData.location(ctx) }

        design.elements.forEachIndexed { idx, el ->
            var xFrac by remember(idx, design.elements.size) { mutableStateOf(el.xFrac) }
            var yFrac by remember(idx, design.elements.size) { mutableStateOf(el.yFrac) }

            val (ewI, ehI) = when (el.type) {
                CustomWidgetStore.ElementType.TIMES -> (wPx * 0.6f).toInt() to (hPx * 0.6f).toInt()
                CustomWidgetStore.ElementType.COUNTDOWN -> (wPx * 0.68f).toInt() to (hPx * 0.34f).toInt()
                CustomWidgetStore.ElementType.COMPASS -> { val s = (minOf(wPx, hPx) * 0.4f).toInt(); s to s }
                CustomWidgetStore.ElementType.CITY -> (wPx * 0.5f).toInt() to (hPx * 0.14f).toInt()
            }
            val ew = ewI.coerceAtLeast(20); val eh = ehI.coerceAtLeast(20)

            val bmp = remember(el.type, ew, eh, design.bgUri, design.bgColor) {
                when (el.type) {
                    CustomWidgetStore.ElementType.TIMES ->
                        WidgetArt.renderRows(ctx, day, next, pal, Settings.fontStyle(ctx), Settings.rowStyle(ctx), ew, eh)
                    CustomWidgetStore.ElementType.COUNTDOWN ->
                        WidgetArt.renderNextChrome(ctx, next, pal, Settings.fontStyle(ctx), Settings.counterStyle(ctx), ew, eh)
                    CustomWidgetStore.ElementType.COMPASS ->
                        WidgetRenderer.buildCompassBitmap(ctx, PrayerData.qiblaBearing(loc.lat, loc.lon), ew)
                    CustomWidgetStore.ElementType.CITY -> null
                }
            }

            val offX = (xFrac * wPx - ew / 2f).roundToInt()
            val offY = (yFrac * hPx - eh / 2f).roundToInt()

            Box(
                Modifier
                    .offset { IntOffset(offX, offY) }
                    .size(with(density) { ew.toDp() }, with(density) { eh.toDp() })
                    .border(2.dp, if (selected == idx) AccentColor else Color.Transparent, RoundedCornerShape(8.dp))
                    .pointerInput(idx, wPx, hPx) {
                        detectDragGestures(
                            onDragStart = { onSelect(idx) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                xFrac = ((xFrac * wPx + dragAmount.x) / wPx).coerceIn(0.06f, 0.94f)
                                yFrac = ((yFrac * hPx + dragAmount.y) / hPx).coerceIn(0.06f, 0.94f)
                                el.xFrac = xFrac; el.yFrac = yFrac
                            }
                        )
                    }
                    .clickable { onSelect(idx) },
                contentAlignment = Alignment.Center
            ) {
                if (el.type == CustomWidgetStore.ElementType.CITY) {
                    Text(loc.city, color = Color(pal.dim), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else if (bmp != null) {
                    Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun stringResource_(id: Int): String = androidx.compose.ui.res.stringResource(id)
