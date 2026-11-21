package ai.lazycode.kinetic.engine

import kotlin.math.exp

/**
 * Gravity from a raw accelerometer, for phones without a gravity sensor (budget Xiaomi/Redmi
 * models ship only an accelerometer). A one-pole low-pass keeps the slow gravity direction and
 * drops most hand shake; [x]/[y]/[z] then feed [TiltCalibrator] like TYPE_GRAVITY values.
 */
class GravityFilter(private val tauS: Float = 0.1f) {
    var x = 0f
        private set
    var y = 0f
        private set
    var z = 0f
        private set
    private var started = false

    fun reset() {
        started = false
    }

    /** Feeds one accelerometer reading (m/s²); [dtS] is the time since the previous one. */
    fun update(ax: Float, ay: Float, az: Float, dtS: Float) {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite() || !dtS.isFinite()) return // would stick forever
        if (!started) {
            started = true
            x = ax; y = ay; z = az
            return
        }
        val k = 1f - exp(-dtS / tauS)
        x += (ax - x) * k
        y += (ay - y) * k
        z += (az - z) * k
    }
}
