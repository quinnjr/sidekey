package io.github.quinnjr.sidekey.report

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.quinnjr.sidekey.BuildConfig
import io.github.quinnjr.sidekey.core.GlobalSettingsPort
import io.github.quinnjr.sidekey.core.KEY_FUNCTION_LONGPRESS
import io.github.quinnjr.sidekey.core.KEY_PBLP

/**
 * Reads the report payload off the device.
 *
 * The One UI version is not a public API: `Build.VERSION.SEM_PLATFORM_INT` is a Samsung SDK
 * addition and `SystemProperties.get` is on the non-SDK blocklist. Both are reached
 * reflectively and both failures degrade to `"unknown"` rather than crashing — a missing
 * version weakens a report, it should never lose one.
 */
class DeviceReportCollector(
    private val context: Context,
    private val settings: GlobalSettingsPort,
) {
    fun collect(outcome: FixOutcome): DeviceReport = DeviceReport(
        model = Build.MODEL,
        device = Build.DEVICE,
        fingerprint = Build.FINGERPRINT,
        androidRelease = Build.VERSION.RELEASE,
        oneUi = oneUiVersion(),
        csc = systemProperty("ro.csc.sales_code") ?: UNKNOWN,
        pblp = settings.getInt(KEY_PBLP),
        functionKeyLongPress = settings.getString(KEY_FUNCTION_LONGPRESS),
        functionKeyDoublePress = settings.getString(KEY_FUNCTION_DOUBLEPRESS),
        longPressPowerForAssist = runCatching {
            Settings.Secure.getInt(context.contentResolver, KEY_LONG_PRESS_POWER_FOR_ASSIST)
        }.getOrNull(),
        outcome = outcome,
        appVersion = BuildConfig.VERSION_NAME,
    )

    private fun oneUiVersion(): String {
        semPlatformInt()?.let { sem ->
            if (sem >= SEM_BASE) {
                val v = sem - SEM_BASE
                return "${v / 10000}.${(v % 10000) / 100}"
            }
        }
        return systemProperty("ro.build.version.oneui") ?: UNKNOWN
    }

    private fun semPlatformInt(): Int? = runCatching {
        Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
    }.getOrNull()

    private fun systemProperty(key: String): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    }.getOrNull()?.ifBlank { null }

    private companion object {
        const val UNKNOWN = "unknown"
        const val SEM_BASE = 90000
        const val KEY_FUNCTION_DOUBLEPRESS = "function_key_config_doublepress_selected_item"
        const val KEY_LONG_PRESS_POWER_FOR_ASSIST = "long_press_power_for_assist"
    }
}
