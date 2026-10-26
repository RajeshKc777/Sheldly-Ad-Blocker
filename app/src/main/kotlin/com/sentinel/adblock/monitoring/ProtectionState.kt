package com.sentinel.adblock.monitoring

import com.sentinel.core.dns.DnsPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The single source of truth the UI observes.
 *
 * Honesty rules encoded here:
 *  - [Phase.RUNNING] is only set after `establish()` returned a descriptor *and*
 *    the read loop started. Never before.
 *  - counters say "DNS queries", not "ads removed".
 *  - recent domains are kept in memory only (bounded ring); nothing is written
 *    to disk, so a crash or force-stop leaves no browsing trail.
 */
object ProtectionState {

    enum class Phase { STOPPED, STARTING, RUNNING, ERROR }

    data class Status(
        val phase: Phase,
        val message: String? = null,
        val ruleCount: Int = 0,
        val sinceMillis: Long = 0L,
    ) {
        val isActive: Boolean get() = phase == Phase.RUNNING
    }

    data class Counters(
        val total: Long = 0,
        val blocked: Long = 0,
        val allowlistedQueries: Long = 0,
        val cacheHits: Long = 0,
        val upstreamFailures: Long = 0,
        val ipv6Observed: Long = 0,
    ) {
        val allowed: Long get() = total - blocked
    }

    data class BlockedEvent(
        val domain: String,
        val rule: String,
        val source: String,
        val atMillis: Long,
    )

    private const val MAX_RECENT_EVENTS = 50

    private val _status = MutableStateFlow(Status(Phase.STOPPED))
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _counters = MutableStateFlow(Counters())
    val counters: StateFlow<Counters> = _counters.asStateFlow()

    private val _recent = MutableStateFlow<List<BlockedEvent>>(emptyList())
    val recent: StateFlow<List<BlockedEvent>> = _recent.asStateFlow()

    fun setStarting() {
        _counters.value = Counters()
        _recent.value = emptyList()
        _status.value = Status(Phase.STARTING)
    }

    fun setRunning(ruleCount: Int) {
        _status.value = Status(
            phase = Phase.RUNNING,
            ruleCount = ruleCount,
            sinceMillis = System.currentTimeMillis(),
        )
    }

    fun setStopped() {
        _status.value = Status(Phase.STOPPED)
    }

    fun setError(message: String) {
        _status.value = Status(Phase.ERROR, message)
    }

    fun record(outcome: DnsPipeline.Outcome) {
        _counters.update { current ->
            current.copy(
                total = current.total + 1,
                blocked = current.blocked + if (outcome.blocked) 1 else 0,
                allowlistedQueries = current.allowlistedQueries + if (outcome.allowlisted) 1 else 0,
                cacheHits = current.cacheHits + if (outcome.fromCache) 1 else 0,
                upstreamFailures = current.upstreamFailures + if (outcome.upstreamFailed) 1 else 0,
            )
        }

        if (outcome.blocked) {
            val event = BlockedEvent(
                domain = outcome.question,
                rule = outcome.matchedRule?.toString() ?: "unknown",
                source = outcome.matchedRule?.origin?.sourceId ?: "unknown",
                atMillis = System.currentTimeMillis(),
            )
            _recent.update { existing -> (listOf(event) + existing).take(MAX_RECENT_EVENTS) }
        }
    }

    /** Counts IPv6 packets seen on the tunnel. They are not routed by this build. */
    fun recordIpv6() {
        _counters.update { it.copy(ipv6Observed = it.ipv6Observed + 1) }
    }

    /** Test-only hook so state does not leak between test cases. */
    internal fun resetForTest() {
        _status.value = Status(Phase.STOPPED)
        _counters.value = Counters()
        _recent.value = emptyList()
    }
}
