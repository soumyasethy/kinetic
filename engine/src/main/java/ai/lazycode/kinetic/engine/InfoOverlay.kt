package ai.lazycode.kinetic.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * A clean, legible clock + date + battery readout drawn over any [Scene] —
 * turns a decorative wallpaper into an informative one. Reads the wall-clock and
 * battery fields the host fills into [SceneState]. Zero-alloc per frame.
 */
object InfoOverlay {
    private val clock = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun draw(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val cx = w / 2f
        val top = h * 0.18f

        clock.textSize = h * 0.085f
        clock.setShadowLayer(h * 0.012f, 0f, h * 0.004f, 0x99000000.toInt())
        val hh = s.hour.toString().padStart(2, '0')
        val mm = s.minute.toString().padStart(2, '0')
        canvas.drawText("$hh:$mm", cx, top, clock)

        sub.setShadowLayer(h * 0.009f, 0f, h * 0.003f, 0x99000000.toInt())
        sub.textSize = h * 0.024f
        canvas.drawText(s.dateLabel, cx, top + h * 0.045f, sub)

        sub.textSize = h * 0.020f
        val batt = (if (s.charging) "⚡ " else "") + "${s.batteryPct}%"
        canvas.drawText(batt, cx, top + h * 0.082f, sub)
    }
}
