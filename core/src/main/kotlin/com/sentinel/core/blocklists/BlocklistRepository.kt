package com.sentinel.core.blocklists

import com.sentinel.core.filtering.AllowlistManager
import com.sentinel.core.filtering.CompiledRule
import com.sentinel.core.filtering.DomainFilter
import com.sentinel.core.filtering.DomainMatcher
import com.sentinel.core.filtering.FilterVerdict
import com.sentinel.core.filtering.ParseIssue
import com.sentinel.core.filtering.ParseStats
import com.sentinel.core.filtering.RuleParser

/**
 * An immutable, fully-built filter. Readers of [BlocklistRepository.current]
 * get a consistent view without locking; writers replace the whole object.
 *
 * A snapshot is itself a [DomainFilter] so the VPN service can hold one
 * reference and answer packets without any further indirection.
 *
 * Precedence, in order:
 *  1. the user's own rules (allow *and* deny), because explicit intent beats any list;
 *  2. downloaded/bundled lists, where `@@` exceptions beat block rules;
 *  3. no match -> query is forwarded.
 */
data class FilterSnapshot(
    private val lists: DomainMatcher,
    private val user: DomainMatcher,
    val stats: ParseStats,
    val builtAtMillis: Long,
    val sourceIds: List<String>,
) : DomainFilter {

    override val blockRuleCount: Int
        get() = lists.blockRuleCount + user.blockRuleCount

    val allowRuleCount: Int
        get() = lists.allowRuleCount + user.allowRuleCount

    override fun shouldBlock(domain: String): Boolean = evaluate(domain).blocked

    override fun evaluate(domain: String): FilterVerdict {
        val userVerdict = user.evaluate(domain)
        if (userVerdict.reason != FilterVerdict.Reason.NO_MATCH &&
            userVerdict.reason != FilterVerdict.Reason.INVALID_DOMAIN
        ) {
            return userVerdict
        }
        return lists.evaluate(domain)
    }

    val isActive: Boolean get() = blockRuleCount > 0

    companion object {
        fun empty(): FilterSnapshot = FilterSnapshot(
            lists = DomainMatcher.Builder().build(),
            user = DomainMatcher.Builder().build(),
            stats = ParseStats(),
            builtAtMillis = 0L,
            sourceIds = emptyList(),
        )
    }
}

/**
 * Single-writer, many-reader holder of the active filter.
 *
 * The `@Volatile` reference swap is the atomicity guarantee: a packet is either
 * matched against the old complete snapshot or the new complete snapshot, never
 * against a half-loaded one. A failed rebuild never touches the reference, so
 * the last known-good rules survive.
 */
class BlocklistRepository {

    @Volatile
    private var snapshot: FilterSnapshot = FilterSnapshot.empty()

    val current: FilterSnapshot get() = snapshot

    fun replace(newSnapshot: FilterSnapshot): FilterSnapshot {
        snapshot = newSnapshot
        return newSnapshot
    }
}

/** A named piece of rule text: a bundled file or a downloaded list. */
data class RuleSource(
    val id: String,
    val displayName: String,
    val text: String,
)

/**
 * Turns rule text into a snapshot and installs it.
 *
 * Rebuilds are synchronous and cheap for realistic list sizes (parsing 100k
 * lines takes tens of milliseconds); callers invoke it off the main thread and
 * only the final reference swap is shared with the hot path.
 */
class FilterEngine(
    private val repository: BlocklistRepository,
    private val allowlist: AllowlistManager,
    private val parser: RuleParser = RuleParser(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var lastSources: List<RuleSource> = emptyList()

    val current: FilterSnapshot get() = repository.current

    /** Rebuilds from [sources] plus the current user rules, atomically. */
    @Synchronized
    fun rebuild(sources: List<RuleSource>): BuildOutcome {
        lastSources = sources
        return buildAndSwap(sources)
    }

    /** Rebuilds using the previously supplied sources (e.g. after an allowlist edit). */
    @Synchronized
    fun refreshUserRules(): BuildOutcome = buildAndSwap(lastSources)

    private fun buildAndSwap(sources: List<RuleSource>): BuildOutcome {
        val listBuilder = DomainMatcher.Builder()
        val issues = mutableListOf<ParseIssue>()
        var stats = ParseStats()
        val sourceIds = ArrayList<String>(sources.size)

        for (source in sources) {
            val parsed = parser.parse(source.id, source.text)
            sourceIds.add(source.id)
            listBuilder.addAll(parsed.rules)
            issues.addAll(parsed.issues)
            stats = ParseStats(
                totalLines = stats.totalLines + parsed.stats.totalLines,
                blankLines = stats.blankLines + parsed.stats.blankLines,
                commentLines = stats.commentLines + parsed.stats.commentLines,
                ignoredLines = stats.ignoredLines + parsed.stats.ignoredLines,
                accepted = stats.accepted + parsed.stats.accepted,
                duplicates = stats.duplicates + parsed.stats.duplicates,
                unsupported = stats.unsupported + parsed.stats.unsupported,
                invalid = stats.invalid + parsed.stats.invalid,
            )
        }

        val userBuilder = DomainMatcher.Builder()
        userBuilder.addAll(allowlist.rules())

        val duplicates = listBuilder.duplicateCount + userBuilder.duplicateCount
        val snapshot = FilterSnapshot(
            lists = listBuilder.build(),
            user = userBuilder.build(),
            stats = stats.copy(duplicates = stats.duplicates + duplicates),
            builtAtMillis = clock(),
            sourceIds = sourceIds,
        )
        repository.replace(snapshot)
        return BuildOutcome(snapshot, issues)
    }

    data class BuildOutcome(
        val snapshot: FilterSnapshot,
        val issues: List<ParseIssue>,
    )
}
