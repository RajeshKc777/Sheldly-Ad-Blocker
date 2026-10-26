package com.sentinel.core.filtering

/** Counters describing what a rule file actually contained. */
data class ParseStats(
    val totalLines: Int = 0,
    val blankLines: Int = 0,
    val commentLines: Int = 0,
    val ignoredLines: Int = 0,
    val accepted: Int = 0,
    val duplicates: Int = 0,
    val unsupported: Int = 0,
    val invalid: Int = 0,
) {
    /** Lines that looked like rules but could not be used. */
    val rejected: Int get() = unsupported + invalid
}

/** One rejected line, kept so the UI can explain *what* was skipped. */
data class ParseIssue(
    val lineNumber: Int,
    val text: String,
    val reason: String,
)

data class ParsedRules(
    val rules: List<CompiledRule>,
    val stats: ParseStats,
    val issues: List<ParseIssue>,
    val sourceId: String,
) {
    /** Convenience for tests and single-list callers. */
    fun toMatcher(): DomainMatcher {
        val builder = DomainMatcher.Builder()
        builder.addAll(rules)
        return builder.build()
    }
}

/**
 * Parses the deliberately small subset of ad-filter syntax this project
 * implements. See docs/RESEARCH.md for the supported/unsupported matrix.
 *
 * Supported
 *  - blank lines and `!` / `#` / `[` comments
 *  - hosts files: `0.0.0.0 example.com`, `127.0.0.1 a.com b.com`, `::1 example.com`
 *  - Adblock/uBO domain rules: `||example.com^`
 *  - bare host names: `example.com` (same meaning as `||example.com^`, matching
 *    uBO's documented hosts-file semantics)
 *  - exception rules: `@@||example.com^`
 *
 * Explicitly *not* supported (counted as `unsupported`, never silently accepted)
 *  - cosmetic rules: `example.com##.ad`, `#@#`, `#?#`, `#%#`
 *  - options/modifiers: `$third-party`, `$script`, `$important`, `$badfilter`
 *  - path, query or wildcard patterns: `example.com/ads`, a bare `*` prefix, etc.
 *  - URL anchors: `|https://example.com`, `||example.com/path`
 *  - regex rules: `/^ads?\\./`
 *  - pre-parsing directives: `!#include`, `!#if`
 */
class RuleParser(private val maxReportedIssues: Int = 25) {

