package com.sentinel.core.blocklists

import com.sentinel.core.filtering.AllowlistManager
import com.sentinel.core.filtering.DomainMatcher
import com.sentinel.core.filtering.FilterVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private val repository = BlocklistRepository()
    private val allowlist = AllowlistManager()
    private val engine = FilterEngine(repository, allowlist, clock = { 42L })

    private val list = RuleSource(
        id = "test-list",
        displayName = "Test list",
        text = """
            ||example.com^
            @@||safe.example.com^
        """.trimIndent(),
    )

    @Test
    fun `builds an active snapshot`() {
        val outcome = engine.rebuild(listOf(list))

        assertTrue(outcome.snapshot.isActive)
        assertEquals(1, outcome.snapshot.blockRuleCount)
        assertEquals(1, outcome.snapshot.allowRuleCount)
        assertEquals(42L, outcome.snapshot.builtAtMillis)
        assertEquals(listOf("test-list"), outcome.snapshot.sourceIds)
        assertEquals(2, outcome.snapshot.stats.accepted)
    }

    @Test
    fun `list rules filter as expected`() {
        engine.rebuild(listOf(list))
        assertTrue(engine.current.shouldBlock("ads.example.com"))
        assertFalse(engine.current.shouldBlock("safe.example.com"))
        assertFalse(engine.current.shouldBlock("unrelated.example"))
    }

    @Test
    fun `user allowlist outranks downloaded block rules`() {
        engine.rebuild(listOf(list))
        assertTrue(engine.current.shouldBlock("ads.example.com"))

        allowlist.addAllow("ads.example.com")
        engine.refreshUserRules()

        val verdict = engine.current.evaluate("ads.example.com")
        assertFalse(verdict.blocked)
        assertEquals(FilterVerdict.Reason.ALLOW_RULE, verdict.reason)
        assertEquals(AllowlistManager.USER_ALLOW, verdict.matchedRule?.origin?.sourceId)
    }

    @Test
    fun `user denylist outranks downloaded exceptions`() {
        engine.rebuild(listOf(list))
        assertFalse(engine.current.shouldBlock("safe.example.com"))

        allowlist.addDeny("safe.example.com")
        engine.refreshUserRules()

        assertTrue(engine.current.shouldBlock("safe.example.com"))
    }

    @Test
    fun `invalid user entries are reported and not added`() {
        val rejected = allowlist.replaceAllow(listOf("good.example", "http://bad.example/path", ""))
        assertEquals(listOf("http://bad.example/path", ""), rejected)
        assertEquals(setOf("good.example"), allowlist.snapshot().allow)
    }

    @Test
    fun `a rejected update leaves the working filter untouched`() {
        engine.rebuild(listOf(list))
        val before = engine.current

        val updater = BlocklistUpdater(fetcher = { FetchResult.Success("   ", 3) })
        val result = updater.fetchAndValidate("new", "New", "https://lists.example/list.txt")

        assertTrue(result is UpdateResult.Rejected)
        assertSame(before, engine.current)
        assertTrue(engine.current.shouldBlock("ads.example.com"))
    }

    @Test
    fun `empty engine reports itself inactive`() {
        assertFalse(repository.current.isActive)
        assertEquals(0, repository.current.blockRuleCount)
        assertFalse(repository.current.shouldBlock("example.com"))
        assertEquals(
            FilterVerdict.Reason.NO_MATCH,
            DomainMatcher.Builder().build().evaluate("example.com").reason,
        )
    }
}
