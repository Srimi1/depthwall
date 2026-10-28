package dev.srimi.depthwall.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Existing accent call sites follow the selected palette, including previews and dialogs. */
val Orange: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val Ink=Color(0xFF000000)
val Surface=Color(0xFF131313)
val Muted=Color(0xFF9D9B97)
val Ivory=Color(0xFFF4EEE5)
@Composable fun DepthTheme(theme:String="Papaya",content:@Composable ()->Unit) {
    val accent = when(theme) {
        "Space blue" -> Color(0xFF8FCBFF)
        "AMOLED monochrome" -> Color(0xFFE3E3E3)
        else -> Color(0xFFFF870A)
    }
    val accentSurface = when(theme) {
        "Space blue" -> Color(0xFF142C40)
        "AMOLED monochrome" -> Color(0xFF292929)
        else -> Color(0xFF3C240F)
    }
    MaterialTheme(colorScheme=darkColorScheme(primary=accent,onPrimary=Ink,primaryContainer=accentSurface,onPrimaryContainer=Ivory,secondary=accent,onSecondary=Ink,secondaryContainer=accentSurface,onSecondaryContainer=accent,tertiary=Ivory,background=Ink,onBackground=Ivory,surface=Surface,onSurface=Ivory,surfaceVariant=Color(0xFF242321),onSurfaceVariant=Muted,outline=Color(0xFF393734)),
        typography=Typography(
            headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=36.sp,lineHeight=39.sp,letterSpacing=(-1).sp),
            headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=28.sp,letterSpacing=(-.7).sp),
            titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=21.sp),
            bodyLarge=TextStyle(fontSize=16.sp,lineHeight=23.sp),
            bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp)
        ),content={ CompositionLocalProvider(LocalContentColor provides Ivory,content=content) })
}
