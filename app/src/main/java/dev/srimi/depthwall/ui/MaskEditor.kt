package dev.srimi.depthwall.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.srimi.depthwall.data.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable fun MaskEditor(item:WallpaperItem,vm:MainViewModel,onDismiss:()->Unit,onSaved:(String)->Unit) {
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf<Bitmap?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var erase by remember { mutableStateOf(true) }
    var brush by remember { mutableFloatStateOf(45f) }
    val history=remember { mutableStateListOf<Bitmap>() }
    var last by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(item.id) {
        runCatching { withContext(Dispatchers.IO) { vm.repo.decode(item.localPath.ifBlank { item.asset },1560) } }
            .onSuccess { original=it;working=it.copy(Bitmap.Config.ARGB_8888,true) }
            .onFailure { vm.message(it.message.orEmpty());onDismiss() }
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().padding(20.dp)) {
            Text("Shape the foreground",style=MaterialTheme.typography.headlineMedium)
            Text("Erase the sky and background. Keep the subject that should sit in front of your clock. Restore brings pixels back.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(vertical=12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                FilterChip(erase,{erase=true},label={Text("Erase")});FilterChip(!erase,{erase=false},label={Text("Restore")})
                TextButton(enabled=history.isNotEmpty(),onClick={working=history.removeAt(history.lastIndex);version++}){Text("Undo")}
            }
            LabelSlider("Brush size",brush,8f..150f){brush=it}
            val bitmap=working;val source=original
            if(bitmap!=null && source!=null) {
                Canvas(Modifier.weight(1f).fillMaxWidth().pointerInput(bitmap,erase,brush) {
                    val scale=minOf(size.width.toFloat()/bitmap.width,size.height.toFloat()/bitmap.height)
                    val origin=Offset((size.width-bitmap.width*scale)/2,(size.height-bitmap.height*scale)/2)
                    fun point(p:Offset)=(p-origin)/scale
                    fun stroke(a:Offset,b:Offset) {
                        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            strokeWidth=brush;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
                            if(erase)xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                            else shader=BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
                        }
                        android.graphics.Canvas(bitmap).drawLine(a.x,a.y,b.x,b.y,paint);version++
                    }
                    detectDragGestures(onDragStart={
                        if(history.size>=8)history.removeAt(0).recycle()
                        history.add(bitmap.copy(Bitmap.Config.ARGB_8888,true));last=point(it);stroke(last,last+Offset(.1f,.1f))
                    },onDrag={change,_->change.consume();val p=point(change.position);stroke(last,p);last=p})
                }) {
                    @Suppress("UNUSED_VARIABLE") val revision=version
                    val scale=minOf(size.width/bitmap.width,size.height/bitmap.height)
                    val left=(size.width-bitmap.width*scale)/2;val top=(size.height-bitmap.height*scale)/2
                    for(y in 0..(size.height/24).toInt())for(x in 0..(size.width/24).toInt())drawRect(if((x+y)%2==0)Color(0xFF222222)else Color(0xFF141414),Offset(x*24f,y*24f),androidx.compose.ui.geometry.Size(24f,24f))
                    drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap,null,android.graphics.RectF(left,top,left+bitmap.width*scale,top+bitmap.height*scale),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
                }
            } else Box(Modifier.weight(1f)) { CircularProgressIndicator() }
            Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("Cancel")}
                Button(enabled=working!=null,onClick={vm.run("Foreground mask ready") {
                    val snapshot=working!!.copy(Bitmap.Config.ARGB_8888,false)
                    val file=withContext(Dispatchers.IO) {
                        File(vm.repo.downloads,"foreground_${UUID.randomUUID()}.png").also { target -> target.outputStream().use { check(snapshot.compress(Bitmap.CompressFormat.PNG,100,it)) };snapshot.recycle() }
                    }
                    onSaved(file.path)
                }},modifier=Modifier.weight(1f)){Text("Use foreground")}
            }
        }
    }
}
