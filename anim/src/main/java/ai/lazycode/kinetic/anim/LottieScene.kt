package ai.lazycode.kinetic.anim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import ai.lazycode.kinetic.engine.Scene
import ai.lazycode.kinetic.engine.SceneState
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable

/**
 * Plays a bundled Lottie animation (from `assets/`) as a full-bleed wallpaper
 * [Scene] — vector motion graphics on the wallpaper surface. Loops continuously,
 * advanced by the host's delta time. Zero-alloc per frame after load.
 */
class LottieScene(
    context: Context,
    assetName: String,
    private val bgColor: Int = 0xFF05070D.toInt(),
    private val speed: Float = 1f,
) : Scene {
    private val drawable = LottieDrawable()
    private val bg = Paint()
    private var progress = 0f
    private var durationS = 4f
    private var ready = false

    init {
        val comp = LottieCompositionFactory.fromAssetSync(context, assetName).value
        if (comp != null) {
            drawable.setComposition(comp)
            durationS = (comp.duration / 1000f).coerceAtLeast(0.5f)
            ready = true
        }
    }

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        bg.color = bgColor
        canvas.drawRect(0f, 0f, w, h, bg)
        if (!ready) return
        progress += (s.dtS / durationS) * speed
        progress -= progress.toInt().toFloat() // loop in [0,1)
        drawable.progress = progress
        drawable.setBounds(0, 0, w.toInt(), h.toInt())
        drawable.draw(canvas)
    }
}
