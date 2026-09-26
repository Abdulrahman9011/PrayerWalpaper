package com.rahmo.prayerwallpaper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFF0B1220)
private val CardColor = Color(0xFF16213A)
private val AccentColor = Color(0xFFE8B24C)
private val TextColor = Color(0xFFF2E9D8)
private val DimColor = Color(0xFF9AA6BE)

private enum class Category { BG, ROWS, COUNTER, FONT, COMPASS }

class WidgetDesignActivity : ComponentActivity() {

    private var onImagePicked: ((Uri) -> Unit)? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* بعض المزوّدات لا تدعم صلاحية دائمة — الصورة تبقى تشتغل بهذه الجلسة على الأقل */ }
            onImagePicked?.invoke(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DesignScreen(
                onPickImage = { cb -> onImagePicked = cb; pickImage.launch("image/*") },
                onApplied = {
                    PrayerWidgetHub.updateAllWidgets(this)
                }
            )
        }
    }
}

@Composable
private fun DesignScreen(onPickImage: ((Uri) -> Unit) -> Unit, onApplied: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var activeCategory by remember { mutableStateOf<Category?>(null) }
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun refresh() { tick++; onApplied() }

    MaterialTheme(colorScheme = darkColorScheme(background = BgColor, surface = CardColor, primary = AccentColor)) {
        Surface(Modifier.fillMaxSize(), color = BgColor) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.design_title), color = AccentColor, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
                )

                key(tick) { LivePreview(ctx) }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    CategoryRow(stringResource(R.string.cat_bg), currentBgLabel(ctx)) {
                        activeCategory = if (activeCategory == Category.BG) null else Category.BG
                    }
                    if (activeCategory == Category.BG) BgPicker(ctx, onPickImage) { refresh() }

                    CategoryRow(stringResource(R.string.cat_rows), rowLabel(ctx, Settings.rowStyle(ctx))) {
                        activeCategory = if (activeCategory == Category.ROWS) null else Category.ROWS
                    }
                    if (activeCategory == Category.ROWS) RowsPicker(ctx) { refresh() }

                    CategoryRow(stringResource(R.string.cat_counter), counterLabel(ctx, Settings.counterStyle(ctx))) {
                        activeCategory = if (activeCategory == Category.COUNTER) null else Category.COUNTER
                    }
                    if (activeCategory == Category.COUNTER) CounterPicker(ctx) { refresh() }

                    CategoryRow(stringResource(R.string.cat_font), fontLabel(ctx, Settings.fontStyle(ctx))) {
                        activeCategory = if (activeCategory == Category.FONT) null else Category.FONT
                    }
                    if (activeCategory == Category.FONT) FontPicker(ctx) { refresh() }

                    CategoryRow(stringResource(R.string.cat_compass), shapeLabel(ctx, Settings.compassShape(ctx))) {
                        activeCategory = if (activeCategory == Category.COMPASS) null else Category.COMPASS
                    }
                    if (activeCategory == Category.COMPASS) CompassPicker(ctx) { refresh() }

                    Spacer(Modifier.height(20.dp))
                    ShareCodeCard(ctx, tick, clipboard) { refresh() }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LivePreview(ctx: Context) {
    val pal = Theme.current(ctx)
    val day = remember { PrayerData.cached(ctx) }
    val next = remember { PrayerData.next(day, System.currentTimeMillis()) }
    val loc = remember { PrayerData.location(ctx) }

    val rowsBmp = remember(Settings.bgTheme(ctx), Settings.rowStyle(ctx), Settings.fontStyle(ctx)) {
        WidgetArt.renderRows(ctx, day, next, pal, Settings.fontStyle(ctx), Settings.rowStyle(ctx), 340, 260)
    }
    val chromeBmp = remember(Settings.bgTheme(ctx), Settings.counterStyle(ctx), Settings.fontStyle(ctx)) {
        WidgetArt.renderNextChrome(ctx, next, pal, Settings.fontStyle(ctx), Settings.counterStyle(ctx), 260, 150)
    }
    val compassBmp = remember(Settings.bgTheme(ctx), Settings.compassShape(ctx)) {
        CompassArt.staticBitmap(Settings.compassShape(ctx), pal, PrayerData.qiblaBearing(loc.lat, loc.lon).toFloat(), 200)
    }
    val bgBmp: Bitmap? = remember(Settings.useCustomBg(ctx), Settings.customBgUri(ctx)) {
        if (Settings.useCustomBg(ctx)) {
            Settings.customBgUri(ctx)?.let {
                try { WidgetArt.loadCustomBackground(ctx.contentResolver, Uri.parse(it), 700, 340) } catch (e: Exception) { null }
            }
        } else null
    }

    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(pal.bg))
    ) {
        if (bgBmp != null) {
            Image(bgBmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxSize().padding(14.dp)) {
            Image(
                rowsBmp.asImageBitmap(), null,
                modifier = Modifier.weight(1.1f).fillMaxHeight()
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.BottomCenter) {
                    Image(chromeBmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                    Text("00:32:10", color = Color(pal.text), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Image(compassBmp.asImageBitmap(), null, modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, valueLabel: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(valueLabel, color = AccentColor, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("›", color = DimColor, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun PickerRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rtlAwareHorizontalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun Tile(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Box(
            Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) AccentColor.copy(alpha = 0.18f) else CardColor)
                .then(if (selected) Modifier.border(2.dp, AccentColor, RoundedCornerShape(14.dp)) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) AccentColor else DimColor, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun BgPicker(ctx: Context, onPickImage: ((Uri) -> Unit) -> Unit, onChanged: () -> Unit) {
    PickerRow {
        for (theme in Settings.BgTheme.entries) {
            val pal = Theme.of(theme)
            Tile(selected = !Settings.useCustomBg(ctx) && !Settings.useFlatColor(ctx) && Settings.bgTheme(ctx) == theme, label = themeLabel(ctx, theme), onClick = {
                Settings.setBgTheme(ctx, theme); onChanged()
            }) {
                Box(Modifier.fillMaxSize(0.8f).clip(RoundedCornerShape(10.dp)).background(Color(pal.bg)))
            }
        }
        Tile(selected = Settings.useCustomBg(ctx), label = stringResource(R.string.bg_custom_photo), onClick = {
            onPickImage { uri ->
                Settings.setCustomBg(ctx, uri.toString())
                onChanged()
            }
        }) {
            Text("+", color = AccentColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.bg_flat_colors_label), color = DimColor, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    PickerRow {
        for (color in Theme.FLAT_COLORS_16) {
            Tile(selected = Settings.useFlatColor(ctx) && Settings.flatColor(ctx) == color, label = "", onClick = {
                Settings.setFlatColor(ctx, color); onChanged()
            }) {
                Box(
                    Modifier.fillMaxSize(0.8f).clip(RoundedCornerShape(10.dp))
                        .background(Color(color)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun RowsPicker(ctx: Context, onChanged: () -> Unit) {
    PickerRow {
        for (style in Settings.RowStyle.entries) {
            Tile(selected = Settings.rowStyle(ctx) == style, label = rowLabel(ctx, style), onClick = {
                Settings.setRowStyle(ctx, style); onChanged()
            }) {
                Text("≡", color = AccentColor, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun CounterPicker(ctx: Context, onChanged: () -> Unit) {
    PickerRow {
        for (style in Settings.CounterStyle.entries) {
            Tile(selected = Settings.counterStyle(ctx) == style, label = counterLabel(ctx, style), onClick = {
                Settings.setCounterStyle(ctx, style); onChanged()
            }) {
                Text("⏱", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun FontPicker(ctx: Context, onChanged: () -> Unit) {
    PickerRow {
        for (f in Settings.FontStyle.entries) {
            Tile(selected = Settings.fontStyle(ctx) == f, label = fontLabel(ctx, f), onClick = {
                Settings.setFontStyle(ctx, f); onChanged()
            }) {
                Text("Aa", color = TextColor, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun CompassPicker(ctx: Context, onChanged: () -> Unit) {
    val pal = Theme.current(ctx)
    PickerRow {
        for (shape in Settings.CompassShape.entries) {
            val bmp = remember(shape) { CompassArt.staticBitmap(shape, pal, 0f, 140) }
            Tile(selected = Settings.compassShape(ctx) == shape, label = shapeLabel(ctx, shape), onClick = {
                Settings.setCompassShape(ctx, shape); onChanged()
            }) {
                Image(bmp.asImageBitmap(), null, modifier = Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun ShareCodeCard(ctx: Context, tick: Int, clipboard: ClipboardManager, onApplied: () -> Unit) {
    var codeInput by remember { mutableStateOf("") }
    val currentCode = remember(tick) { WidgetDesignCode.currentCode(ctx) }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.design_code_label), color = DimColor, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentCode, color = AccentColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("design_code", currentCode))
                    Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.copy), color = TextColor) }
            }
            if (Settings.useCustomBg(ctx)) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.design_code_photo_note), color = DimColor, fontSize = 11.sp)
            }

            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.design_code_apply_label), color = DimColor, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeInput, onValueChange = { codeInput = it.filter { ch -> ch.isDigit() }.take(8) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("00000000", color = DimColor) }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (WidgetDesignCode.apply(ctx, codeInput)) {
                        onApplied()
                        Toast.makeText(ctx, ctx.getString(R.string.design_code_applied), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, ctx.getString(R.string.design_code_invalid), Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF14100A))) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}

private fun currentBgLabel(ctx: Context) = when {
    Settings.useCustomBg(ctx) -> ctx.getString(R.string.bg_custom_photo)
    Settings.useFlatColor(ctx) -> ctx.getString(R.string.bg_flat_colors_label)
    else -> themeLabel(ctx, Settings.bgTheme(ctx))
}

private fun themeLabel(ctx: Context, v: Settings.BgTheme): String = when (v) {
    Settings.BgTheme.NIGHT_GOLD -> ctx.getString(R.string.theme_night_gold)
    Settings.BgTheme.DEEP_TEAL -> ctx.getString(R.string.theme_deep_teal)
    Settings.BgTheme.ROYAL_PURPLE -> ctx.getString(R.string.theme_royal_purple)
    Settings.BgTheme.EMERALD -> ctx.getString(R.string.theme_emerald)
    Settings.BgTheme.CRIMSON_DUSK -> ctx.getString(R.string.theme_crimson_dusk)
    Settings.BgTheme.OCEAN_BLUE -> ctx.getString(R.string.theme_ocean_blue)
    Settings.BgTheme.ROSE_QUARTZ -> ctx.getString(R.string.theme_rose_quartz)
}
private fun rowLabel(ctx: Context, v: Settings.RowStyle): String = when (v) {
    Settings.RowStyle.CARD -> ctx.getString(R.string.row_card)
    Settings.RowStyle.DIVIDED -> ctx.getString(R.string.row_divided)
    Settings.RowStyle.COMPACT -> ctx.getString(R.string.row_compact)
    Settings.RowStyle.BORDERED -> ctx.getString(R.string.row_bordered)
    Settings.RowStyle.MINIMAL -> ctx.getString(R.string.row_minimal)
}
private fun counterLabel(ctx: Context, v: Settings.CounterStyle): String = when (v) {
    Settings.CounterStyle.DIGITAL_CARD -> ctx.getString(R.string.counter_digital_card)
    Settings.CounterStyle.RING_PROGRESS -> ctx.getString(R.string.counter_ring_progress)
    Settings.CounterStyle.PLAIN_TEXT -> ctx.getString(R.string.counter_plain_text)
    Settings.CounterStyle.PILL -> ctx.getString(R.string.counter_pill)
    Settings.CounterStyle.OUTLINE -> ctx.getString(R.string.counter_outline)
}
private fun fontLabel(ctx: Context, v: Settings.FontStyle): String = when (v) {
    Settings.FontStyle.DEFAULT -> ctx.getString(R.string.font_default)
    Settings.FontStyle.SERIF -> ctx.getString(R.string.font_serif)
    Settings.FontStyle.MONOSPACE -> ctx.getString(R.string.font_monospace)
    Settings.FontStyle.CONDENSED -> ctx.getString(R.string.font_condensed)
    Settings.FontStyle.ROUNDED -> ctx.getString(R.string.font_rounded)
}
private fun shapeLabel(ctx: Context, v: Settings.CompassShape): String = when (v) {
    Settings.CompassShape.CLASSIC -> ctx.getString(R.string.shape_classic)
    Settings.CompassShape.MODERN_RING -> ctx.getString(R.string.shape_modern_ring)
    Settings.CompassShape.MINIMAL_ARROW -> ctx.getString(R.string.shape_minimal_arrow)
    Settings.CompassShape.ARC_GAUGE -> ctx.getString(R.string.shape_arc_gauge)
    Settings.CompassShape.TRIANGLE_POINTER -> ctx.getString(R.string.shape_triangle_pointer)
}
