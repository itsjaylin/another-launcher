package com.anotherlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** An app being downloaded or updated, as reported by the system PackageInstaller. */
data class InstallProgress(
    val packageName: String,
    val label: String?,
    val icon: Bitmap?,
    val progress: Float,
    val newApp: Boolean
)

/**
 * Real-time download/update progress monitoring via LauncherApps and PackageInstaller callbacks.
 */
object Installs {
    private val sessions = MutableStateFlow<Map<Int, InstallProgress>>(emptyMap())
    val active: StateFlow<Map<Int, InstallProgress>> = sessions.asStateFlow()
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val launcherApps = app.getSystemService(LauncherApps::class.java) ?: return
        val installer = app.packageManager.packageInstaller

        fun installed(pkg: String) = runCatching { app.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        fun put(info: PackageInstaller.SessionInfo?) {
            val pkg = info?.appPackageName ?: return
            sessions.update {
                it + (info.sessionId to InstallProgress(pkg, info.appLabel?.toString(), info.appIcon, info.progress, !installed(pkg)))
            }
        }

        runCatching {
            launcherApps.allPackageInstallerSessions.filter { it.isActive }.forEach(::put)
        }
        runCatching {
            launcherApps.registerPackageInstallerSessionCallback(app.mainExecutor, object : PackageInstaller.SessionCallback() {
                override fun onCreated(sessionId: Int) = put(installer.getSessionInfo(sessionId))
                override fun onBadgingChanged(sessionId: Int) = put(installer.getSessionInfo(sessionId))
                override fun onActiveChanged(sessionId: Int, active: Boolean) {
                    if (active) put(installer.getSessionInfo(sessionId)) else sessions.update { it - sessionId }
                }
                override fun onProgressChanged(sessionId: Int, progress: Float) {
                    sessions.update { map ->
                        map[sessionId]?.let { map + (sessionId to it.copy(progress = progress)) } ?: map
                    }
                }
                override fun onFinished(sessionId: Int, success: Boolean) {
                    val finished = sessions.value[sessionId]
                    sessions.update { it - sessionId }
                    if (success && finished?.newApp == true) {
                        NewApps.mark(app, finished.packageName)
                    }
                }
            })
        }
    }
}

/** Install progress mapped by package name for icon overlays. */
val LocalInstallProgress = staticCompositionLocalOf { emptyMap<String, Float>() }

/** Newly installed packages that have not yet been opened. */
val LocalNewApps = staticCompositionLocalOf { emptySet<String>() }

/** Tracks newly installed apps and clears the indicator once opened. */
object NewApps {
    private const val PREFS = "anotherlauncher_new_apps"
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    val packages = MutableStateFlow<Set<String>>(emptySet())

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val fresh = prefs.all.mapNotNull { (pkg, at) ->
            pkg.takeIf { (at as? Long)?.let { now - it < KEEP_MS } == true }
        }.toSet()
        prefs.edit().apply {
            prefs.all.keys.filter { it !in fresh }.forEach(::remove)
        }.apply()
        packages.value = fresh
    }

    fun mark(context: Context, pkg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(pkg, System.currentTimeMillis()).apply()
        packages.update { it + pkg }
    }

    fun opened(context: Context, pkg: String) {
        if (pkg !in packages.value) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(pkg).apply()
        packages.update { it - pkg }
    }
}

/** Progress ring shown over an icon while downloading/installing. */
@Composable
fun InstallRing(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "install progress")
    Canvas(modifier.semantics { contentDescription = "Installing, ${(progress * 100).toInt()}%" }) {
        drawRect(Color.Black.copy(alpha = 0.45f))
        val r = size.minDimension * 0.22f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.White.copy(alpha = 0.9f), r, c, style = Stroke(r * 0.14f))
        drawArc(
            Color.White,
            -90f,
            360f * shown,
            true,
            Offset(c.x - r * 0.78f, c.y - r * 0.78f),
            Size(r * 1.56f, r * 1.56f)
        )
    }
}

/** Subtle indicator dot beside newly installed apps. */
@Composable
fun NewAppDot(packageName: String, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    if (packageName !in LocalNewApps.current) return
    Box(
        modifier
            .padding(end = 4.dp)
            .size(size)
            .background(Color(0xFF0A84FF), CircleShape)
            .semantics { contentDescription = "New app" }
    )
}
