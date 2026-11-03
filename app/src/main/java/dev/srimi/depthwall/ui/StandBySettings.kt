package dev.srimi.depthwall.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.srimi.depthwall.R
import dev.srimi.depthwall.clock.ClockFace
import dev.srimi.depthwall.clock.ClockFaces
import dev.srimi.depthwall.clock.ClockMath
import dev.srimi.depthwall.clock.ClockStyle
import dev.srimi.depthwall.data.StandBySettings
import dev.srimi.depthwall.standby.StandByActivity
import dev.srimi.depthwall.standby.StandByTileService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

private val SCENE_LABELS = mapOf("earth" to "Earth", "mars" to "Mars", "moon" to "Moon", "saturn" to "Saturn",
    "sun" to "Sun", "snow" to "Snow Mountain", "geometry" to "Geometry")
private val DIM_LABELS = mapOf(0 to "Never", 10 to "10 min", 30 to "30 min")

/** A Home card that opens StandBy's settings. */
@Composable fun StandByCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = Raised), shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().testTag("home_standby")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val time = rememberTicking()
            ClockPreview(ClockFaces.Bold, time, ClockStyle(is24h = DateFormat.is24HourFormat(LocalContext.current)),
                Modifier.fillMaxWidth().aspectRatio(2.6f).clip(RoundedCornerShape(16.dp)))
            Text("StandBy", style = MaterialTheme.typography.titleMedium)
            Text("Put the phone on its side on the charger for a full-screen clock: ${ClockFaces.standByIds.size - 1} faces, your planets, widgets and a red night mode.", color = Muted, fontSize = 12.sp)
        }
    }
}

