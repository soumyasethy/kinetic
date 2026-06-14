package ai.lazycode.kinetic.wallpaper

import android.graphics.Canvas
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import ai.lazycode.kinetic.engine.SceneState
import ai.lazycode.kinetic.engine.Scene

/**
 * Base live-wallpaper service for the app-factory: renders any [Scene] onto
 * the wallpaper surface with a Choreographer loop. Battery discipline is built in
 * — rendering stops the moment the wallpaper isn't visible (home hidden, screen
 * off), per the platform requirement that wallpapers only use CPU while visible.
 *
 * Subclass and provide a scene:
 * ```
 * class MyWallpaper : SceneWallpaperService() {
 *     override fun createScene() = MyScene()
 * }
 * ```
 */
abstract class SceneWallpaperService : WallpaperService() {

    /** A fresh scene per engine instance (preview + real wallpaper get their own). */
    abstract fun createScene(): Scene

    /** Ambient "energy" 0..1 fed to the scene when there's no live driver. */
    protected open fun ambientSc(timeS: Float): Float = 0.4f

    // Bumped to hot-swap the live scene without re-applying the wallpaper. Call
    // [reloadScene] (e.g. from a SharedPreferences listener) when the user picks
    // a different scene; every running engine rebuilds via [createScene] next frame.
    @Volatile
    private var sceneVersion = 0

    protected fun reloadScene() {
        sceneVersion++
    }

    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine() {
        private var scene = createScene()
        private var localVersion = sceneVersion
        private val state = SceneState()
        private val choreographer = Choreographer.getInstance()
        private var visible = false
        private var lastNanos = 0L

        private val frame = object : Choreographer.FrameCallback {
            override fun doFrame(now: Long) {
                if (!visible) return
                drawFrame(now)
                choreographer.postFrameCallback(this)
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                lastNanos = 0L
                choreographer.postFrameCallback(frame)
            } else {
                choreographer.removeFrameCallback(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            choreographer.removeFrameCallback(frame)
        }

        private fun drawFrame(now: Long) {
            val holder = surfaceHolder
            val canvas: Canvas =
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }) ?: return
            try {
                if (localVersion != sceneVersion) {
                    scene = createScene()
                    localVersion = sceneVersion
                }
                val dt = if (lastNanos == 0L) {
                    0f
                } else {
                    ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                lastNanos = now
                state.timeS += dt
                state.dtS = dt
                state.sc = ambientSc(state.timeS)
                scene.render(canvas, canvas.width.toFloat(), canvas.height.toFloat(), state)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