    fun parse(sourceId: String, text: String): ParsedRules {
        val builder = DomainMatcher.Builder()
        val issues = ArrayList<ParseIssue>(maxReportedIssues)

        var total = 0
        var blank = 0
        var comments = 0
        var ignored = 0
        var accepted = 0
        var duplicates = 0
        var unsupported = 0
        var invalid = 0

        fun report(lineNumber: Int, lineText: String, reason: String) {
            if (issues.size < maxReportedIssues) {
                issues.add(ParseIssue(lineNumber, lineText.take(160), reason))
            }
        }

        fun addHost(lineNumber: Int, rawText: String, host: String, allow: Boolean, subdomains: Boolean) {
            if (host in LOCAL_NAMES) {
                ignored++
                return
            }
            val normalized = Domains.normalize(host)
            if (normalized == null) {
                invalid++
                report(lineNumber, rawText, "not a host name")
                return
            }
            val rule = CompiledRule(
                pattern = normalized,
                matchSubdomains = subdomains,
                action = if (allow) RuleAction.ALLOW else RuleAction.BLOCK,
                origin = RuleOrigin(sourceId, lineNumber),
            )
            if (builder.add(rule)) accepted++ else duplicates++
        }

        text.lineSequence().forEachIndexed { index, rawLine ->
            total++
            val lineNumber = index + 1
            var line = rawLine.trim()

            if (line.isEmpty()) {
                blank++
                return@forEachIndexed
            }

            // Trailing hosts-file style comment: only when preceded by a space,
            // so that cosmetic markers such as `##` are not silently chopped.
            val inlineComment = line.indexOf(" #")
            if (inlineComment > 0) line = line.substring(0, inlineComment).trim()
            if (line.isEmpty()) {
                blank++
                return@forEachIndexed
            }

            if (line.startsWith("!#")) {
                unsupported++
                report(lineNumber, line, "uBO pre-parsing directive")
                return@forEachIndexed
            }

            if (line.startsWith('!') || line.startsWith('#') || line.startsWith('[')) {
                comments++
                return@forEachIndexed
            }

            if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#%#")) {
                unsupported++
                report(lineNumber, line, "cosmetic filter (no browser DOM here)")
                return@forEachIndexed
            }

            if (line.contains('\t') || line.contains(' ')) {
                val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
                if (tokens.size < 2) {
                    invalid++
                    report(lineNumber, line, "expected <address> <host>")
                    return@forEachIndexed
                }
                val address = tokens[0].removeSurrounding("[", "]")
                if (!Domains.isIpLiteral(address)) {
                    invalid++
                    report(lineNumber, line, "first column is not an IP address")
                    return@forEachIndexed
                }
                for (i in 1 until tokens.size) {
                    addHost(lineNumber, line, tokens[i], allow = false, subdomains = true)
                }
                return@forEachIndexed
            }

            if (line.startsWith("|http") || line.startsWith("|https")) {
                unsupported++
                report(lineNumber, line, "URL anchor (paths are not visible at DNS level)")
                return@forEachIndexed
            }

            if (line.startsWith('/') && line.endsWith('/') && line.length > 1) {
                unsupported++
                report(lineNumber, line, "regular expression rule")
                return@forEachIndexed
            }

            if (line.contains('$')) {
                unsupported++
                report(lineNumber, line, "filter option/modifier")
                return@forEachIndexed
            }

            val isDomainRule = line.startsWith("||") || line.startsWith("@@||")
            if (line.contains('*') || line.contains('~') ||
                (line.contains('|') && !isDomainRule)
            ) {
                unsupported++
                report(lineNumber, line, "wildcard or URL anchor")
                return@forEachIndexed
            }

            var allow = false
            var body = line
            if (body.startsWith("@@")) {
                allow = true
                body = body.substring(2)
            }

            if (body.startsWith("||")) {
                body = body.substring(2)
                if (body.endsWith('^')) body = body.dropLast(1)
                if (body.contains('/') || body.contains('*') || body.contains('^')) {
                    unsupported++
                    report(lineNumber, line, "domain rule with path or wildcard")
                    return@forEachIndexed
                }
                addHost(lineNumber, line, body, allow, subdomains = true)
                return@forEachIndexed
            }

            if (body.startsWith('|') || body.endsWith('|')) {
                unsupported++
                report(lineNumber, line, "URL anchor")
                return@forEachIndexed
            }

            // Bare token: treat as a domain (hosts-file / uBO semantics).
            if (body.contains('/') || body.contains('*') || body.contains('=')) {
                unsupported++
                report(lineNumber, line, "path, wildcard or query pattern")
                return@forEachIndexed
            }
            addHost(lineNumber, line, body, allow, subdomains = true)
        }

        val stats = ParseStats(
            totalLines = total,
            blankLines = blank,
            commentLines = comments,
            ignoredLines = ignored,
            accepted = accepted,
            duplicates = duplicates,
            unsupported = unsupported,
            invalid = invalid,
        )
        return ParsedRules(
            rules = builder.rules,
            stats = stats,
            issues = issues,
            sourceId = sourceId,
        )
    }

    private companion object {
        /** Names that appear in hosts files but are not advertising. */
        val LOCAL_NAMES = setOf(
            "localhost",
            "localhost.localdomain",
            "local",
            "broadcasthost",
            "ip6-localhost",
            "ip6-loopback",
            "ip6-allnodes",
            "ip6-allrouters",
            "0.0.0.0",
            "::1",
        )
    }
}
