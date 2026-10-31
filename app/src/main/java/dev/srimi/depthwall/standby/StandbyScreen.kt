package dev.srimi.depthwall.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.ui.DepthTheme
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private val NightRed = Color(0xFFEB5145)

/** Broadcast-driven: no per-frame or per-second clock loop, including in the dream host. */
@Composable private fun rememberStandbyClock(): StandbyClock {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var clock by remember { mutableStateOf(StandbyClock(ZonedDateTime.now(), DateFormat.is24HourFormat(context))) }
    LifecycleResumeEffect(context, configuration) {
        fun refresh() { clock = StandbyClock(ZonedDateTime.now(), DateFormat.is24HourFormat(context)) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        refresh()
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        onPauseOrDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return clock
}

private data class StandbyClock(val now: ZonedDateTime, val use24Hour: Boolean)

@Composable fun StandbySession(
    repository: Repository,
    preview: Boolean,
    onExit: () -> Unit,
    onNightChanged: (Boolean) -> Unit,
) {
    val settings by repository.standbySettings.flow.collectAsStateWithLifecycle<StandbySettings?>(null)
    val appSettings by repository.settings.flow.collectAsStateWithLifecycle(AppSettings())
    val clock = rememberStandbyClock()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var notice by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var powered by remember { mutableStateOf(false) }
    LifecycleResumeEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                battery = if (level >= 0 && scale > 0) (100L * level / scale).toInt().coerceIn(0, 100) else null
                powered = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { receiver.onReceive(context, it) }
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
    val selected = settings
    val photo by produceState(StandbyPhoto.DEFAULT_MODEL, selected?.photoWallpaperId) {
        value = withContext(Dispatchers.IO) {
            StandbyPhoto.model(selected?.photoWallpaperId?.let { repository.dao.find(it) })
        }
    }
    LaunchedEffect(selected?.enabled, preview) {
        if (selected != null && !selected.enabled && !preview) onExit()
    }
    LaunchedEffect(controls, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (controls) { delay(5_000); controls = false }
        }
    }
    fun choose(design: StandbyDesign) {
        scope.launch {
            try { repository.standbySettings.setDesign(design); notice = null }
            catch (_: java.io.IOException) { notice = "Could not save this design. Please try again."; controls = true }
        }
    }
    val night = StandbyPolicy.isNight(selected?.nightMode ?: StandbyNightMode.AUTO, clock.now.toLocalTime())
    val updateBrightness by rememberUpdatedState(onNightChanged)
    LifecycleResumeEffect(night) {
        updateBrightness(night)
        onPauseOrDispose { }
    }
    DepthTheme(if (night) "Papaya" else appSettings.appTheme) {
        val ink = if (night) NightRed else MaterialTheme.colorScheme.primary
        if (selected == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ink)
            }
        } else {
            val threshold = with(LocalDensity.current) { 64.dp.toPx() }
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("standby_screen")
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next design") { choose(selected.design.next(1)); true },
                        CustomAccessibilityAction("Previous design") { choose(selected.design.next(-1)); true },
                        CustomAccessibilityAction("Exit StandBy") { onExit(); true })
                }
                .pointerInput(Unit) { detectTapGestures { controls = !controls } }
                .pointerInput(selected.design) {
                    var distance = 0f
                    detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(distance) > threshold) choose(selected.design.next(if (distance < 0) 1 else -1))
                        }) { change, amount -> change.consume(); distance += amount }
                }) {
                // Keep the entire face inside its safe padding while moving a few pixels each minute.
                val minute = clock.now.toEpochSecond() / 60
                val driftX = (Math.floorMod(minute, 9L).toInt() - 4).dp
                val driftY = (Math.floorMod(Math.floorDiv(minute, 9L), 9L).toInt() - 4).dp
                StandbyFace(selected.design, clock.now, clock.use24Hour, locale, night,
                    modifier = Modifier.fillMaxSize().padding(20.dp).offset(driftX, driftY), photoModel = photo)
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (preview) "PREVIEW" else "STANDBY", color = ink.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(selected.design.label, color = ink.copy(alpha = .7f), fontSize = 11.sp)
                    if (powered) Icon(Icons.Outlined.Bolt, "Connected to power", Modifier.size(14.dp), tint = ink.copy(alpha = .7f))
                    battery?.let { Text("$it%", color = ink.copy(alpha = .7f), fontSize = 11.sp,
                        modifier = Modifier.semantics { contentDescription = "Battery $it percent" }) }
                    if (night) Text("NIGHT", color = ink.copy(alpha = .7f), fontSize = 10.sp)
                }
                if (controls) {
                    Row(Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(32.dp))
                        .background(if (night) Color(0xFF240907) else Color(0xED181716)),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { choose(selected.design.next(-1)) }, modifier = Modifier.testTag("standby_previous")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous design", tint = ink)
                        }
                        Text("Swipe for designs", color = ink, fontSize = 12.sp)
                        IconButton(onClick = { choose(selected.design.next(1)) }, modifier = Modifier.testTag("standby_next")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next design", tint = ink)
                        }
                        IconButton(onClick = onExit, modifier = Modifier.testTag("standby_exit")) {
                            Icon(Icons.Outlined.Close, "Exit StandBy", tint = ink)
                        }
                    }
                }
                notice?.let { Text(it, color = ink, modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)) }
            }
        }
    }
}

