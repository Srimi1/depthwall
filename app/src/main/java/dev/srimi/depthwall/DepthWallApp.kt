package dev.srimi.depthwall

import android.app.Application
import dev.srimi.depthwall.data.Repository

class DepthWallApp : Application() {
    val repository by lazy { Repository(this) }
}
