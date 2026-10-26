package com.sentinel.core.filtering

/**
 * Holds the user's own allow/deny entries, independent of any downloaded list.
 *
 * Kept as its own type for two reasons:
 *  - user rules change far more often than bundled lists, and rebuilding must
 *    be cheap and obviously correct;
 *  - the UI needs to validate input and tell the user *which* entry was
 *    rejected, which a silent `Set<String>` cannot do.
 */
class AllowlistManager {

    private val allow = LinkedHashSet<String>()
    private val deny = LinkedHashSet<String>()

    @Synchronized
    fun replaceAllow(domains: Collection<String>): List<String> = replace(allow, domains)

    @Synchronized
    fun replaceDeny(domains: Collection<String>): List<String> = replace(deny, domains)

    @Synchronized
    fun addAllow(domain: String): Boolean = addOne(allow, domain)

    @Synchronized
    fun removeAllow(domain: String): Boolean {
        val normalized = Domains.normalize(domain) ?: return false
        return allow.remove(normalized)
    }

    @Synchronized
    fun addDeny(domain: String): Boolean = addOne(deny, domain)

    @Synchronized
    fun removeDeny(domain: String): Boolean {
        val normalized = Domains.normalize(domain) ?: return false
        return deny.remove(normalized)
    }

    @Synchronized
    fun snapshot(): UserRules = UserRules(allow.toSet(), deny.toSet())

    fun rules(): List<CompiledRule> {
        val current = snapshot()
        val result = ArrayList<CompiledRule>(current.allow.size + current.deny.size)
        current.allow.forEach {
            result.add(
                CompiledRule(it, matchSubdomains = true, RuleAction.ALLOW, RuleOrigin(USER_ALLOW)),
            )
        }
        current.deny.forEach {
            result.add(
                CompiledRule(it, matchSubdomains = true, RuleAction.BLOCK, RuleOrigin(USER_DENY)),
            )
        }
        return result
    }

    private fun replace(target: MutableSet<String>, domains: Collection<String>): List<String> {
        val rejected = ArrayList<String>()
        target.clear()
        for (domain in domains) {
            val normalized = Domains.normalize(domain)
            if (normalized == null) rejected.add(domain) else target.add(normalized)
        }
        return rejected
    }

    private fun addOne(target: MutableSet<String>, domain: String): Boolean {
        val normalized = Domains.normalize(domain) ?: return false
        return target.add(normalized)
    }

    data class UserRules(val allow: Set<String>, val deny: Set<String>)

    companion object {
        const val USER_ALLOW = "user-allowlist"
        const val USER_DENY = "user-denylist"
    }
}
