package com.rahmo.prayerwallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel

private val BgColor = LiquidGlass.DeepSpace
private val CardColor = LiquidGlass.GlassFillStrong
private val AccentColor = LiquidGlass.Gold
private val TextColor = LiquidGlass.TextPrimary
private val DimColor = LiquidGlass.TextDim
private val OkColor = LiquidGlass.Ok
private val ErrColor = LiquidGlass.Err

enum class LocationStatus { IDLE, LOCATING, FETCHING, OK, DENIED, LOCATION_OFF, FAILED }

class SettingsViewModel : ViewModel() {
    var status by mutableStateOf(LocationStatus.IDLE)
    var city by mutableStateOf<String?>(null)
    var tick by mutableStateOf(0)
}

/** شاشة الإعدادات: الموقع، سرعة البوصلة، طريقة الحساب، إشعار الأذان، ومظهر التطبيق. */
class SettingsActivity : ComponentActivity() {

    private val vm: SettingsViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) locateAndFetch() else vm.status = LocationStatus.DENIED
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* بغض النظر عن النتيجة، الإعداد بقي كما اختاره المستخدم */ }

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        Settings.setRingtoneUri(this, uri?.toString())
        vm.tick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PrayerData.hasLocation(this)) {
            vm.city = PrayerData.location(this).city
        }
        setContent {
            SettingsRoot(
                vm = vm,
                onLocate = ::onLocateClick,
                onSetWallpaper = ::onSetWallpaperClick,
                onSaveManualLocation = ::onSaveManualLocation,
                onPickRingtone = ::onPickRingtone,
                onSettingsChanged = ::onSettingsChanged,
                onBack = { finish() }
            )
        }
    }

    private fun onSettingsChanged() {
        try { PrayerData.recompute(this) } catch (e: Exception) {}
        try { AdhanScheduler.scheduleAll(this) } catch (e: Exception) {}
        try { PrayerWidgetHub.updateAllWidgets(this) } catch (e: Exception) {}
        vm.tick++
        try {
            if (Settings.adhanNotifEnabled(this) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (e: Exception) {}
    }

    private fun onPickRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            val current = Settings.ringtoneUri(this@SettingsActivity)
            if (current != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
        }
        ringtoneLauncher.launch(intent)
    }

    private fun onSaveManualLocation(lat: Double, lon: Double, city: String) {
        Settings.setManualLocation(this, lat, lon, city.ifBlank { getString(R.string.default_city) })
        vm.city = Settings.manualCity(this)
        vm.status = LocationStatus.OK
        onSettingsChanged()
    }

    private fun onLocateClick() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            locateAndFetch()
        } else {
            permLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun locateAndFetch() {
        Settings.clearManualLocation(this)
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val enabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) { false }
        if (!enabled) { vm.status = LocationStatus.LOCATION_OFF; return }
        vm.status = LocationStatus.LOCATING

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            try {
                if (lm.isProviderEnabled(p)) {
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (best == null || l.accuracy < best!!.accuracy)) best = l
                }
            } catch (e: SecurityException) {}
        }
        if (best != null) { onLocationReady(best); return }

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                try { lm.removeUpdates(this) } catch (e: Exception) {}
                onLocationReady(location)
            }
        }
        try {
            for (p in providers) {
                if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) { vm.status = LocationStatus.DENIED; return }
        Handler(Looper.getMainLooper()).postDelayed({
            if (vm.status == LocationStatus.LOCATING) {
                try { lm.removeUpdates(listener) } catch (e: Exception) {}
                vm.status = LocationStatus.FAILED
            }
        }, 10_000L)
    }

    private fun onLocationReady(loc: Location) {
        vm.status = LocationStatus.FETCHING
        Thread {
            // اسم المدينة تحسين اختياري فقط — إن فشل (لا إنترنت) نعرض الإحداثيات، والتطبيق يشتغل طبيعي بدونه
            val city = PrayerData.reverseGeocode(loc.latitude, loc.longitude)
                ?: String.format(java.util.Locale.US, "%.3f, %.3f", loc.latitude, loc.longitude)
            PrayerData.saveLocation(this, loc.latitude, loc.longitude, city)
            runOnUiThread {
                vm.city = city
                vm.status = LocationStatus.OK
                onSettingsChanged()
            }
        }.start()
    }

    private fun onSetWallpaperClick() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, PrayerWallpaperService::class.java)
            )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.fetch_fail), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SettingsRoot(
    vm: SettingsViewModel,
    onLocate: () -> Unit,
    onSetWallpaper: () -> Unit,
    onSaveManualLocation: (Double, Double, String) -> Unit,
    onPickRingtone: () -> Unit,
    onSettingsChanged: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val decorative = Settings.decorativeAppBg(ctx)

    MaterialTheme(
        colorScheme = darkColorScheme(background = BgColor, surface = CardColor, primary = AccentColor, onBackground = TextColor)
    ) {
        LiquidGlassBackground(decorative = decorative) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource_(R.string.go_back), tint = AccentColor)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource_(R.string.settings_title), color = AccentColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))

                SectionCard(stringResource_(R.string.section_location)) {
                    val (label, color) = statusText(vm)
                    Text(label, color = color, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(vm.city ?: stringResource_(R.string.default_city), color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onLocate, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = LiquidGlass.GlassFillStrong, contentColor = AccentColor)) {
                            Text(stringResource_(R.string.btn_locate))
                        }
                        Button(onClick = onSetWallpaper, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF14100A))) {
                            Text(stringResource_(R.string.btn_set), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource_(R.string.manual_location_hint), color = DimColor, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    ManualLocationFields(onSaveManualLocation)
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(stringResource_(R.string.section_compass)) {
                    Text(stringResource_(R.string.compass_speed_label), color = DimColor, fontSize = 13.sp)
                    ChipRow(
                        options = Settings.CompassSpeed.entries.toList(),
                        selected = Settings.compassSpeed(ctx),
                        label = { speedLabel(ctx, it) }
                    ) { Settings.setCompassSpeed(ctx, it); onSettingsChanged() }
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(stringResource_(R.string.section_calc)) {
                    Text(stringResource_(R.string.calc_method_label), color = DimColor, fontSize = 13.sp)
                    ChipRow(Settings.CalcMethod.entries.toList(), Settings.calcMethod(ctx), { methodLabel(ctx, it) }) {
                        Settings.setCalcMethod(ctx, it); onSettingsChanged()
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource_(R.string.asr_method_label), color = DimColor, fontSize = 13.sp)
                    ChipRow(Settings.AsrMethod.entries.toList(), Settings.asrMethod(ctx), { asrLabel(ctx, it) }) {
                        Settings.setAsrMethod(ctx, it); onSettingsChanged()
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(stringResource_(R.string.section_adhan)) {
                    SwitchRow(stringResource_(R.string.adhan_notif_toggle), Settings.adhanNotifEnabled(ctx)) {
                        Settings.setAdhanNotifEnabled(ctx, it); onSettingsChanged()
                    }
                    SwitchRow(stringResource_(R.string.adhan_vibrate_toggle), Settings.vibrationEnabled(ctx)) {
                        Settings.setVibrationEnabled(ctx, it); onSettingsChanged()
                    }
                    SwitchRow(stringResource_(R.string.adhan_ringtone_toggle), Settings.ringtoneEnabled(ctx)) {
                        Settings.setRingtoneEnabled(ctx, it); onSettingsChanged()
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onPickRingtone,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, LiquidGlass.borderBrush()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource_(R.string.adhan_pick_ringtone), color = TextColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource_(R.string.adhan_no_music_note), color = DimColor, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(stringResource_(R.string.adhan_persistent_toggle), Settings.persistentAlarm(ctx)) {
                        Settings.setPersistentAlarm(ctx, it); onSettingsChanged()
                    }
                    Text(stringResource_(R.string.adhan_persistent_note), color = DimColor, fontSize = 11.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource_(R.string.reminder_label), color = DimColor, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = REMINDER_OPTIONS,
                        selected = Settings.reminderMinutes(ctx),
                        label = { reminderLabel(ctx, it) }
                    ) { Settings.setReminderMinutes(ctx, it); onSettingsChanged() }
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(stringResource_(R.string.section_app_look)) {
                    SwitchRow(stringResource_(R.string.app_look_decorative_toggle), decorative) {
                        Settings.setDecorativeAppBg(ctx, it); onSettingsChanged()
                    }
                    Text(stringResource_(R.string.app_look_decorative_note), color = DimColor, fontSize = 11.sp)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, LiquidGlass.borderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().rtlAwareHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (opt in options) {
            val isSel = opt == selected
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isSel) Modifier.background(AccentColor, shape)
                        else Modifier.liquidGlass(shape)
                    )
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    label(opt),
                    fontSize = 13.sp,
                    color = if (isSel) Color(0xFF14100A) else TextColor,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentColor, checkedTrackColor = AccentColor.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun ManualLocationFields(onSave: (Double, Double, String) -> Unit) {
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text(stringResource_(R.string.field_lat)) }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text(stringResource_(R.string.field_lon)) }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource_(R.string.field_city)) }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull()
            if (la != null && lo != null) onSave(la, lo, city)
        },
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, LiquidGlass.borderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource_(R.string.manual_location_save), color = TextColor) }
}

