package dev.srimi.depthwall.standby

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.ui.ChoiceRow
import dev.srimi.depthwall.ui.DepthTheme
import dev.srimi.depthwall.ui.Muted
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class StandbySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as DepthWallApp).repository
        setContent {
            val theme by repository.settings.flow.collectAsStateWithLifecycle(AppSettings())
            DepthTheme(theme.appTheme) { StandbySettingsScreen(repository, onClose = { finish() }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StandbySettingsScreen(repository: Repository, onClose: () -> Unit) {
    val settings by repository.standbySettings.flow.collectAsStateWithLifecycle<StandbySettings?>(null)
    val wallpapers by repository.dao.observe().collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    var pickPhoto by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    fun edit(block: suspend () -> Unit) {
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar("Could not save your preference. Please try again.") }
        }
    }
    LaunchedEffect(repository) { repository.seed() }
    val importPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val item = repository.importImage(uri)
                repository.standbySettings.setPhoto(item.id)
                repository.standbySettings.setDesign(StandbyDesign.PHOTO)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar("This photo could not be imported. Try a different image.") }
            finally { importing = false }
        }
    }
    val preview = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("StandBy") }, navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            })
        }) { padding ->
        val current = settings
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(240.dp), modifier = Modifier.fillMaxSize().padding(padding).testTag("standby_designs"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("A little space for your time.", style = MaterialTheme.typography.headlineMedium)
                        Text("Plug in, turn your phone sideways, and make it a clock for your desk or bedside.", color = Muted)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Enable StandBy", style = MaterialTheme.typography.titleMedium)
                                Text("Opens immediately in DepthWall while charging horizontally.", color = Muted, fontSize = 12.sp)
                            }
                            Switch(current.enabled, { edit { repository.standbySettings.setEnabled(it) } },
                                modifier = Modifier.testTag("standby_enabled"))
                        }
                        Button(onClick = { preview.launch(StandbyActivity.intent(context, preview = true)) },
                            modifier = Modifier.fillMaxWidth().testTag("standby_preview")) { Text("Preview full screen") }
                        Text("Swipe to change designs. Tap for Exit or use Back. Exiting pauses automatic entry until you unplug or turn the phone upright.", color = Muted, fontSize = 12.sp)
                        HorizontalDivider()
                        Text("Choose a design", style = MaterialTheme.typography.titleLarge)
                    }
                }
                items(StandbyDesign.entries, key = { it.name }) { design ->
                    Card(onClick = { edit { repository.standbySettings.setDesign(design) } },
                        modifier = Modifier.fillMaxWidth().testTag("standby_design_${design.name}").semantics { selected = current.design == design },
                        colors = CardDefaults.cardColors(containerColor = if (current.design == design)
                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp)) {
                        StandbyFace(design, ZonedDateTime.now().withHour(10).withMinute(9), DateFormat.is24HourFormat(context),
                            locale, night = false, modifier = Modifier.fillMaxWidth().aspectRatio(1.85f).padding(10.dp)
                                .clip(RoundedCornerShape(14.dp)))
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(design.label, style = MaterialTheme.typography.titleMedium)
                                Text(design.description, color = Muted, fontSize = 12.sp)
                            }
                            if (current.design == design) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        Text("Photo wallpaper", style = MaterialTheme.typography.titleLarge)
                        Text(wallpapers.firstOrNull { it.id == current.photoWallpaperId }?.title ?: "Blue Hour · bundled artwork",
                            color = Muted, modifier = Modifier.testTag("standby_photo_selection"))
                        OutlinedButton(onClick = { pickPhoto = true }, modifier = Modifier.fillMaxWidth().testTag("standby_choose_photo")) {
                            Text("Choose offline wallpaper")
                        }
                        OutlinedButton(enabled = !importing, onClick = { importPhoto.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (importing) "Importing…" else "Import a photo")
                        }
                        Text("Photos stay on your phone. Download online wallpapers in Browse first to use them here.", color = Muted, fontSize = 12.sp)
                        HorizontalDivider()
                        Text("Night mode", style = MaterialTheme.typography.titleLarge)
                        ChoiceRow(StandbyNightMode.entries.map { it.label }, current.nightMode.label) { label ->
                            edit { repository.standbySettings.setNightMode(StandbyNightMode.entries.first { it.label == label }) }
                        }
                        Text("Auto uses dim red from 10 pm to 7 am in your local time. Photos are hidden at night. Clocks shift slightly each minute to reduce static OLED exposure.", color = Muted, fontSize = 12.sp)
                        HorizontalDivider()
                        Text("Charging screensaver", style = MaterialTheme.typography.titleLarge)
                        Text("For StandBy outside the app: open Android’s screensaver settings, select DepthWall StandBy, and choose While charging. Leave the phone charging horizontally and let it reach its normal screen timeout.", color = Muted)
                        OutlinedButton(onClick = {
                            val destination = openScreensaverSettings(context)
                            if (destination != Settings.ACTION_DREAM_SETTINGS) scope.launch {
                                snackbar.showSnackbar(if (destination == null) "Screensaver settings are unavailable on this device. In-app StandBy still works."
                                    else "Look for Screen saver in Display settings.")
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("standby_system_setup")) { Text("Set up charging screensaver") }
                        Text("Android and your ROM control screensaver timing. Pressing the power button can skip it. This is separate from Always-On Display and does not replace the system lock screen.", color = Muted, fontSize = 12.sp)
                        Text("System previews also require charging and landscape. Use Preview full screen above to try any design without either.", color = Muted, fontSize = 12.sp)
                        Text("Phone behaviour still needs verification on your exact OnePlus / crDroid build.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            if (pickPhoto) PhotoPicker(wallpapers.filter { it.kind == WallpaperKind.STATIC.name && it.isLocal },
                onClose = { pickPhoto = false }, onChoose = { item ->
                    pickPhoto = false
                    edit { repository.standbySettings.setPhoto(item.id); repository.standbySettings.setDesign(StandbyDesign.PHOTO) }
                })
        }
    }
}

@Composable private fun PhotoPicker(items: List<WallpaperItem>, onClose: () -> Unit, onChoose: (WallpaperItem) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Offline wallpapers") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onChoose(item) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(item.thumbnailModel, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Text(item.title, Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

internal fun openScreensaverSettings(context: android.content.Context): String? {
    for (action in listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS)) {
        try { context.startActivity(Intent(action)); return action }
        catch (_: ActivityNotFoundException) { }
        catch (_: SecurityException) { }
    }
    return null
}
