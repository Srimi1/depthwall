package dev.srimi.depthwall.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.wallpaper.SceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun WallpaperPreview(item:WallpaperItem,vm:MainViewModel,onClose:()->Unit,onLive:(SceneConfig)->Unit) {
    val context=LocalContext.current
    var config by remember(item.id) { vm.draft(item) }
    var live by rememberSaveable(item.id) { mutableStateOf(item.kind!="STATIC") }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showSurface by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf("Home") }
    var mask by remember { mutableStateOf(false) }
    var credits by remember { mutableStateOf(false) }
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var local by remember(item.id) { mutableStateOf(item) }
    var favourite by remember(item.id) { mutableStateOf(item.favorite) }
    val importForeground=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.run("Foreground imported") { val fg=vm.repo.importImage(it,true);config=config.copy(foreground=fg.localPath,scene="depth");live=true } }
    }
    LaunchedEffect(local.id,local.localPath) {
        if(local.isLocal && local.kind=="STATIC") {
            runCatching { withContext(Dispatchers.IO) { vm.repo.decode(local.localPath.ifBlank { local.asset }) } }.onSuccess { bitmap=it }.onFailure { vm.message(it.message.orEmpty()) }
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler(onBack=onClose)
        Box(Modifier.fillMaxSize().background(Ink)) {
            if(live && local.isLocal) ScenePreview(config,showSurface,Modifier.fillMaxSize(),vm::message)
            else bitmap?.let { b -> CropPreview(b,config,Modifier.fillMaxSize()) }
                ?: coil.compose.AsyncImage(local.imageModel,local.title,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(.8f),Color.Transparent))))
            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onClose,modifier=Modifier.testTag("close_preview")){Icon(Icons.Outlined.ArrowBack,"Close preview")}
                Column(Modifier.weight(1f).padding(horizontal=8.dp)) { Text(local.title,style=MaterialTheme.typography.titleLarge);Text(if(item.kind=="NATIVE_SCENE")"DEPTHWALL ORIGINAL" else if(live)"LAYERED WALLPAPER" else "WALLPAPER",fontSize=9.sp,letterSpacing=1.4.sp,color=Orange) }
                IconButton(onClick={favourite=!favourite;vm.favorite(local)}) { Icon(if(favourite)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Toggle favourite",tint=if(favourite)Orange else Ivory) }
                IconButton(onClick={credits=true}) { Icon(Icons.Outlined.Info,"Image credits") }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.95f)))).navigationBarsPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(live && item.kind=="NATIVE_SCENE")TextButton(onClick={showSurface=!showSurface}) { Icon(Icons.Outlined.PlayArrow,null);Text(if(showSurface)"Show orbit" else "Preview unlock") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={showControls=!showControls}) { Icon(if(showControls)Icons.Outlined.ExpandMore else Icons.Outlined.Tune,null);Text(if(showControls)"Hide controls" else "Customise") }
                }
                if(showControls)Column(Modifier.fillMaxWidth().heightIn(max=310.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF0121212)).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(!local.isLocal) {
                        Text("Download to crop, edit or apply offline.",color=Muted,fontSize=12.sp)
                        if(local.bytes>0)Text("Download size: %.1f MB".format(local.bytes/1048576.0),color=Muted,fontSize=11.sp)
                        Button(onClick={vm.run("Saved for offline use"){local=vm.repo.download(local);config=vm.repo.config(local)}}){Text("Download image")}
                    }
                    if(item.kind!="NATIVE_SCENE") {
                        ChoiceRow(listOf("Still","Depth + clock"),if(live)"Depth + clock" else "Still") { live=it=="Depth + clock" }
                        LabelSlider("Zoom",config.zoom,1f..3f) { config=config.copy(zoom=it) }
                        LabelSlider("Horizontal position",config.offsetX,0f..1f) { config=config.copy(offsetX=it) }
                        LabelSlider("Vertical position",config.offsetY,0f..1f) { config=config.copy(offsetY=it) }
                    }
                    if(live) {
                        Text("Clock style",fontSize=12.sp,color=Muted)
                        ChoiceRow(listOf("Off","Digital","Minimal","Analogue"),config.clock) { config=config.copy(clock=it) }
                        if(config.clock!="Off") {
                            LabelSlider("Clock position",config.clockY,.18f.. .72f) { config=config.copy(clockY=it) }
                            Text("This clock is drawn in the wallpaper. The crDroid clock is separate.",color=Muted,fontSize=11.sp)
                        }
                        LabelSlider("Parallax",config.parallax,0f..1f) { config=config.copy(parallax=it) }
                        if(item.kind!="NATIVE_SCENE" && local.isLocal) {
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick={importForeground.launch(arrayOf("image/png","image/webp"))}){Text("Import layer",fontSize=12.sp)}
                                OutlinedButton(onClick={mask=true}){Text("Edit mask",fontSize=12.sp)}
                            }
                            if(config.foreground.isNotEmpty())TextButton(onClick={vm.run("Layered wallpaper saved to Library") { vm.repo.saveDepth(local,config.foreground,"${local.title} · depth") }}){Text("Save depth wallpaper")}
                        }
                    } else {
                        Text("Apply to",color=Muted,fontSize=12.sp)
                        ChoiceRow(listOf("Home","Lock","Both"),target){target=it}
                    }
                }
                Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedIconButton(onClick={vm.run("Saved to Pictures/DepthWall") { vm.repo.export(local) }},modifier=Modifier.size(52.dp)) { Icon(Icons.Outlined.Download,"Export image") }
                    Button(onClick={
                        if(live) {
                            if(local.isLocal)onLive(config) else vm.message("Download this image before applying a live wallpaper.")
                        } else {
                            val metrics=context.resources.displayMetrics
                            vm.run("Wallpaper applied") { vm.repo.applyStatic(local,when(target){"Lock"->WallpaperManager.FLAG_LOCK;"Both"->3;else->1},config,metrics.widthPixels,metrics.heightPixels) }
                        }
                    },modifier=Modifier.weight(1f).height(52.dp).testTag("apply_wallpaper")) { Icon(Icons.Outlined.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(live)"Preview & apply" else "Set wallpaper") }
                }
            }
        }
    }
    if(mask)MaskEditor(local,vm,onDismiss={mask=false},onSaved={path->config=config.copy(foreground=path,scene="depth");mask=false;live=true})
    if(credits)AlertDialog(onDismissRequest={credits=false},title={Text("About this wallpaper")},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text(local.title);Text(local.author);Text(local.license,color=Muted);Text(if(local.bytes>0)"Downloaded: %.1f MB".format(local.bytes/1048576.0) else "Bundled original or remote image",fontSize=12.sp);Text(local.sourceUrl,fontSize=11.sp,color=Muted)}},confirmButton={TextButton(onClick={credits=false}){Text("Done")}},dismissButton={if(local.sourceUrl.startsWith("https://"))TextButton(onClick={runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(local.sourceUrl))) }}){Text("View source ↗")}})
}

