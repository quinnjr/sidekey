package io.github.quinnjr.sidekey.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IssueUrlBuilderTest {

    private fun report(model: String = "SM-S928U1", pblp: Int? = 101) = DeviceReport(
        model = model,
        device = "e3q",
        fingerprint = "samsung/e3quew/e3q:16/BP4A.251205.006/S928U1UES6DZF2:user/release-keys",
        androidRelease = "16",
        oneUi = "8.5",
        csc = "XAA",
        pblp = pblp,
        functionKeyLongPress = "long_press_power_off",
        functionKeyDoublePress = "key_camera",
        longPressPowerForAssist = 0,
        outcome = FixOutcome.Worked,
        appVersion = "0.1.0",
    )

    @Test
    fun `title carries model, one ui and observed value for dedup`() {
        assertEquals("SM-S928U1 / One UI 8.5 / pblp=101", report().title())
    }

    @Test
    fun `title renders a missing value as unknown rather than null`() {
        assertTrue(report(pblp = null).title().endsWith("pblp=unknown"))
    }

    @Test
    fun `build targets the configured repo with the device-report label`() {
        val url = IssueUrlBuilder.build("quinnjr/sidekey", report()) as IssueUrl.Ready
        assertTrue(url.uri.startsWith("https://github.com/quinnjr/sidekey/issues/new?"))
        assertTrue(url.uri.contains("labels=device-report"))
    }

    @Test
    fun `build percent-encodes characters that would break the query`() {
        val url = IssueUrlBuilder.build("quinnjr/sidekey", report(model = "A#B&C D")) as IssueUrl.Ready
        assertTrue(url.uri.contains("A%23B%26C"))
        assertFalse(url.uri.contains("A#B&C D"))
    }

    @Test
    fun `markdown contains every payload field`() {
        val md = report().toMarkdown()
        listOf(
            "SM-S928U1", "e3q", "16", "8.5", "XAA", "101",
            "long_press_power_off", "key_camera", "0.1.0", "Worked",
        ).forEach { assertTrue(md.contains(it), "markdown missing: $it") }
    }

    @Test
    fun `an oversized body falls back to a blank issue plus the raw body`() {
        val url = IssueUrlBuilder.build("quinnjr/sidekey", report(model = "X".repeat(7000)))
        assertTrue(url is IssueUrl.TooLong, "expected TooLong, got $url")
        url as IssueUrl.TooLong
        assertEquals("https://github.com/quinnjr/sidekey/issues/new", url.blankIssueUri)
        assertTrue(url.body.contains("XXX"))
    }
}
