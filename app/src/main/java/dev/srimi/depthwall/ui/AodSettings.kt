package dev.srimi.depthwall.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.srimi.depthwall.data.AodStatus
import dev.srimi.depthwall.data.AodSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun AodSettingsSection() {
    var open by remember { mutableStateOf(false) }
    Text("Always-on display", style = MaterialTheme.typography.titleSmall)
    Text("OnePlus 7T Pro McLaren 4G · crDroid Android 17", fontSize = 12.sp, color = Muted)
    Text("Use crDroid’s AOD for its clock and notifications. DepthWall pauses its motion and sensors while the display sleeps.", fontSize = 12.sp, color = Muted)
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("aod_setup")) { Text("Set up & check AOD") }
    if (open) AodSetupDialog { open = false }
}

@Composable private fun AodSetupDialog(onClose: () -> Unit) {
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
        try { status = withContext(Dispatchers.IO) { AodSupport.read(context) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failure = "Could not read the device’s AOD preferences. You can still open system settings." }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("AOD on your phone") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Target: OnePlus 7T Pro McLaren 4G (hotdog)", style = MaterialTheme.typography.titleSmall)
            Text("Enable Always-On Display in crDroid’s settings, then lock the phone to check its clock and notifications.", fontSize = 13.sp)
            val current = status
            if (current != null) {
                Text("Reported: ${current.model} · Android ${current.androidVersion}", color = Muted, fontSize = 12.sp)
                if (!current.isHotdog) Text("This device reports “${current.device}”. The phone profile targets the 4G hotdog model; its hardware has not been verified here.", color = Muted, fontSize = 12.sp)
                Text("System AOD preference: ${AodStatus.label(current.alwaysOn)}", fontSize = 13.sp)
                Text("A saved preference does not confirm that AOD is working. Schedules and your ROM’s device configuration also apply.", color = Muted, fontSize = 12.sp)
                if (current.batterySaver) Text("Battery Saver is on. Your ROM may suppress AOD while it is active.", fontSize = 12.sp)
                if (current.colorInversion == true) Text("Colour inversion is enabled and can disable crDroid’s AOD.", fontSize = 12.sp)
                if (current.suppressed == true) Text("The system currently reports that ambient display is suppressed.", fontSize = 12.sp)
            } else if (failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            HorizontalDivider()
            Text("Wallpaper during AOD", style = MaterialTheme.typography.titleSmall)
            Text("Wallpaper imagery during AOD requires a supported, privileged ROM integration. This ordinary APK cannot enable it. DepthWall’s wallpaper clock is separate from crDroid’s AOD clock.", color = Muted, fontSize = 12.sp)
            Text("Physical-phone verification is still needed: check sleep → AOD → wake/unlock, charging and Battery Saver. Copy the report if you need help with your exact build.", color = Muted, fontSize = 12.sp)
            notice?.let { Text(it, fontSize = 12.sp) }
            TextButton(onClick = { refresh++; notice = null }) { Text("Refresh observations") }
            TextButton(enabled = status != null, onClick = {
                status?.let { snapshot ->
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DepthWall AOD report", snapshot.report()))
                    notice = "Compatibility report copied."
                }
            }) { Text("Copy compatibility report") }
        }
    }, confirmButton = {
        TextButton(onClick = {
            notice = when (AodSupport.openSettings(context)) {
                AodSupport.Destination.AOD -> null
                AodSupport.Destination.DISPLAY, AodSupport.Destination.SETTINGS -> "Look for Always-On Display or Lock screen in system settings."
                AodSupport.Destination.UNAVAILABLE -> "System settings could not be opened on this ROM."
            }
        }) { Text("Open system AOD settings") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}
