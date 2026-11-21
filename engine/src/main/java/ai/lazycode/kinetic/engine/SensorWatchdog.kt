package ai.lazycode.kinetic.engine

/**
 * Notices a tilt feed that went quiet while the wallpaper is visible. Some phones (MIUI/HyperOS
 * power managers, idle-uid sensor limits) stop delivering accelerometer events without telling
 * the app, and nothing brings them back until the listener is registered again. Times are
 * System.nanoTime-style nanoseconds.
 */
class SensorWatchdog(
    private val silenceNanos: Long = 2_000_000_000L,
) {
    private var lastSeen = 0L

    /** The listener was (re)registered at [now]. */
    fun started(now: Long) {
        lastSeen = now
    }

    /** A sensor event arrived at [now]. */
    fun onEvent(now: Long) {
        lastSeen = now
    }

    /** True when no event has arrived for [silenceNanos]; call [started] after re-registering. */
    fun shouldRestart(now: Long): Boolean = now - lastSeen > silenceNanos
}
