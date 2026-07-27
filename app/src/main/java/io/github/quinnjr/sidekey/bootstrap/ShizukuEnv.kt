package io.github.quinnjr.sidekey.bootstrap

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Real [BootstrapEnv] backed by Shizuku.
 *
 * Every Shizuku call is wrapped: the library may be absent at runtime, the service may not
 * be running, and `newProcess` is an unstable API. A failure here must surface as a failed
 * grant with a visible reason, never a crash.
 */
class ShizukuEnv(private val context: Context) : BootstrapEnv {

    override fun hasPermission(): Boolean =
        context.checkSelfPermission(Bootstrapper.PERMISSION) == PackageManager.PERMISSION_GRANTED

    override fun shizukuAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    override fun shizukuGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun runShell(command: String): Int = runCatching {
        val method = Shizuku::class.java.getMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        process.waitFor()
    }.getOrDefault(FAILED)

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    private companion object {
        const val FAILED = -1
    }
}