@Composable
private fun statusText(vm: SettingsViewModel): Pair<String, Color> = when (vm.status) {
    LocationStatus.IDLE -> stringResource_(R.string.no_location, PrayerData.location(LocalContext.current).city) to DimColor
    LocationStatus.LOCATING -> stringResource_(R.string.locating) to DimColor
    LocationStatus.FETCHING -> stringResource_(R.string.fetching) to DimColor
    LocationStatus.OK -> stringResource_(R.string.fetch_ok) to OkColor
    LocationStatus.DENIED -> stringResource_(R.string.location_denied) to ErrColor
    LocationStatus.LOCATION_OFF -> stringResource_(R.string.location_off) to ErrColor
    LocationStatus.FAILED -> stringResource_(R.string.fetch_fail) to ErrColor
}

private fun speedLabel(ctx: Context, v: Settings.CompassSpeed): String = when (v) {
    Settings.CompassSpeed.REALTIME -> ctx.getString(R.string.speed_realtime)
    Settings.CompassSpeed.FAST -> ctx.getString(R.string.speed_fast)
    Settings.CompassSpeed.NORMAL -> ctx.getString(R.string.speed_normal)
    Settings.CompassSpeed.SLOW -> ctx.getString(R.string.speed_slow)
}
private fun methodLabel(ctx: Context, v: Settings.CalcMethod): String = when (v) {
    Settings.CalcMethod.DIYANET -> ctx.getString(R.string.method_diyanet)
    Settings.CalcMethod.MWL -> ctx.getString(R.string.method_mwl)
    Settings.CalcMethod.ISNA -> ctx.getString(R.string.method_isna)
    Settings.CalcMethod.EGYPT -> ctx.getString(R.string.method_egypt)
    Settings.CalcMethod.KARACHI -> ctx.getString(R.string.method_karachi)
    Settings.CalcMethod.MAKKAH -> ctx.getString(R.string.method_makkah)
}
private fun asrLabel(ctx: Context, v: Settings.AsrMethod): String = when (v) {
    Settings.AsrMethod.STANDARD -> ctx.getString(R.string.asr_standard)
    Settings.AsrMethod.HANAFI -> ctx.getString(R.string.asr_hanafi)
}

private val REMINDER_OPTIONS = listOf(0, 5, 10, 15, 20)
private fun reminderLabel(ctx: Context, minutes: Int): String =
    if (minutes <= 0) ctx.getString(R.string.reminder_off)
    else ctx.getString(R.string.reminder_minutes_fmt, minutes)

@Composable
private fun stringResource_(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringResource_(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)
