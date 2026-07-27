package io.github.quinnjr.sidekey.core

/** `Settings.Global` key that `PhoneWindowManager` actually reads. */
const val KEY_PBLP = "power_button_long_press"

/** `Settings.Global` key that One UI's own Settings screen displays. */
const val KEY_FUNCTION_LONGPRESS = "function_key_config_longpress_selected_item"

/**
 * What the side key long-press should do.
 *
 * AOSP defines 0..5 for [KEY_PBLP]. `101` is a Samsung extension meaning "launch Bixby/AI",
 * observed on SM-S928U1 running One UI 8.5. Unknown values become [Raw] so the app remains
 * useful on devices whose extension values have not been catalogued yet.
 */
sealed class Behavior(val pblp: Int, val functionKeyItem: String?) {

    data object PowerMenu : Behavior(1, "long_press_power_off")

    data object Assistant : Behavior(5, null)

    data object Nothing : Behavior(0, null)

    data object SamsungAi : Behavior(101, null)

    data class Raw(val value: Int) : Behavior(value, null)

    companion object {
        fun fromInt(value: Int): Behavior = when (value) {
            PowerMenu.pblp -> PowerMenu
            Assistant.pblp -> Assistant
            Nothing.pblp -> Nothing
            SamsungAi.pblp -> SamsungAi
            else -> Raw(value)
        }
    }
}
