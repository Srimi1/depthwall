package dev.srimi.depthwall

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.PackManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt in with -Pandroid.testInstrumentationRunnerArguments.ports=true after installing the inspected packs. */
@RunWith(AndroidJUnit4::class)
class OriginalPortsIntegrationTest {
    @Test fun inspectedOriginalsExposeAll18PublicScenes():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ports")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=PackManager(context)
        val packs=manager.installed()
        assertEquals(6,packs.size)
        assertEquals(18,packs.sumOf { it.services.size })
        for(pack in packs) {
            val expected=manager.verifiedRelease(pack.definition.packageName)!!
            assertEquals(expected.getString("version"),pack.version)
            assertEquals(expected.getJSONArray("scenes").length(),pack.services.size)
            assertEquals(listOf(expected.getString("certificate")),pack.signatures)
            for(service in pack.services) {
                assertTrue(service.serviceInfo.exported)
                assertNotNull(context.packageManager.resolveService(manager.preview(service).apply {
                    action="android.service.wallpaper.WallpaperService"
                    component=service.component
                },0))
                // These exact modern packs expose locations as services, with no public settings activities.
                assertNull(manager.configuration(service))
            }
            val app=context.packageManager.getApplicationInfo(pack.definition.packageName,0)
            val inspection=manager.inspect(Uri.fromFile(File(app.sourceDir)))
            assertNull(inspection.problem)
            assertEquals(expected.getString("sha256"),inspection.sha256)
            assertEquals(pack.services.size,inspection.services.size)
            inspection.file.delete()
        }
    }
}
