package dev.srimi.depthwall.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Orange=Color(0xFFFF870A)
val Ink=Color(0xFF000000)
val Surface=Color(0xFF131313)
val Muted=Color(0xFF9D9B97)
val Ivory=Color(0xFFF4EEE5)
@Composable fun DepthTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Orange,onPrimary=Ink,primaryContainer=Color(0xFF3C240F),onPrimaryContainer=Ivory,secondary=Orange,onSecondary=Ink,secondaryContainer=Color(0xFF35210E),onSecondaryContainer=Orange,tertiary=Ivory,background=Ink,onBackground=Ivory,surface=Surface,onSurface=Ivory,surfaceVariant=Color(0xFF242321),onSurfaceVariant=Muted,outline=Color(0xFF393734)),
        typography=Typography(
            headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=36.sp,lineHeight=39.sp,letterSpacing=(-1).sp),
            headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=28.sp,letterSpacing=(-.7).sp),
            titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=21.sp),
            bodyLarge=TextStyle(fontSize=16.sp,lineHeight=23.sp),
            bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp)
        ),content={ CompositionLocalProvider(LocalContentColor provides Ivory,content=content) })
}
