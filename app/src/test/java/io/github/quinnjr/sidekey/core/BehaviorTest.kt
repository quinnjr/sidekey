package io.github.quinnjr.sidekey.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BehaviorTest {

    @Test
    fun `power menu maps to 1 and syncs the function key string`() {
        assertEquals(1, Behavior.PowerMenu.pblp)
        assertEquals("long_press_power_off", Behavior.PowerMenu.functionKeyItem)
    }

    @Test
    fun `samsung ai maps to 101 and leaves the function key alone`() {
        assertEquals(101, Behavior.SamsungAi.pblp)
        assertNull(Behavior.SamsungAi.functionKeyItem)
    }

    @Test
    fun `known values round-trip through fromInt`() {
        assertEquals(Behavior.PowerMenu, Behavior.fromInt(1))
        assertEquals(Behavior.Assistant, Behavior.fromInt(5))
        assertEquals(Behavior.Nothing, Behavior.fromInt(0))
        assertEquals(Behavior.SamsungAi, Behavior.fromInt(101))
    }

    @Test
    fun `unknown values become Raw so other devices still work`() {
        assertEquals(Behavior.Raw(77), Behavior.fromInt(77))
        assertEquals(77, Behavior.fromInt(77).pblp)
        assertNull(Behavior.fromInt(77).functionKeyItem)
    }
}
