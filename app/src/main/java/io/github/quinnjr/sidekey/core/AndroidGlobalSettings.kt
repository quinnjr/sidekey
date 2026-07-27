package io.github.quinnjr.sidekey.core

import android.content.Context
import android.provider.Settings

/** Real [GlobalSettingsPort] over `Settings.Global`. Requires `WRITE_SECURE_SETTINGS` to write. */
class AndroidGlobalSettings(context: Context) : GlobalSettingsPort {

    private val resolver = context.applicationContext.contentResolver

    override fun getInt(key: String): Int? = try {
        Settings.Global.getInt(resolver, key)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    override fun getString(key: String): String? = Settings.Global.getString(resolver, key)

    override fun putInt(key: String, value: Int): WriteResult = guard {
        if (Settings.Global.putInt(resolver, key, value)) WriteResult.Ok
        else WriteResult.Rejected("putInt returned false for $key")
    }

    override fun putString(key: String, value: String): WriteResult = guard {
        if (Settings.Global.putString(resolver, key, value)) WriteResult.Ok
        else WriteResult.Rejected("putString returned false for $key")
    }

    private inline fun guard(block: () -> WriteResult): WriteResult = try {
        block()
    } catch (_: SecurityException) {
        WriteResult.NoPermission
    } catch (e: Exception) {
        WriteResult.Rejected(e.message ?: e::class.java.simpleName)
    }
}
