package dev.srimi.depthwall.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.srimi.depthwall.data.AmbientWallpaper
import dev.srimi.depthwall.data.AodStatus
import dev.srimi.depthwall.data.AodSupport
import dev.srimi.depthwall.data.WallpaperApplyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun AodSettingsSection(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Text("Always-on display", style = MaterialTheme.typography.titleSmall)
    Text("OnePlus 7T Pro McLaren 4G · crDroid Android 17 · rooted", fontSize = 12.sp, color = Muted)
    Text("Super wallpapers stay on AOD as a dim planet that zooms back in when you wake the phone. This needs the DepthWall AOD module (Magisk or KernelSU).", fontSize = 12.sp, color = Muted)
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("aod_setup")) { Text("Set up & check AOD") }
    if (open) AodSetupDialog(vm) { open = false }
}

@Composable private fun AodSetupDialog(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<AodStatus?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        failure = null
        status = null
        try { status = withContext(Dispatchers.IO) { AmbientWallpaper.syncComponent(context); AodSupport.read(context) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failure = "Could not read the device’s AOD state. You can still open system settings." }
    }
    // Same staging and confirmation as a normal live apply, with the AOD-capable component.
    val applyAod = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accepted = result.resultCode == Activity.RESULT_OK
        val flags = vm.repo.scenes.finishPreview(accepted, if (accepted) WallpaperApplyState.capture(context) else null,
            AmbientWallpaper.ambientComponent(context).flattenToString())
        notice = when {
            flags and 2 != 0 -> "AOD wallpaper is on the lock screen. Lock the phone to check it."
            flags != 0 -> "Applied to Home only. AOD shows the lock screen, so apply to Lock or Both."
            accepted -> "Android did not confirm the destination. Try again and choose Lock or Both."
            else -> null
        }
        refresh++
    }
    fun applyAodWallpaper() {
        val before = WallpaperApplyState.capture(context)
        if (before == null) { notice = "Cannot read the current wallpaper. Try again."; return }
        // Keep the scene already on the lock screen, falling back to the home scene.
        val lockIsOwn = before.lockId > 0 && AmbientWallpaper.isOwn(context, before.lockComponent)
        val scene = vm.repo.scenes.active(if (lockIsOwn) 2 else 1)
        try {
            vm.repo.scenes.stage(scene, before)
            applyAod.launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, AmbientWallpaper.ambientComponent(context)))
        } catch (e: Exception) { vm.repo.scenes.clearPending(); notice = "Wallpaper picker unavailable: ${e.message}" }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Super wallpaper on AOD") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val current = status
            if (current != null) {
                Text("Reported: ${current.model} · Android ${current.androidVersion}", color = Muted, fontSize = 12.sp)
                if (!current.isHotdog) Text("This device reports “${current.device}”, not the 4G hotdog model this profile targets.", color = Muted, fontSize = 12.sp)
                Check("Installed as a system app", current.systemApp, "Flash the DepthWall AOD module and reboot.")
                Check("AOD wallpaper permission", current.ambientPermission, "Granted only to the system copy from the module.")
                Check("AOD wallpaper component", current.ambientComponent, "Turns on by itself once the permission is granted.")
                Check("ROM shows wallpapers on AOD", current.romAodWallpaper, "The module’s overlay sets crDroid’s config_dozeSupportsAodWallpaper.")
                Check("Always-on display", current.alwaysOn, "Turn on Always-on display in crDroid’s settings.")
                Check("Wallpaper shown on AOD", current.aodWallpaperSetting,
                    if (current.canWriteSecure) "crDroid’s doze_always_on_wallpaper_enabled is off. Turn it on below."
                    else "The module turns on crDroid’s doze_always_on_wallpaper_enabled after a reboot.")
                if (current.aodWallpaperSetting != true && current.canWriteSecure) OutlinedButton(onClick = {
                    notice = if (AmbientWallpaper.setAodWallpaper(context, true)) "Wallpaper on AOD turned on." else "crDroid refused the setting change."
                    refresh++
                }, modifier = Modifier.fillMaxWidth()) { Text("Show wallpaper on AOD") }
                Check("DepthWall AOD on the lock screen", current.lockUsesAmbient, "AOD shows the lock wallpaper. Apply below and choose Lock or Both.")
                if (current.batterySaver) Text("Battery Saver is on. crDroid may suppress AOD while it is active.", fontSize = 12.sp)
                if (current.colorInversion == true) Text("Colour inversion is on and can disable crDroid’s AOD.", fontSize = 12.sp)
                if (current.suppressed == true) Text("The system currently reports that ambient display is suppressed.", fontSize = 12.sp)
                Text(if (current.aodReady) "Everything DepthWall can check is ready. Lock the phone to see the AOD planet."
                    else "AOD frames stay off until every item above is ready. Nothing runs in the background meanwhile.",
                    color = if (current.aodReady) Orange else Muted, fontSize = 12.sp)
                Button(enabled = current.ambientComponent, onClick = ::applyAodWallpaper, modifier = Modifier.fillMaxWidth().testTag("aod_apply")) {
                    Text("Apply AOD wallpaper")
                }
            } else if (failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            HorizontalDivider()
            Text("On AOD the planet shrinks to a soft disc on true black below crDroid’s clock and shifts a few pixels each time against burn-in. Motion, sensors and DepthWall’s clock stay off.", color = Muted, fontSize = 12.sp)
            notice?.let { Text(it, fontSize = 12.sp) }
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DepthWall AOD module", AodSupport.MODULE_STEPS))
                notice = "Module steps copied."
            }) { Text("Copy module install steps") }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { refresh++; notice = null }) { Text("Refresh") }
                TextButton(enabled = status != null, onClick = {
                    status?.let { snapshot ->
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DepthWall AOD report", snapshot.report()))
                        notice = "Compatibility report copied."
                    }
                }) { Text("Copy report") }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            notice = when (AodSupport.openSettings(context)) {
                AodSupport.Destination.AOD -> null
                AodSupport.Destination.DISPLAY, AodSupport.Destination.SETTINGS -> "Look for Always-On Display or Lock screen in system settings."
                AodSupport.Destination.UNAVAILABLE -> "System settings could not be opened on this ROM."
            }
        }) { Text("crDroid AOD settings") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

/** One readiness line: a known yes/no, or unknown when the ROM does not report it. */
@Composable private fun Check(label: String, value: Boolean?, help: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(when (value) { true -> "✓"; false -> "✕"; null -> "?" }, Modifier.width(22.dp),
            color = when (value) { true -> Orange; false -> Color(0xFFE5484D); null -> Muted }, fontSize = 14.sp)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            if (value != true) Text(help, color = Muted, fontSize = 11.sp)
        }
    }
}
