package ai.lazycode.kinetic.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorWatchdogTest {
    private val ms = 1_000_000L

    @Test
    fun aSteadyFeedNeverRestarts() {
        val w = SensorWatchdog()
        w.started(0)
        var t = 0L
        while (t < 10_000 * ms) {
            w.onEvent(t)
            assertFalse(w.shouldRestart(t))
            t += 20 * ms
        }
    }

    @Test
    fun aFeedThatGoesSilentIsRestarted() {
        val w = SensorWatchdog()
        w.started(0)
        w.onEvent(100 * ms)
        assertFalse(w.shouldRestart(1_000 * ms))
        assertTrue(w.shouldRestart(2_200 * ms))
    }

    @Test
    fun aFeedThatNeverStartsIsRestarted() {
        val w = SensorWatchdog()
        w.started(0)
        assertTrue(w.shouldRestart(2_100 * ms))
    }

    @Test
    fun restartsAreSpacedOut() {
        val w = SensorWatchdog()
        w.started(0)
        assertTrue(w.shouldRestart(2_100 * ms))
        w.started(2_100 * ms)
        assertFalse(w.shouldRestart(3_000 * ms))
        assertTrue(w.shouldRestart(4_300 * ms))
    }
}