/** Shared by the activity, system screensaver and small design previews. */
@Composable internal fun StandbyFace(
    design: StandbyDesign,
    now: ZonedDateTime,
    use24Hour: Boolean,
    locale: Locale,
    night: Boolean,
    modifier: Modifier = Modifier,
    photoModel: String = StandbyPhoto.DEFAULT_MODEL,
) {
    val accent = if (night) NightRed else MaterialTheme.colorScheme.primary
    val text = if (night) NightRed else Color(0xFFF4EEE5)
    val hour = if (use24Hour) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val hourText = String.format(locale, if (use24Hour) "%02d" else "%d", hour)
    val minuteText = String.format(locale, "%02d", now.minute)
    val time = "$hourText:$minuteText"
    val period = if (use24Hour) "" else now.format(DateTimeFormatter.ofPattern("a", locale))
    val date = now.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale))
    BoxWithConstraints(modifier.testTag("standby_face_${design.name}").background(Color.Black)) {
        val faceWidth = maxWidth
        val faceHeight = maxHeight
        val size = minOf(faceWidth.value / 5.2f, faceHeight.value / 2.7f)
        when (design) {
            StandbyDesign.DIGITAL -> DigitalFace(time, period, date, accent, text, size)
            StandbyDesign.FLIP -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlipTile(hourText, accent, night, size * 1.12f)
                    Text(":", color = accent.copy(alpha = .6f), fontSize = fittedSp(size * .75f))
                    FlipTile(minuteText, accent, night, size * 1.12f)
                }
                Text(listOf(period, date).filter { it.isNotEmpty() }.joinToString(" · "), color = text.copy(alpha = .7f),
                    fontSize = fittedSp(maxOf(9f, size * .15f)), modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            }
            StandbyDesign.ANALOGUE -> Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly) {
                AnalogueClock(now, time, accent, text, minOf(faceHeight * .82f, faceWidth * .46f))
                Column(Modifier.widthIn(max = faceWidth * .4f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(now.dayOfWeek.getDisplayName(TextStyle.FULL, locale), color = text.copy(alpha = .6f),
                        fontSize = fittedSp(maxOf(10f, size * .22f)))
                    Text(String.format(locale, "%02d", now.dayOfMonth), color = accent,
                        fontSize = fittedSp(size * 1.05f), fontWeight = FontWeight.Light)
                    Text(now.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), color = text.copy(alpha = .7f),
                        fontSize = fittedSp(maxOf(10f, size * .2f)))
                    if (period.isNotEmpty()) Text(period, color = accent, fontSize = fittedSp(maxOf(10f, size * .17f)))
                }
            }
            StandbyDesign.CALENDAR -> Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) { DigitalFace(time, period,
                    now.dayOfWeek.getDisplayName(TextStyle.FULL, locale), accent, text, size * .5f) }
                VerticalDivider(Modifier.height(faceHeight * .72f), color = accent.copy(alpha = .18f))
                MonthGrid(now, locale, accent, text, Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp))
            }
            StandbyDesign.PHOTO -> if (night) {
                DigitalFace(time, period, date, accent, text, size)
            } else {
                OfflinePhoto(photoModel, Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)))
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .2f), Color.Black.copy(alpha = .35f), Color.Black.copy(alpha = .86f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(time, color = text, fontSize = fittedSp(size * 1.05f), fontWeight = FontWeight.Bold, maxLines = 1)
                        if (period.isNotEmpty()) Text(period, color = text, fontSize = fittedSp(size * .2f), modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
                    }
                    Text(date, color = text.copy(alpha = .85f), fontSize = fittedSp(maxOf(10f, size * .17f)))
                }
            }
        }
    }
}

