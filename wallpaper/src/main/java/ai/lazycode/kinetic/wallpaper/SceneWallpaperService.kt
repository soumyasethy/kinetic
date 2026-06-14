package ai.lazycode.kinetic.wallpaper

import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import ai.lazycode.kinetic.engine.InfoOverlay
import ai.lazycode.kinetic.engine.Scene
import ai.lazycode.kinetic.engine.SceneState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Base live-wallpaper service for the app-factory: renders any [Scene] onto the
 * wallpaper surface with a Choreographer loop. Battery discipline is built in —
 * rendering stops the moment the wallpaper isn't visible. Optionally overlays a
 * clock + date + battery readout ([showInfo]) so a decorative wallpaper can also
 * be informative.
 *
 * ```
 * class MyWallpaper : SceneWallpaperService() {
 *     override fun createScene() = MyScene()
 *     override fun showInfo() = true
 * }
 * ```
 */
abstract class SceneWallpaperService : WallpaperService() {

    /** A fresh scene per engine instance (preview + real wallpaper get their own). */
    abstract fun createScene(): Scene

    /** Ambient "energy" 0..1 fed to the scene when there's no live driver. */
    protected open fun ambientSc(timeS: Float): Float = 0.4f

    /** Draw the clock + date + battery overlay over the scene. */
    protected open fun showInfo(): Boolean = false

    // Bumped to hot-swap the live scene without re-applying the wallpaper.
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

        // Cached time/date/battery — refreshed cheaply, not rebuilt per frame.
        private val cal = Calendar.getInstance()
        private val dateFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        private var lastDay = -1
        private var dateLabel = ""
        private var battFrame = 0

        // Tilt (gravity sensor). Raw values set on the sensor thread; low-pass
        // smoothed into SceneState on the render thread.
        private val sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        private val gravity = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        @Volatile private var rawTiltX = 0f
        @Volatile private var rawTiltY = 0f
        private var smoothTiltX = 0f
        private var smoothTiltY = 0f
        private val tiltListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Gravity ~9.8 m/s²; map to -1..1. +x tilt-right, +y tilt-up.
                rawTiltX = (-e.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                rawTiltY = (e.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

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
                battFrame = 0
                if (gravity != null) {
                    sensorManager?.registerListener(tiltListener, gravity, SensorManager.SENSOR_DELAY_GAME)
                }
                choreographer.postFrameCallback(frame)
            } else {
                sensorManager?.unregisterListener(tiltListener)
                choreographer.removeFrameCallback(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            sensorManager?.unregisterListener(tiltListener)
            choreographer.removeFrameCallback(frame)
        }

        private fun refreshInfo() {
            val ms = System.currentTimeMillis()
            cal.timeInMillis = ms
            state.hour = cal.get(Calendar.HOUR_OF_DAY)
            state.minute = cal.get(Calendar.MINUTE)
            val day = cal.get(Calendar.DAY_OF_YEAR)
            if (day != lastDay) {
                dateLabel = dateFmt.format(cal.time)
                lastDay = day
            }
            state.dateLabel = dateLabel
            // Battery is slow-changing — poll ~every 2s, not every frame.
            if (battFrame == 0) {
                val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
                if (bm != null) {
                    state.batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    state.charging = bm.isCharging
                }
            }
            battFrame = (battFrame + 1) % 120
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
                val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                state.timeS += dt
                state.dtS = dt
                state.sc = ambientSc(state.timeS)
                // Low-pass smooth the tilt so parallax glides instead of jitters.
                smoothTiltX += (rawTiltX - smoothTiltX) * 0.08f
                smoothTiltY += (rawTiltY - smoothTiltY) * 0.08f
                state.tiltX = smoothTiltX
                state.tiltY = smoothTiltY
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                scene.render(canvas, w, h, state)
                if (showInfo()) {
                    refreshInfo()
                    InfoOverlay.draw(canvas, w, h, state)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
