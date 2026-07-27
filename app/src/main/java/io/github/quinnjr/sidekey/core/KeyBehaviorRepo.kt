package io.github.quinnjr.sidekey.core

/**
 * Owns both side-key settings and keeps them consistent.
 *
 * One UI 8.5 ships them desynced: [KEY_FUNCTION_LONGPRESS] reads `long_press_power_off`
 * while [KEY_PBLP] reads `101`. Only [KEY_PBLP] changes behaviour; [KEY_FUNCTION_LONGPRESS]
 * is written alongside it so Samsung's own Settings screen stops lying to the user.
 */
class KeyBehaviorRepo(private val settings: GlobalSettingsPort) {

    fun observed(): Behavior? = settings.getInt(KEY_PBLP)?.let(Behavior::fromInt)

    fun apply(behavior: Behavior): WriteResult {
        val result = settings.putInt(KEY_PBLP, behavior.pblp)
        if (result != WriteResult.Ok) return result

        behavior.functionKeyItem?.let { settings.putString(KEY_FUNCTION_LONGPRESS, it) }

        val readBack = settings.getInt(KEY_PBLP)
        return if (readBack == behavior.pblp) {
            WriteResult.Ok
        } else {
            WriteResult.Overridden(readBack ?: -1)
        }
    }
}
