package io.github.quinnjr.sidekey.bootstrap

sealed interface BootstrapState {
    /** The app already holds `WRITE_SECURE_SETTINGS`; nothing more is needed, ever. */
    data object Granted : BootstrapState

    /** Shizuku is bound and authorised — the self-grant can run now. */
    data object ShizukuReady : BootstrapState

    data object ShizukuMissing : BootstrapState

    data object ShizukuDenied : BootstrapState
}

/**
 * Capability probe, kept as an interface so [Bootstrapper] is testable without Shizuku on
 * the classpath.
 */
interface BootstrapEnv {
    fun hasPermission(): Boolean
    fun shizukuAvailable(): Boolean
    fun shizukuGranted(): Boolean
    fun runShell(command: String): Int
}

/**
 * Turns a one-time Shizuku session into a permanent capability.
 *
 * Shizuku dies on every reboot unless the device is rooted, which would be useless for a
 * boot-persistence feature. So Shizuku is used exactly once — to `pm grant` this package
 * `WRITE_SECURE_SETTINGS`, which persists until uninstall. After that Shizuku is
 * disposable.
 */
class Bootstrapper(
    private val env: BootstrapEnv,
    private val packageName: String = DEFAULT_PACKAGE,
) {
    fun state(): BootstrapState = when {
        env.hasPermission() -> BootstrapState.Granted
        !env.shizukuAvailable() -> BootstrapState.ShizukuMissing
        !env.shizukuGranted() -> BootstrapState.ShizukuDenied
        else -> BootstrapState.ShizukuReady
    }

    fun selfGrant(): Boolean = env.runShell(grantCommand(packageName)) == 0

    companion object {
        const val DEFAULT_PACKAGE = "io.github.quinnjr.sidekey"
        const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

        /** Also shown verbatim in the setup wizard for the adb tier. */
        fun grantCommand(packageName: String = DEFAULT_PACKAGE) =
            "pm grant $packageName $PERMISSION"
    }
}
