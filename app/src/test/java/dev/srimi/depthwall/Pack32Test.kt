package dev.srimi.depthwall

import dev.srimi.depthwall.data.Pack32
import org.junit.Assert.*
import org.junit.Test

class Pack32Test {
    private val apk=listOf(
        "AndroidManifest.xml","classes.dex","classes2.dex","resources.arsc","res/drawable/icon.png","assets/bin/Data/data.unity3d",
        "lib/arm64-v8a/libunity.so","lib/arm64-v8a/libmain.so","lib/armeabi-v7a/libunity.so","lib/armeabi-v7a/libmain.so",
        "lib/x86/libunity.so","lib/x86_64/libunity.so",
        "META-INF/MANIFEST.MF","META-INF/CERT.SF","META-INF/CERT.RSA","META-INF/OTHER.DSA","META-INF/KEY.EC","META-INF/services/kotlin.Plugin","META-INF/androidx.core.version"
    )
    @Test fun keepsThe32BitEngineAndDropsOthers() {
        val kept=Pack32.keep(apk)
        assertEquals(listOf(
            "AndroidManifest.xml","classes.dex","classes2.dex","resources.arsc","res/drawable/icon.png","assets/bin/Data/data.unity3d",
            "lib/armeabi-v7a/libunity.so","lib/armeabi-v7a/libmain.so","META-INF/services/kotlin.Plugin","META-INF/androidx.core.version"
        ),kept)
        assertTrue(kept.none { it.startsWith("lib/arm64-v8a/") || it.startsWith("lib/x86") })
    }
    @Test fun dropsOnlyTopLevelSignatureFiles() {
        assertTrue(Pack32.isSignatureFile("META-INF/MANIFEST.MF"))
        assertTrue(Pack32.isSignatureFile("META-INF/cert.rsa"))
        assertFalse(Pack32.isSignatureFile("META-INF/nested/CERT.SF"))
        assertFalse(Pack32.isSignatureFile("assets/CERT.RSA"))
    }
    @Test fun refusesPacksWithout32BitLibraries() {
        val error=runCatching { Pack32.keep(apk.filterNot { it.startsWith("lib/armeabi-v7a/") }) }.exceptionOrNull()
        assertEquals("This pack has no 32-bit engine",error?.message)
        assertTrue(runCatching { Pack32.keep(listOf("classes.dex","lib/armeabi-v7a/README.txt")) }.isFailure)
    }
}
