package ai.lazycode.kinetic.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Turns gravity-sensor readings (device axes, m/s²) into [tiltX]/[tiltY] for scenes, in any
 * posture.
 *
 * Normal holds (upright, reclined, flat, lying on your back with the phone overhead) keep the
 * original mapping exactly: tiltX = -gx/g, tiltY = gy/g. That mapping assumes the phone's
 * left-right axis is roughly level, which fails when you lie on your side: that axis then points
 * at the floor, gx sits near ±g and tilting hardly changes it. So when gravity runs mostly along
 * the x axis for a moment, the current pose becomes the neutral one and tilt is measured
 * relative to it: turning the phone like a steering wheel steers, and tiltY reads the other
 * rotation. Sitting back up returns to the normal mapping.
 */
class TiltCalibrator {
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    /** True while tilt is measured relative to a sideways neutral pose. */
    var sideways = false
        private set

    // Slow copy of the gravity direction, for posture decisions only.
    private val slow = FloatArray(3)
    private var started = false
    private var timer = 0f

    // Sideways frame: neutral gravity [b], steer direction [w], second axis [u] (all unit, orthogonal).
    private val b = FloatArray(3)
    private val w = FloatArray(3)
    private val u = FloatArray(3)

    /** Forget the posture; the next reading decides it again (call when the scene becomes visible). */
    fun reset() {
        started = false
        sideways = false
        timer = 0f
    }

    /** Feeds one reading; [dtS] is the time since the previous one. */
    fun update(gx: Float, gy: Float, gz: Float, dtS: Float) {
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        if (n < 1e-3f) return
        val x = gx / n
        val y = gy / n
        val z = gz / n

        if (!started) {
            started = true
            slow[0] = x; slow[1] = y; slow[2] = z
            if (abs(x) > ENTER) setNeutral(x, y, z)
        } else {
            val k = 1f - exp(-dtS / SLOW_TAU)
            slow[0] += (x - slow[0]) * k
            slow[1] += (y - slow[1]) * k
            slow[2] += (z - slow[2]) * k
            decide(x, y, z, dtS)
        }

        if (sideways) {
            tiltX = (x * w[0] + y * w[1] + z * w[2]).coerceIn(-1f, 1f)
            tiltY = (x * u[0] + y * u[1] + z * u[2]).coerceIn(-1f, 1f)
        } else {
            tiltX = (-gx / G).coerceIn(-1f, 1f)
            tiltY = (gy / G).coerceIn(-1f, 1f)
        }
    }

    private fun decide(x: Float, y: Float, z: Float, dtS: Float) {
        val sn = sqrt(slow[0] * slow[0] + slow[1] * slow[1] + slow[2] * slow[2])
        val sx = abs(slow[0]) / sn
        val wantChange = if (!sideways) {
            sx > ENTER
        } else {
            val cosFromNeutral = (slow[0] * b[0] + slow[1] * b[1] + slow[2] * b[2]) / sn
            sx < EXIT || cosFromNeutral < MOVED_COS
        }
        timer = if (wantChange) timer + dtS else 0f
        if (timer < SETTLE) return
        timer = 0f
        if (abs(x) > ENTER || (sideways && sx >= EXIT)) setNeutral(x, y, z) else sideways = false
    }

    private fun setNeutral(x: Float, y: Float, z: Float) {
        sideways = true
        b[0] = x; b[1] = y; b[2] = z
        // Steer = turning like a wheel (rotation in the screen plane, z × b), blended with
        // "lower the right edge" (-x made perpendicular to b) as the screen tips toward flat.
        var w0 = -b[1] + b[2] * b[2] * -(1f - b[0] * b[0])
        var w1 = b[0] + b[2] * b[2] * (b[0] * b[1])
        var w2 = b[2] * b[2] * (b[0] * b[2])
        var wn = sqrt(w0 * w0 + w1 * w1 + w2 * w2)
        if (wn < 0.3f) { w0 = -b[1]; w1 = b[0]; w2 = 0f; wn = sqrt(w0 * w0 + w1 * w1) }
        w[0] = w0 / wn; w[1] = w1 / wn; w[2] = w2 / wn
        // u = w × b
        u[0] = w[1] * b[2] - w[2] * b[1]
        u[1] = w[2] * b[0] - w[0] * b[2]
        u[2] = w[0] * b[1] - w[1] * b[0]
    }

    private companion object {
        const val G = 9.80665f          // SensorManager.GRAVITY_EARTH
        const val ENTER = 0.8f          // |gx|/g above this (≈53°+) for SETTLE s = lying on a side
        const val EXIT = 0.45f          // |gx|/g below this for SETTLE s = back to a normal hold
        const val MOVED_COS = 0.643f    // cos 50°: a sideways pose this far off neutral = new pose
        const val SETTLE = 1.0f         // seconds a posture must hold before it counts
        const val SLOW_TAU = 0.3f       // seconds, posture smoothing
    }
}
