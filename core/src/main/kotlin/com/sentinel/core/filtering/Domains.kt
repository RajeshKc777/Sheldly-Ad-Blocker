package com.sentinel.core.filtering

import java.net.IDN
import java.util.Locale

/**
 * Normalisation and validation for the only identifier this project filters on:
 * DNS host names.
 *
 * Every path funnels through [normalize]: the DNS packet parser, the rule
 * parser and the user-facing allowlist editor, so that `Example.COM.`,
 * `example.com` and a rule-file entry `example.com` are the same key.
 *
 * Design notes:
 *  - We do not try to be a general URL parser. Anything with a scheme, path,
 *    query or wildcard is rejected rather than "cleaned", because silently
 *    rewriting bad input hides rule-file bugs.
 *  - Internationalised names are converted to their ASCII (punycode) form with
 *    [IDN.toASCII], which is what actually appears on the wire.
 *  - A trailing dot means "fully qualified"; it is dropped, not rejected.
 */
object Domains {

    /** Maximum length of a DNS name in presentation form, per RFC 1035. */
    const val MAX_HOSTNAME_LENGTH = 253

    /** Maximum length of a single label, per RFC 1035. */
    const val MAX_LABEL_LENGTH = 63

    private const val EXTRA_LABEL_CHARS = "-_"

    /** Characters that mean "this is not a bare host name" (URL, glob, regex, separator). */
    private const val FORBIDDEN = "/\\:@?#*|^$\"'()[]<>,;="

    /**
     * Returns the canonical ASCII form of [raw], or `null` when [raw] cannot be
     * a host name. Callers must treat `null` as "do not filter", never as
     * "block".
     */
    fun normalize(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null

        // Fully-qualified trailing dot(s): "example.com." -> "example.com".
        value = value.trimEnd('.')
        if (value.isEmpty()) return null

        for (ch in value) {
            if (ch.isWhitespace() || ch.isISOControl() || ch in FORBIDDEN) return null
        }

        // A leading dot produces an empty label (" .example.com"), reject it.
        if (value.startsWith('.')) return null
        if (value.contains("..")) return null

        val ascii = try {
            IDN.toASCII(value, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            return null
        }.lowercase(Locale.ROOT)

        if (ascii.length > MAX_HOSTNAME_LENGTH) return null

        var labelLength = 0
        var labelStart = true
        var labelEndsWithSeparator = false
        for (ch in ascii) {
            if (ch == '.') {
                if (labelLength == 0) return null
                if (labelLength > MAX_LABEL_LENGTH) return null
                if (labelEndsWithSeparator) return null
                labelLength = 0
                labelStart = true
                labelEndsWithSeparator = false
                continue
            }
            if (!isLabelChar(ch)) return null
            if (labelStart && (ch == '-' || ch == '_')) return null
            labelLength++
            labelStart = false
            labelEndsWithSeparator = ch == '-' || ch == '_'
        }
        if (labelLength == 0 || labelLength > MAX_LABEL_LENGTH) return null
        if (labelEndsWithSeparator) return null

        return ascii
    }

    fun isValid(host: String): Boolean = normalize(host) != null

    /** True when [host] is already normalised (no work would be done by [normalize]). */
    fun isNormalized(host: String): Boolean = normalize(host) == host

    /**
     * True for IPv4/IPv6 literals in presentation form. IP literals are valid
     * DNS answer data but are not host names, so they never match domain rules.
     */
    fun isIpLiteral(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host.contains(':')) return host.all { it.isDigit() || it in "abcdefABCDEF:." }
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
                (part.toIntOrNull() ?: return false) in 0..255
        }
    }

    /**
     * Returns every label-aligned suffix of a normalised host name, most
     * specific first.
     *
     * `a.b.example.com` -> `a.b.example.com`, `b.example.com`, `example.com`, `com`.
     *
     * This is what makes suffix matching boundary-correct: `notexample.com`
     * produces only `notexample.com` and `com`, so a rule for `example.com`
     * cannot match it. A naive `endsWith("example.com")` would be wrong.
     */
    fun suffixes(host: String): List<String> {
        val result = ArrayList<String>(4)
        result.add(host)
        var index = host.indexOf('.')
        while (index >= 0 && index + 1 < host.length) {
            result.add(host.substring(index + 1))
            index = host.indexOf('.', index + 1)
        }
        return result
    }

    private fun isLabelChar(ch: Char): Boolean =
        ch in 'a'..'z' || ch in '0'..'9' || ch in EXTRA_LABEL_CHARS
}
