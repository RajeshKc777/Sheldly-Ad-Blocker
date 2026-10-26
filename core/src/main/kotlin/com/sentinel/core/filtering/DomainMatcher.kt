package com.sentinel.core.filtering

/**
 * Immutable domain matcher.
 *
 * Why hash maps instead of a trie: a host name has 2–5 labels, so a match is
 * 2–5 `HashMap` lookups with no allocation beyond the suffix strings. That is
 * roughly 100 ns per query on a mid-range phone, which is irrelevant next to a
 * DNS round trip. A trie would save a few hundred nanoseconds and cost a large
 * amount of code plus tricky memory management. Revisit only with measurements
 * that show matching is a bottleneck (see docs/TESTING.md).
 *
 * Correctness rules implemented here:
 *  - allow rules always win over block rules (documented project decision);
 *  - the most specific rule at a given position wins;
 *  - matching is label-aligned, so `notexample.com` never matches `example.com`;
 *  - invalid input is "not filtered", never "blocked".
 */
class DomainMatcher private constructor(
    private val blockSuffix: Map<String, CompiledRule>,
    private val blockExact: Map<String, CompiledRule>,
    private val allowSuffix: Map<String, CompiledRule>,
    private val allowExact: Map<String, CompiledRule>,
) : DomainFilter {

    override val blockRuleCount: Int get() = blockSuffix.size + blockExact.size

    val allowRuleCount: Int get() = allowSuffix.size + allowExact.size

    override fun shouldBlock(domain: String): Boolean = evaluate(domain).blocked

    override fun evaluate(domain: String): FilterVerdict {
        val host = Domains.normalize(domain)
            ?: return FilterVerdict(false, null, FilterVerdict.Reason.INVALID_DOMAIN)

        // Most specific first: the host itself, then each parent suffix.
        var suffixStart = 0
        var position = 0
        while (true) {
            val candidate = if (suffixStart == 0) host else host.substring(suffixStart)

            if (position == 0) {
                allowExact[host]?.let { return FilterVerdict(false, it, FilterVerdict.Reason.ALLOW_RULE) }
            }
            allowSuffix[candidate]?.let { return FilterVerdict(false, it, FilterVerdict.Reason.ALLOW_RULE) }

            val nextDot = host.indexOf('.', if (suffixStart == 0) 0 else suffixStart)
            if (nextDot < 0 || nextDot + 1 >= host.length) break
            suffixStart = nextDot + 1
            position++
        }

        suffixStart = 0
        position = 0
        while (true) {
            val candidate = if (suffixStart == 0) host else host.substring(suffixStart)

            if (position == 0) {
                blockExact[host]?.let { return FilterVerdict(true, it, FilterVerdict.Reason.BLOCK_RULE) }
            }
            blockSuffix[candidate]?.let { return FilterVerdict(true, it, FilterVerdict.Reason.BLOCK_RULE) }

            val nextDot = host.indexOf('.', if (suffixStart == 0) 0 else suffixStart)
            if (nextDot < 0 || nextDot + 1 >= host.length) break
            suffixStart = nextDot + 1
            position++
        }

        return FilterVerdict(false, null, FilterVerdict.Reason.NO_MATCH)
    }

    fun isEmpty(): Boolean = blockSuffix.isEmpty() && blockExact.isEmpty() &&
        allowSuffix.isEmpty() && allowExact.isEmpty()

    /** Builds a matcher, rejecting duplicates so rule files are predictable. */
    class Builder {
        private val blockSuffix = LinkedHashMap<String, CompiledRule>()
        private val blockExact = LinkedHashMap<String, CompiledRule>()
        private val allowSuffix = LinkedHashMap<String, CompiledRule>()
        private val allowExact = LinkedHashMap<String, CompiledRule>()

        private val accepted = ArrayList<CompiledRule>()

        /** Rules that were actually added, in insertion order. */
        val rules: List<CompiledRule> get() = accepted

        var duplicateCount: Int = 0
            private set

        fun add(rule: CompiledRule): Boolean {
            val target = when {
                rule.action == RuleAction.ALLOW && rule.matchSubdomains -> allowSuffix
                rule.action == RuleAction.ALLOW -> allowExact
                rule.matchSubdomains -> blockSuffix
                else -> blockExact
            }
            if (target.containsKey(rule.pattern)) {
                duplicateCount++
                return false
            }
            target[rule.pattern] = rule
            accepted.add(rule)
            return true
        }

        fun addAll(rules: Iterable<CompiledRule>) {
            for (rule in rules) add(rule)
        }

        fun build(): DomainMatcher = DomainMatcher(
            blockSuffix = HashMap(blockSuffix),
            blockExact = HashMap(blockExact),
            allowSuffix = HashMap(allowSuffix),
            allowExact = HashMap(allowExact),
        )
    }
}
