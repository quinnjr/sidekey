package io.github.quinnjr.sidekey.core

/**
 * Outcome of a settings write. Every write returns one of these; silently doing nothing is
 * the worst possible failure here, because the user has lost their power button and would
 * have no idea why.
 */
sealed interface WriteResult {

    data object Ok : WriteResult

    /** The app does not hold `WRITE_SECURE_SETTINGS`. Show the setup wizard. */
    data object NoPermission : WriteResult

    data class Rejected(val reason: String) : WriteResult

    /** The write succeeded but read back as something else — the system overrode us. */
    data class Overridden(val observed: Int) : WriteResult
}
