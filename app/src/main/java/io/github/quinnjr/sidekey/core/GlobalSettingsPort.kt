package io.github.quinnjr.sidekey.core

/**
 * The seam between decision logic and `Settings.Global`.
 *
 * Every class that reads or writes settings depends on this rather than on a
 * `ContentResolver`, so the logic is exercised on the JVM with no Android runtime.
 */
interface GlobalSettingsPort {
    fun getInt(key: String): Int?
    fun getString(key: String): String?
    fun putInt(key: String, value: Int): WriteResult
    fun putString(key: String, value: String): WriteResult
}
