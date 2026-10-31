-keepattributes Signature,*Annotation*
-dontwarn com.android.apksig.**
# apksig parses ASN.1 by reflecting over annotated fields.
-keep class com.android.apksig.** { *; }
