package ai.lazycode.kinetic.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltCalibratorTest {
    private val g = 9.80665f
    private val eps = 0.01f

    /** Gravity reading after the device turns clockwise (as its user sees the screen) by [deg]. */
    private fun clockwise(v: FloatArray, deg: Float): FloatArray {
        val a = Math.toRadians(deg.toDouble()).toFloat()
        return floatArrayOf(v[0] * cos(a) - v[1] * sin(a), v[0] * sin(a) + v[1] * cos(a), v[2])
    }

    private fun hold(c: TiltCalibrator, v: FloatArray, seconds: Float) {
        var t = 0f
        while (t < seconds) { c.update(v[0], v[1], v[2], 0.02f); t += 0.02f }
    }

    @Test
    fun normalHoldsMatchTheOriginalMapping() {
        val poses = listOf(
            floatArrayOf(0f, g, 0f),                 // upright
            floatArrayOf(-3f, 6f, 7f),               // held at ~45°, tilted right
            floatArrayOf(2.5f, 1f, 9.4f),            // nearly flat, tilted left
            floatArrayOf(-2f, 1.5f, -9.5f),          // lying on your back, phone overhead
        )
        for (p in poses) {
            val c = TiltCalibrator()
            hold(c, p, 2f)
            assertEquals(-p[0] / g, c.tiltX, eps)
            assertEquals(p[1] / g, c.tiltY, eps)
            assertFalse(c.sideways)
        }
    }

    @Test
    fun lyingOnYourSideIsCentred() {
        for (side in listOf(floatArrayOf(-g, 0f, 0f), floatArrayOf(g, 0f, 0f), floatArrayOf(-9.2f, 0f, 3.3f))) {
            val c = TiltCalibrator()
            c.update(side[0], side[1], side[2], 0f)
            assertTrue(c.sideways)
            assertEquals(0f, c.tiltX, eps)
        }
    }

    @Test
    fun lyingOnYourSideSteersLikeAWheel() {
        for (side in listOf(floatArrayOf(-g, 0f, 0f), floatArrayOf(g, 0f, 0f))) {
            val c = TiltCalibrator()
            hold(c, side, 0.5f)
            hold(c, clockwise(side, 20f), 0.5f)
            assertEquals(sin(Math.toRadians(20.0)).toFloat(), c.tiltX, eps)
            hold(c, clockwise(side, -20f), 0.5f)
            assertEquals(-sin(Math.toRadians(20.0)).toFloat(), c.tiltX, eps)
        }
    }

    @Test
    fun steeringOnYourSideDoesNotMoveTiltY() {
        val side = floatArrayOf(-g, 0f, 0f)
        val c = TiltCalibrator()
        hold(c, side, 0.5f)
        val rest = c.tiltY
        hold(c, clockwise(side, 25f), 0.5f)
        assertEquals(rest, c.tiltY, eps)
    }

    @Test
    fun lyingDownMidGameRecalibratesAndSittingUpRestores() {
        val c = TiltCalibrator()
        hold(c, floatArrayOf(0f, 7f, 7f), 2f)
        hold(c, floatArrayOf(-g, 0f, 0f), 2f)
        assertTrue(c.sideways)
        assertEquals(0f, c.tiltX, eps)
        hold(c, floatArrayOf(-3f, 6f, 7f), 2f)
        assertFalse(c.sideways)
        assertEquals(3f / g, c.tiltX, eps)
    }

    @Test
    fun aHardHeldSteerIsNotMistakenForLyingDown() {
        val c = TiltCalibrator()
        val steer = floatArrayOf(-6.2f, 5.4f, 5.4f)   // ~39° hard right, held
        hold(c, steer, 6f)
        assertFalse(c.sideways)
        assertEquals(6.2f / g, c.tiltX, eps)
    }

    @Test
    fun resetRecalibratesFromTheNextReading() {
        val c = TiltCalibrator()
        hold(c, floatArrayOf(-g, 0f, 0f), 2f)
        c.reset()
        c.update(0f, g, 0f, 0f)
        assertFalse(c.sideways)
        assertEquals(0f, c.tiltX, eps)
    }
}
