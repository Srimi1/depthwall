-keepattributes Signature,*Annotation*
-dontwarn com.android.apksig.**
# apksig parses ASN.1 by reflecting over annotated fields.
-keep class com.android.apksig.** { *; }
# Hidden WallpaperService.Engine callback (AOD). Nothing in the app calls it, so R8 would remove it.
-keepclassmembers class dev.srimi.depthwall.wallpaper.DepthWallpaperService$DepthEngine {
    public void onAmbientModeChanged(boolean, long);
}
