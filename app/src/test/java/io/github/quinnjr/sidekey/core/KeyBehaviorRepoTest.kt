package io.github.quinnjr.sidekey.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyBehaviorRepoTest {

    @Test
    fun `apply writes pblp and syncs the function key string`() {
        val fake = FakeGlobalSettings()
        assertEquals(WriteResult.Ok, KeyBehaviorRepo(fake).apply(Behavior.PowerMenu))
        assertEquals(1, fake.getInt(KEY_PBLP))
        assertEquals("long_press_power_off", fake.getString(KEY_FUNCTION_LONGPRESS))
    }

    @Test
    fun `apply leaves the function key untouched when the behavior has no string`() {
        val fake = FakeGlobalSettings()
        KeyBehaviorRepo(fake).apply(Behavior.Assistant)
        assertEquals(5, fake.getInt(KEY_PBLP))
        assertNull(fake.getString(KEY_FUNCTION_LONGPRESS))
    }

    @Test
    fun `apply reports NoPermission without writing anything`() {
        val fake = FakeGlobalSettings(permitted = false)
        assertEquals(WriteResult.NoPermission, KeyBehaviorRepo(fake).apply(Behavior.PowerMenu))
        assertTrue(fake.writes.isEmpty())
    }

    @Test
    fun `observed returns null when the key is absent`() {
        assertNull(KeyBehaviorRepo(FakeGlobalSettings()).observed())
    }

    @Test
    fun `observed reads back the current behavior`() {
        val fake = FakeGlobalSettings()
        fake.systemOverride(KEY_PBLP, 101)
        assertEquals(Behavior.SamsungAi, KeyBehaviorRepo(fake).observed())
    }
}
