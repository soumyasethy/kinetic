package ai.lazycode.kinetic.anim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import ai.lazycode.kinetic.engine.Scene
import ai.lazycode.kinetic.engine.SceneState
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.RenderMode
import kotlin.math.max

/**
 * Plays a bundled Lottie animation (from `assets/`) as a full-bleed wallpaper
 * [Scene] — vector motion graphics on the wallpaper surface, **crisp at any
 * resolution**.
 *
 * Quality notes:
 *  - [RenderMode.HARDWARE] draws the vectors straight onto the (hardware) wallpaper
 *    canvas — no low-res offscreen bitmap, so no upscaling blur.
 *  - The drawable is sized to a COVER rectangle (≥ the surface) and centre-cropped,
 *    so the animation fills the screen at native resolution instead of being
 *    letterboxed/fit and softened.
 */
class LottieScene(
    context: Context,
    assetName: String,
    private val bgColor: Int = 0xFF05070D.toInt(),
    private val speed: Float = 1f,
) : Scene {
    private val drawable = LottieDrawable().apply { renderMode = RenderMode.HARDWARE }
    private val bg = Paint()
    private var progress = 0f
    private var durationS = 4f
    private var compW = 1f
    private var compH = 1f
    private var ready = false

    // Cover bounds, recomputed only when the surface size changes.
    private var boundsForW = -1f
    private var boundsForH = -1f
    private var offX = 0f
    private var offY = 0f

    init {
        val comp = LottieCompositionFactory.fromAssetSync(context, assetName).value
        if (comp != null) {
            drawable.setComposition(comp)
            durationS = (comp.duration / 1000f).coerceAtLeast(0.5f)
            compW = comp.bounds.width().toFloat().coerceAtLeast(1f)
            compH = comp.bounds.height().toFloat().coerceAtLeast(1f)
            ready = true
        }
    }

    private fun fitBounds(w: Float, h: Float) {
        val scale = max(w / compW, h / compH)          // COVER, not fit
        val dw = (compW * scale).toInt()
        val dh = (compH * scale).toInt()
        drawable.setBounds(0, 0, dw, dh)               // render at full cover resolution
        offX = (w - dw) / 2f                            // centre-crop
        offY = (h - dh) / 2f
        boundsForW = w; boundsForH = h
    }

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        bg.color = bgColor
        canvas.drawRect(0f, 0f, w, h, bg)
        if (!ready) return
        if (boundsForW != w || boundsForH != h) fitBounds(w, h)

        progress += (s.dtS / durationS) * speed
        progress -= progress.toInt().toFloat()         // loop in [0,1)
        drawable.progress = progress

        canvas.save()
        canvas.translate(offX, offY)
        drawable.draw(canvas)
        canvas.restore()
    }
}