@Composable private fun BoxScope.DigitalFace(time: String, period: String, date: String, accent: Color, text: Color, size: Float) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = accent, fontSize = fittedSp(size * 1.65f), lineHeight = fittedSp(size * 1.75f),
            fontWeight = FontWeight.ExtraLight, fontFamily = FontFamily.SansSerif, maxLines = 1)
        if (period.isNotEmpty()) Text(period, color = accent.copy(alpha = .7f), fontSize = fittedSp(maxOf(9f, size * .18f)), letterSpacing = 2.sp)
        Text(date, color = text.copy(alpha = .7f), fontSize = fittedSp(maxOf(10f, size * .2f)),
            modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
    }
}

@Composable private fun FlipTile(value: String, accent: Color, night: Boolean, size: Float) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (night) Color(0xFF1E0806) else Color(0xFF1C1A18))
        .padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(value, transitionSpec = {
            (slideInVertically(tween(180)) { it / 4 } + fadeIn(tween(180))) togetherWith
                (slideOutVertically(tween(180)) { -it / 4 } + fadeOut(tween(180)))
        }, label = "Minute flip") { number ->
            Text(number, color = accent, fontSize = fittedSp(size), lineHeight = fittedSp(size * 1.2f),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
        Canvas(Modifier.matchParentSize()) {
            drawLine(Color.Black.copy(alpha = .8f), Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), 2.dp.toPx())
        }
    }
}

@Composable private fun AnalogueClock(now: ZonedDateTime, time: String, accent: Color, text: Color, diameter: Dp) {
    Box(Modifier.size(diameter).semantics { contentDescription = "Analogue clock, $time" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(accent.copy(alpha = .15f), radius * .97f, style = Stroke(2.dp.toPx()))
            repeat(60) { tick ->
                val angle = Math.toRadians(tick * 6.0 - 90)
                val outer = radius * .9f
                val inner = radius * if (tick % 5 == 0) .8f else .86f
                drawLine(if (tick % 5 == 0) text.copy(alpha = .6f) else accent.copy(alpha = .25f),
                    center + Offset(cos(angle).toFloat() * inner, sin(angle).toFloat() * inner),
                    center + Offset(cos(angle).toFloat() * outer, sin(angle).toFloat() * outer),
                    if (tick % 5 == 0) 2.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
            }
            fun hand(degrees: Double, length: Float, width: Float, colour: Color) {
                val angle = Math.toRadians(degrees - 90)
                drawLine(colour, center, center + Offset(cos(angle).toFloat() * radius * length,
                    sin(angle).toFloat() * radius * length), width.dp.toPx(), StrokeCap.Round)
            }
            hand((now.hour % 12) * 30.0 + now.minute * .5, .46f, 5f, text)
            hand(now.minute * 6.0, .7f, 3f, accent)
            drawCircle(accent, 5.dp.toPx())
        }
    }
}

@Composable private fun MonthGrid(now: ZonedDateTime, locale: Locale, accent: Color, text: Color, modifier: Modifier) {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val month = now.toLocalDate().withDayOfMonth(1)
    val offset = Math.floorMod(month.dayOfWeek.value - firstDay.value, 7)
    BoxWithConstraints(modifier) {
        val textSize = minOf(maxHeight.value / 15f, maxWidth.value / 18f).coerceAtLeast(7f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), color = accent,
                fontSize = fittedSp(textSize * 1.2f), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                repeat(7) { i ->
                    val day = DayOfWeek.of((firstDay.value - 1 + i) % 7 + 1)
                    Text(day.getDisplayName(TextStyle.NARROW_STANDALONE, locale), color = text.copy(alpha = .45f),
                        fontSize = fittedSp(textSize * .8f), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { weekday ->
                        val day = week * 7 + weekday - offset + 1
                        val today = day == now.dayOfMonth
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (today) Canvas(Modifier.fillMaxSize()) { drawCircle(accent.copy(alpha = .2f), size.minDimension * .46f) }
                            if (day in 1..month.lengthOfMonth()) Text(String.format(locale, "%d", day),
                                color = if (today) accent else text.copy(alpha = .65f), fontSize = fittedSp(textSize),
                                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun OfflinePhoto(model: String, modifier: Modifier) {
    var failed by remember(model) { mutableStateOf(false) }
    val context = LocalContext.current
    val source = if (failed) StandbyPhoto.DEFAULT_MODEL else model
    val request = remember(source) { ImageRequest.Builder(context).data(source).size(1600, 900).build() }
    AsyncImage(request, "Selected offline wallpaper", modifier.testTag("standby_photo"), contentScale = ContentScale.Crop,
        onError = { if (source != StandbyPhoto.DEFAULT_MODEL) failed = true })
}

/** Fit the whole clock on narrow/large-font windows without truncating the actual time. */
@Composable private fun fittedSp(size: Float) = (size / LocalDensity.current.fontScale).sp
