package io.github.quinnjr.sidekey.bootstrap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeEnv(
    var permission: Boolean = false,
    var available: Boolean = false,
    var granted: Boolean = false,
    var exitCode: Int = 0,
) : BootstrapEnv {
    val commands = mutableListOf<String>()
    override fun hasPermission() = permission
    override fun shizukuAvailable() = available
    override fun shizukuGranted() = granted
    override fun runShell(command: String): Int {
        commands += command
        return exitCode
    }
}

class BootstrapperTest {

    @Test
    fun `already granted short-circuits`() {
        assertEquals(BootstrapState.Granted, Bootstrapper(FakeEnv(permission = true)).state())
    }

    @Test
    fun `no shizuku reports missing`() {
        assertEquals(BootstrapState.ShizukuMissing, Bootstrapper(FakeEnv()).state())
    }

    @Test
    fun `shizuku present but unauthorised reports denied`() {
        assertEquals(
            BootstrapState.ShizukuDenied,
            Bootstrapper(FakeEnv(available = true, granted = false)).state(),
        )
    }

    @Test
    fun `shizuku ready when available and granted`() {
        assertEquals(
            BootstrapState.ShizukuReady,
            Bootstrapper(FakeEnv(available = true, granted = true)).state(),
        )
    }

    @Test
    fun `selfGrant runs pm grant for this package`() {
        val env = FakeEnv(available = true, granted = true)
        assertTrue(Bootstrapper(env, packageName = "io.github.quinnjr.sidekey").selfGrant())
        assertEquals(
            "pm grant io.github.quinnjr.sidekey android.permission.WRITE_SECURE_SETTINGS",
            env.commands.single(),
        )
    }

    @Test
    fun `selfGrant reports failure on a non-zero exit`() {
        assertFalse(Bootstrapper(FakeEnv(available = true, granted = true, exitCode = 1)).selfGrant())
    }
}
