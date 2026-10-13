package com.salvia.salviabrowxer.ui.utils

/**
 * Why a page did not load, as far as the chrome needs to know.
 *
 * The three cases read differently to a user and are not interchangeable: a network failure is
 * usually the connection, an HTTP status is the site's answer, and a certificate problem is the one
 * case this browser refuses to work around. The WebView client classifies, the view model picks the
 * message.
 */
enum class PageErrorKind {
    /** DNS, timeout, connection reset — the document never arrived. */
    NETWORK,

    /** The server answered with a 4xx/5xx status for the document itself. */
    HTTP,

    /** An untrusted certificate. The request is always cancelled; it is never overridden. */
    CERTIFICATE
}
