package io.github.quinnjr.sidekey.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.quinnjr.sidekey.ui.DesiredBehaviorStore

/**
 * Starts [PinService] after boot.
 *
 * Deliberately does not write the setting itself: on One UI 8.5 the system rewrites
 * `power_button_long_press` *after* boot, so an immediate write would simply be clobbered.
 * [PinService] observes instead.
 *
 * Handles `LOCKED_BOOT_COMPLETED` as well as `BOOT_COMPLETED`. Measured on SM-S928U1:
 * `BOOT_COMPLETED` only arrives once the user unlocks — three minutes after boot in one
 * run — during which the side key would still open Bixby. The locked variant fires during
 * direct boot and closes that window. Both are handled because the pair is idempotent and
 * the locked broadcast is not guaranteed on every OEM.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return

        val desired = DesiredBehaviorStore(context).desired()
        if (desired == null) {
            Log.i(TAG, "${intent.action}: no behavior chosen yet, nothing to pin")
            return
        }

        Log.i(TAG, "${intent.action}: starting PinService for desired=$desired")
        PinService.start(context, desired)
    }

    private companion object {
        const val TAG = "SideKey"

        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
        )
    }
}
