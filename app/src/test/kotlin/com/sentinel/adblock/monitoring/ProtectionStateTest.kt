package com.sentinel.adblock.monitoring

import com.sentinel.core.dns.DnsPipeline
import com.sentinel.core.filtering.CompiledRule
import com.sentinel.core.filtering.RuleAction
import com.sentinel.core.filtering.RuleOrigin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The observation object is the only place the UI learns what the tunnel is
 * doing, so its honesty rules are tested directly:
 *  - counters describe DNS queries, not "ads";
 *  - the recent list is bounded and newest-first;
 *  - a restart wipes the session view instead of carrying stale numbers.
 */
class ProtectionStateTest {

    private val rule = CompiledRule(
        pattern = "ads.example.com",
        matchSubdomains = true,
        action = RuleAction.BLOCK,
        origin = RuleOrigin("builtin-test-list", line = 12),
    )

    @Before
    fun setUp() = ProtectionState.resetForTest()

    @After
    fun tearDown() = ProtectionState.resetForTest()

    private fun outcome(
        question: String = "ads.example.com",
        blocked: Boolean = true,
        allowlisted: Boolean = false,
        fromCache: Boolean = false,
        upstreamFailed: Boolean = false,
    ) = DnsPipeline.Outcome(
        response = ByteArray(0),
        blocked = blocked,
        allowlisted = allowlisted,
        question = question,
        matchedRule = if (blocked) rule else null,
        fromCache = fromCache,
        upstreamFailed = upstreamFailed,
    )

    @Test
    fun `starts stopped with empty counters`() {
        val status = ProtectionState.status.value
        assertEquals(ProtectionState.Phase.STOPPED, status.phase)
        assertFalse(status.isActive)
        assertEquals(0L, ProtectionState.counters.value.total)
        assertTrue(ProtectionState.recent.value.isEmpty())
    }

    @Test
    fun `starting clears a previous session`() {
        ProtectionState.record(outcome())
        ProtectionState.setRunning(10)
        ProtectionState.setStarting()

        assertEquals(ProtectionState.Phase.STARTING, ProtectionState.status.value.phase)
        assertEquals(0L, ProtectionState.counters.value.total)
        assertTrue(ProtectionState.recent.value.isEmpty())
    }

    @Test
    fun `running requires a rule count and records the start time`() {
        ProtectionState.setRunning(ruleCount = 1234)
        val status = ProtectionState.status.value
        assertEquals(ProtectionState.Phase.RUNNING, status.phase)
        assertTrue(status.isActive)
        assertEquals(1234, status.ruleCount)
        assertTrue(status.sinceMillis > 0L)
    }

    @Test
    fun `blocked queries count as blocked and appear in the recent list`() {
        ProtectionState.record(outcome(question = "ads.example.com"))

        val counters = ProtectionState.counters.value
        assertEquals(1L, counters.total)
        assertEquals(1L, counters.blocked)
        assertEquals(0L, counters.allowed)

        val event = ProtectionState.recent.value.single()
        assertEquals("ads.example.com", event.domain)
        assertEquals("||ads.example.com^", event.rule)
        assertEquals("builtin-test-list", event.source)
    }

    @Test
    fun `allowed queries are the remainder, not a separate increment`() {
        ProtectionState.record(outcome(blocked = false, allowlisted = true))
        ProtectionState.record(outcome(blocked = false))
        ProtectionState.record(outcome(blocked = true))

        val counters = ProtectionState.counters.value
        assertEquals(3L, counters.total)
        assertEquals(1L, counters.blocked)
        assertEquals(2L, counters.allowed)
        assertEquals(1L, counters.allowlistedQueries)
    }

    @Test
    fun `cache hits and upstream failures are tracked independently of blocking`() {
        ProtectionState.record(outcome(blocked = false, fromCache = true))
        ProtectionState.record(outcome(blocked = false, upstreamFailed = true))

        val counters = ProtectionState.counters.value
        assertEquals(1L, counters.cacheHits)
        assertEquals(1L, counters.upstreamFailures)
        assertEquals(0L, counters.blocked)
    }

    @Test
    fun `recent list is newest first and bounded`() {
        repeat(60) { index ->
            ProtectionState.record(outcome(question = "host$index.example.com"))
        }

        val recent = ProtectionState.recent.value
        assertEquals(50, recent.size)
        assertEquals("host59.example.com", recent.first().domain)
        assertEquals("host10.example.com", recent.last().domain)
    }

    @Test
    fun `ipv6 observations do not touch query counters`() {
        repeat(3) { ProtectionState.recordIpv6() }

        val counters = ProtectionState.counters.value
        assertEquals(3L, counters.ipv6Observed)
        assertEquals(0L, counters.total)
    }

    @Test
    fun `error status carries an explanation and is not active`() {
        ProtectionState.setError("VPN permission was denied or revoked.")

        val status = ProtectionState.status.value
        assertEquals(ProtectionState.Phase.ERROR, status.phase)
        assertEquals("VPN permission was denied or revoked.", status.message)
        assertFalse(status.isActive)
        assertEquals(0, status.ruleCount)
    }

    @Test
    fun `stopped clears status but keeps nothing of the session`() {
        ProtectionState.setRunning(5)
        ProtectionState.setStopped()

        val status = ProtectionState.status.value
        assertEquals(ProtectionState.Phase.STOPPED, status.phase)
        assertEquals(0, status.ruleCount)
        assertNull(status.message)
    }
}
