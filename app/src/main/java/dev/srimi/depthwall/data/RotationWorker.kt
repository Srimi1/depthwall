package dev.srimi.depthwall.data

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.work.*
import dev.srimi.depthwall.DepthWallApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RotationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val repo=(applicationContext as DepthWallApp).repository
        val prefs=repo.settings.flow.first()
        if(prefs.rotationHours==0) return Result.success()
        val manager=WallpaperManager.getInstance(applicationContext)
        // Choosing a live wallpaper suspends static rotation; never replace it silently.
        val live=if(Build.VERSION.SDK_INT>=34) {
            listOf(1,2).filter { prefs.rotationTarget and it != 0 }.any { manager.getWallpaperInfo(it)!=null }
        } else manager.wallpaperInfo!=null
        if(live) return Result.success()
        val candidates=repo.dao.rotationCandidates().filter { !prefs.favoritesOnly || it.favorite }
        if(candidates.isEmpty()) return Result.success()
        val state=applicationContext.getSharedPreferences("rotation",Context.MODE_PRIVATE)
        val previous=state.getString("last",null)
        val next=candidates.filter { it.id!=previous }.ifEmpty { candidates }.random()
        return try {
            val metrics=applicationContext.resources.displayMetrics
            repo.applyStatic(next,prefs.rotationTarget,SceneConfig(),metrics.widthPixels,metrics.heightPixels)
            state.edit().putString("last",next.id).putLong("time",System.currentTimeMillis()).apply()
            Result.success()
        } catch(e:Exception) { if(runAttemptCount<2) Result.retry() else Result.failure() }
    }
    companion object {
        fun schedule(context:Context,s:AppSettings) {
            val work=WorkManager.getInstance(context)
            if(s.rotationHours==0) work.cancelUniqueWork("wallpaper_rotation")
            else work.enqueueUniquePeriodicWork("wallpaper_rotation",ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<RotationWorker>(s.rotationHours.toLong(),TimeUnit.HOURS)
                    .setInitialDelay(s.rotationHours.toLong(),TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
        }
    }
}