@Composable fun LabelSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,fontSize=12.sp,color=Muted,modifier=Modifier.width(118.dp));Slider(value,onChange,valueRange=range,modifier=Modifier.weight(1f)) }
}
@Composable private fun CropPreview(bitmap:Bitmap,config:SceneConfig,modifier:Modifier) {
    val image=remember(bitmap){bitmap.asImageBitmap()}
    Canvas(modifier) {
        val crop=CropMath.calculate(bitmap.width,bitmap.height,size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1),config.zoom,config.offsetX,config.offsetY)
        drawImage(image,srcOffset=IntOffset(crop.left,crop.top),srcSize=IntSize(crop.width,crop.height),dstSize=IntSize(size.width.toInt(),size.height.toInt()))
    }
}
@Composable fun ScenePreview(config:SceneConfig,surface:Boolean,modifier:Modifier,onError:(String)->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val view=remember { SceneView(context) }
    DisposableEffect(owner,view) {
        val observer=LifecycleEventObserver { _,event -> when(event){Lifecycle.Event.ON_RESUME->view.setActive(true);Lifecycle.Event.ON_PAUSE->view.setActive(false);else->Unit} }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);view.setActive(false) }
    }
    AndroidView(factory={view},modifier=modifier,update={it.config=config;it.showSurface=surface;it.onError=onError})
}
