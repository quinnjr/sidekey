package io.github.quinnjr.sidekey.core

sealed interface PinDecision {
    data object Write : PinDecision
    data object Idle : PinDecision
    data object Stop : PinDecision
}

/**
 * Decides whether to re-assert the setting, wait, or stop watching.
 *
 * Measured on SM-S928U1 / One UI 8.5: `system_server` rewrites [KEY_PBLP] to `101` roughly
 * 3.7 seconds *after* `BOOT_COMPLETED`. Writing once on boot therefore loses the race, and
 * guessing a delay is fragile. Instead the caller observes the setting and feeds every
 * observation here; [Stop] is only returned once the value has stayed correct for the whole
 * [stabilityWindowMs], so a late write is still caught.
 */
class PinPolicy(
    private val desired: Int,
    private val stabilityWindowMs: Long = 60_000,
) {
    fun decide(observed: Int?, elapsedMs: Long): PinDecision = when {
        observed != desired -> PinDecision.Write
        elapsedMs >= stabilityWindowMs -> PinDecision.Stop
        else -> PinDecision.Idle
    }
}
