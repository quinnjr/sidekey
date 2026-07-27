package io.github.quinnjr.sidekey.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The measured behaviour this guards: on SM-S928U1 / One UI 8.5, system_server rewrites
 * power_button_long_press to 101 roughly 3.7 seconds AFTER BOOT_COMPLETED.
 */
class PinPolicyTest {

    private val policy = PinPolicy(desired = 1, stabilityWindowMs = 60_000)

    @Test
    fun `writes when the observed value is wrong`() {
        assertEquals(PinDecision.Write, policy.decide(observed = 101, elapsedMs = 3_700))
    }

    @Test
    fun `writes when the value is missing entirely`() {
        assertEquals(PinDecision.Write, policy.decide(observed = null, elapsedMs = 0))
    }

    @Test
    fun `idles while correct and inside the stability window`() {
        assertEquals(PinDecision.Idle, policy.decide(observed = 1, elapsedMs = 3_700))
    }

    @Test
    fun `stops once correct and past the stability window`() {
        assertEquals(PinDecision.Stop, policy.decide(observed = 1, elapsedMs = 60_000))
    }

    @Test
    fun `a late system write past the window still triggers a write`() {
        assertEquals(PinDecision.Write, policy.decide(observed = 101, elapsedMs = 90_000))
    }
}
