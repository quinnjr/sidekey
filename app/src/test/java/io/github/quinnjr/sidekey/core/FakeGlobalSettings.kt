package io.github.quinnjr.sidekey.core

/**
 * In-memory [GlobalSettingsPort] so every decision class can be exercised on the JVM
 * with no Android runtime and no Robolectric.
 */
class FakeGlobalSettings(
    private val ints: MutableMap<String, Int> = mutableMapOf(),
    private val strings: MutableMap<String, String> = mutableMapOf(),
    var permitted: Boolean = true,
) : GlobalSettingsPort {

    val writes = mutableListOf<Pair<String, Any>>()

    /** Simulates the OEM rewriting the value out from under us, as One UI does at boot. */
    fun systemOverride(key: String, value: Int) {
        ints[key] = value
    }

    override fun getInt(key: String): Int? = ints[key]

    override fun getString(key: String): String? = strings[key]

    override fun putInt(key: String, value: Int): WriteResult {
        if (!permitted) return WriteResult.NoPermission
        ints[key] = value
        writes += key to value
        return WriteResult.Ok
    }

    override fun putString(key: String, value: String): WriteResult {
        if (!permitted) return WriteResult.NoPermission
        strings[key] = value
        writes += key to value
        return WriteResult.Ok
    }
}
