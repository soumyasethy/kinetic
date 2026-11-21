package ai.lazycode.kinetic.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class GravityFilterTest {
    private val g = 9.80665f

    @Test
    fun firstReadingPassesStraightThrough() {
        val f = GravityFilter()
        f.update(1f, 2f, 9f, 0f)
        assertEquals(1f, f.x, 0f); assertEquals(2f, f.y, 0f); assertEquals(9f, f.z, 0f)
    }

    @Test
    fun aHeldTiltSettlesOnTheTrueGravityQuickly() {
        val f = GravityFilter()
        f.update(0f, g, 0f, 0f)                 // upright
        var t = 0f
        while (t < 0.5f) { f.update(-4f, 8.95f, 0f, 0.01f); t += 0.01f } // tilted right, held 0.5 s
        assertEquals(-4f, f.x, 0.05f)
        assertEquals(8.95f, f.y, 0.05f)
    }

    @Test
    fun handShakeIsMostlyFilteredOut() {
        val f = GravityFilter()
        f.update(0f, g, 0f, 0f)
        var worst = 0f
        var t = 0f
        while (t < 2f) {
            // 8 Hz, ±3 m/s² of linear shake on top of an upright hold
            f.update(3f * sin(t * 2f * Math.PI.toFloat() * 8f), g, 0f, 0.01f)
            if (t > 0.5f) worst = maxOf(worst, abs(f.x))
            t += 0.01f
        }
        assertTrue("shake left in x: $worst", worst < 0.6f)
    }
}
