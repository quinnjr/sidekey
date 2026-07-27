package io.github.quinnjr.sidekey.report

/** Did setting the value actually restore the power menu on this device? */
enum class FixOutcome { Worked, DidNotWork, NotTried }

/**
 * Everything submitted in a device report.
 *
 * Deliberately contains no serial, IMEI, `ANDROID_ID`, account, installed-app list,
 * location, or free-text field — there is nothing to redact because nothing identifying is
 * gathered. Every field is either firmware metadata or a setting this app already reads.
 */
data class DeviceReport(
    val model: String,
    val device: String,
    val fingerprint: String,
    val androidRelease: String,
    val oneUi: String,
    val csc: String,
    val pblp: Int?,
    val functionKeyLongPress: String?,
    val functionKeyDoublePress: String?,
    val longPressPowerForAssist: Int?,
    val outcome: FixOutcome,
    val appVersion: String,
) {
    /** Shaped so duplicates are searchable and the value table builds itself from titles. */
    fun title(): String = "$model / One UI $oneUi / pblp=${pblp ?: UNKNOWN}"

    fun toMarkdown(): String = buildString {
        appendLine("| field | value |")
        appendLine("| --- | --- |")
        appendLine("| model | `$model` |")
        appendLine("| device | `$device` |")
        appendLine("| fingerprint | `$fingerprint` |")
        appendLine("| android | `$androidRelease` |")
        appendLine("| one ui | `$oneUi` |")
        appendLine("| csc | `$csc` |")
        appendLine("| `power_button_long_press` | `${pblp ?: UNKNOWN}` |")
        appendLine("| `function_key_config_longpress_selected_item` | `${functionKeyLongPress ?: UNSET}` |")
        appendLine("| `function_key_config_doublepress_selected_item` | `${functionKeyDoublePress ?: UNSET}` |")
        appendLine("| `long_press_power_for_assist` | `${longPressPowerForAssist ?: UNSET}` |")
        appendLine("| did the fix work? | ${outcome.name} |")
        appendLine("| app version | `$appVersion` |")
    }

    private companion object {
        const val UNKNOWN = "unknown"
        const val UNSET = "unset"
    }
}
