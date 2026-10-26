package com.sentinel.core.filtering

/** What a compiled rule does when it matches. */
enum class RuleAction { BLOCK, ALLOW }

/** Where a rule came from, for diagnostics and user explanations. */
data class RuleOrigin(
    val sourceId: String,
    val line: Int? = null,
)

/**
 * A single domain rule after parsing and normalisation.
 *
 * [pattern] is always a normalised host name. [matchSubdomains] distinguishes
 * `||example.com^` (example.com *and* its subdomains) from an exact-host rule
 * that only matches `example.com` itself.
 */
data class CompiledRule(
    val pattern: String,
    val matchSubdomains: Boolean,
    val action: RuleAction,
    val origin: RuleOrigin,
) {
    override fun toString(): String = buildString {
        if (action == RuleAction.ALLOW) append("@@")
        if (matchSubdomains) append("||")
        append(pattern)
        if (matchSubdomains) append('^')
    }
}

/** Outcome of evaluating one host name against a [DomainFilter]. */
data class FilterVerdict(
    val blocked: Boolean,
    val matchedRule: CompiledRule? = null,
    val reason: Reason,
) {
    enum class Reason {
        /** A block rule matched. */
        BLOCK_RULE,

        /** A rule from an allowlist (user or upstream `@@`) matched. */
        ALLOW_RULE,

        /** No rule matched; the query may proceed. */
        NO_MATCH,

        /** The input was not a host name; treated as "do not filter". */
        INVALID_DOMAIN,
    }

    val isAllowlisted: Boolean get() = reason == Reason.ALLOW_RULE
}

/**
 * The contract the rest of the application codes against.
 *
 * `shouldBlock` exists because it is the API the project brief specifies and
 * the API the VPN service actually needs; `evaluate` exists because a bare
 * boolean cannot explain *why*, and unexplained blocking is unhelpful both to
 * users and to whoever debugs a broken app later.
 */
interface DomainFilter {
    fun shouldBlock(domain: String): Boolean

    fun evaluate(domain: String): FilterVerdict

    /** Number of block patterns currently loaded. */
    val blockRuleCount: Int
}