/** Wait for saved preferences before editing, like the main settings dialog. */
@Composable fun StandBySettingsDialog(vm: MainViewModel, onClose: () -> Unit) {
    val loaded by produceState<StandBySettings?>(null, vm) {
        value = try { vm.repo.settings.standBy.first() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { StandBySettings() }
    }
    loaded?.let { StandByEditor(it, vm, onClose) }
        ?: AlertDialog(onDismissRequest = onClose, title = { Text("StandBy") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {}, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable private fun StandByEditor(initial: StandBySettings, vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var s by remember { mutableStateOf(initial) }
    var notice by remember { mutableStateOf<String?>(null) }
    val time = rememberTicking()
    val is24h = DateFormat.is24HourFormat(context)
    val faces = ClockFaces.all.filterNot { it.standByOnly }
    fun styleFor(night: Boolean) = ClockStyle(is24h = is24h, showSeconds = s.showSeconds, zones = s.zones).let { if (night) it.toNight() else it }
    AlertDialog(onDismissRequest = onClose, title = { Text("StandBy") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val selected = faces.firstOrNull { it.id == s.face } ?: ClockFaces.Bold
            ClockPreview(selected, time, styleFor(s.nightMode == "Always"),
                Modifier.fillMaxWidth().aspectRatio(2.17f).clip(RoundedCornerShape(14.dp)).testTag("standby_preview"))
            Text("Clock face", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(faces, key = { it.id }) { face ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ClockPreview(face, time, styleFor(false), Modifier.size(112.dp, 52.dp).clip(RoundedCornerShape(10.dp))
                            .border(BorderStroke(if (face == selected) 2.dp else 1.dp, if (face == selected) Orange else Hairline), RoundedCornerShape(10.dp))
                            .clickable { s = s.copy(face = face.id) }.testTag("standby_face_${face.id}"))
                        Text(face.id, fontSize = 11.sp, color = if (face == selected) Orange else Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Text("Swipe up or down in StandBy to change the face, sideways for your planet and widgets. Long-press to leave.", fontSize = 12.sp, color = Muted)
            HorizontalDivider()
            Text("Night mode", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(StandBySettings.NIGHT_MODES, s.nightMode) { s = s.copy(nightMode = it) }
            Text("Auto turns everything deep red and dims the screen when the room is dark, using the light sensor.", fontSize = 12.sp, color = Muted)
            Text("Planet page", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(StandBySettings.SCENES.map { SCENE_LABELS.getValue(it) }, SCENE_LABELS.getValue(s.scene)) { label ->
                s = s.copy(scene = SCENE_LABELS.entries.first { it.value == label }.key)
            }
            Text("World clock", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StandBySettings.CITIES.forEach { zone ->
                    val on = zone in s.zones
                    FilterChip(selected = on, shape = RoundedCornerShape(30.dp), label = { Text(ClockMath.cityName(zone), fontSize = 12.sp) }, onClick = {
                        s = s.copy(zones = if (on) (s.zones - zone).ifEmpty { s.zones } else (s.zones + zone).takeLast(4))
                    })
                }
            }
            Text("Up to four cities.", fontSize = 12.sp, color = Muted)
            HorizontalDivider()
            Text("AMOLED care", style = MaterialTheme.typography.titleSmall)
            StandByToggle("Show seconds", s.showSeconds) { s = s.copy(showSeconds = it) }
            StandByToggle("Shift pixels every minute", s.burnInShift) { s = s.copy(burnInShift = it) }
            Text("Dim when untouched", fontSize = 12.sp, color = Muted)
            ChoiceRow(StandBySettings.DIM_CHOICES.map { DIM_LABELS.getValue(it) }, DIM_LABELS.getValue(s.dimAfterMinutes)) { label ->
                s = s.copy(dimAfterMinutes = DIM_LABELS.entries.first { it.value == label }.key)
            }
            Text("True black everywhere, at most one redraw a second, a few pixels of drift each minute. Planets render at 30 fps and 720 px; nothing runs off the charger.", fontSize = 12.sp, color = Muted)
            HorizontalDivider()
            Text("Start on the charger", style = MaterialTheme.typography.titleSmall)
            Text("In Screen saver, choose DepthWall StandBy and set When to start to While charging. Turn the phone on its side on the charger.", fontSize = 12.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                        .onFailure { notice = "Screen saver settings are unavailable on this ROM." }
                }, modifier = Modifier.testTag("standby_dream_settings")) { Text("Screen saver", fontSize = 12.sp) }
                if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(context, StandByTileService::class.java), "StandBy",
                        Icon.createWithResource(context, R.drawable.ic_standby), context.mainExecutor) { result ->
                        notice = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "StandBy tile is in Quick Settings." else null
                    }
                }) { Text("Add tile", fontSize = 12.sp) }
            }
            notice?.let { Text(it, fontSize = 12.sp) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val chosen = s
            vm.run("StandBy saved") { vm.repo.settings.saveStandBy(chosen) }
            context.startActivity(Intent(context, StandByActivity::class.java))
            onClose()
        }, modifier = Modifier.testTag("standby_try")) { Text("Save & try") }
    }, dismissButton = {
        Row {
            TextButton(onClick = { val chosen = s; vm.run("StandBy saved") { vm.repo.settings.saveStandBy(chosen) }; onClose() }) { Text("Save") }
            TextButton(onClick = onClose) { Text("Cancel") }
        }
    })
}

@Composable private fun StandByToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 13.sp); Switch(value, onChange) }
}

/** The current time, refreshed each second while the preview is on screen. */
@Composable private fun rememberTicking(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000 - System.currentTimeMillis() % 1000); now = ZonedDateTime.now() } }
    return now
}

/** Draws a clock face exactly as StandBy does, on true black. */
@Composable private fun ClockPreview(face: ClockFace, time: ZonedDateTime, style: ClockStyle, modifier: Modifier) {
    Canvas(modifier.background(Color.Black)) {
        drawIntoCanvas { canvas ->
            face.draw(canvas.nativeCanvas, android.graphics.RectF(size.width * .04f, size.height * .06f, size.width * .96f, size.height * .94f),
                time, style.copy(animate = false))
        }
    }
}
