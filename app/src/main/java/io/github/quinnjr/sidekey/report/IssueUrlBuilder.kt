package io.github.quinnjr.sidekey.report

import java.net.URLEncoder

sealed interface IssueUrl {
    data class Ready(val uri: String) : IssueUrl

    /** Too long for a query string: open [blankIssueUri] and paste [body] from the clipboard. */
    data class TooLong(val blankIssueUri: String, val body: String) : IssueUrl
}

/**
 * Builds a prefilled GitHub issue URL. No backend, no hosting, no stored user data.
 *
 * Uses plain `issues/new?title=&body=` rather than an issue-form template, because form
 * prefill requires per-field ids and silently breaks whenever the template is edited.
 */
object IssueUrlBuilder {

    /** GitHub rejects issue URLs beyond roughly 8000 characters; leave headroom. */
    const val MAX_URL_LENGTH = 6000

    private const val LABEL = "device-report"

    fun build(repo: String, report: DeviceReport): IssueUrl {
        val base = "https://github.com/$repo/issues/new"
        val body = report.toMarkdown()
        val uri = buildString {
            append(base)
            append("?labels=").append(encode(LABEL))
            append("&title=").append(encode(report.title()))
            append("&body=").append(encode(body))
        }
        return if (uri.length <= MAX_URL_LENGTH) IssueUrl.Ready(uri) else IssueUrl.TooLong(base, body)
    }

    /**
     * `URLEncoder` targets `application/x-www-form-urlencoded`, so a space becomes `+` —
     * correct for a query string and what GitHub expects.
     */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
