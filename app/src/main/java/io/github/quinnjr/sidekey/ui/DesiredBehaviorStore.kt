package io.github.quinnjr.sidekey.ui

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers the behaviour the user asked for.
 *
 * Backed by **device-protected storage** so [io.github.quinnjr.sidekey.service.BootReceiver]
 * can read it during direct boot, before the user has unlocked. Credential-protected
 * storage is unreadable at that point, which would leave the side key broken from reboot
 * until first unlock — measured at over three minutes on a real device.
 *
 * `SharedPreferences` rather than DataStore on purpose: the receiver reads this
 * synchronously on the main thread during boot, where a coroutine-only API is both awkward
 * and slower.
 *
 * A null result means the user has never chosen, so nothing should be pinned.
 */
class DesiredBehaviorStore(context: Context) {

    private val prefs = context.applicationContext
        .let { app -> if (app.isDeviceProtectedStorage) app else app.createDeviceProtectedStorageContext() }
        .also { protected ->
            // No-op once migrated; harmless to attempt on every construction.
            protected.moveSharedPreferencesFrom(context.applicationContext, PREFS)
        }
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun desired(): Int? = if (prefs.contains(KEY)) prefs.getInt(KEY, DEFAULT) else null

    fun setDesired(value: Int) = prefs.edit { putInt(KEY, value) }

    fun clear() = prefs.edit { remove(KEY) }

    private companion object {
        const val PREFS = "sidekey"
        const val KEY = "desired_pblp"
        const val DEFAULT = 1
    }
}
