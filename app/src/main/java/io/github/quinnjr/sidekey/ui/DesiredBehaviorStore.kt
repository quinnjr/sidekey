package io.github.quinnjr.sidekey.ui

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers the behaviour the user asked for.
 *
 * `SharedPreferences` rather than DataStore on purpose: [io.github.quinnjr.sidekey.service.BootReceiver]
 * reads this synchronously on the main thread during boot, where a coroutine-only API is
 * both awkward and slower.
 *
 * A null result means the user has never chosen, so nothing should be pinned.
 */
class DesiredBehaviorStore(context: Context) {

    private val prefs = context.applicationContext
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
