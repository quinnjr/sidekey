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
 * `power_button_long_press` about 3.7 seconds *after* this broadcast, so an immediate write
 * would simply be clobbered. [PinService] observes instead.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val desired = DesiredBehaviorStore(context).desired()
        if (desired == null) {
            Log.i(TAG, "boot: no behavior chosen yet, nothing to pin")
            return
        }

        Log.i(TAG, "boot: starting PinService for desired=$desired")
        PinService.start(context, desired)
    }

    private companion object {
        const val TAG = "SideKey"
    }
}
