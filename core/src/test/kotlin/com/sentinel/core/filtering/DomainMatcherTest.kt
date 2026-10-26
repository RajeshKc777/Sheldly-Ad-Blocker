package com.sentinel.core.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    private fun rule(
        pattern: String,
        action: RuleAction = RuleAction.BLOCK,
        subdomains: Boolean = true,
        source: String = "test",
    ) = CompiledRule(pattern, subdomains, action, RuleOrigin(source))

    private fun matcher(vararg rules: CompiledRule): DomainMatcher =
        DomainMatcher.Builder().apply { addAll(rules.toList()) }.build()

    @Test
    fun `suffix rule blocks the domain and its subdomains`() {
        val filter = matcher(rule("doubleclick.net"))
        assertTrue(filter.shouldBlock("doubleclick.net"))
        assertTrue(filter.shouldBlock("ads.doubleclick.net"))
        assertTrue(filter.shouldBlock("a.b.doubleclick.net"))
        assertTrue(filter.shouldBlock("ADS.DoubleClick.NET."))
    }

    @Test
    fun `suffix rule does not match unrelated lookalikes`() {
        val filter = matcher(rule("example.com"))
        assertFalse(filter.shouldBlock("notexample.com"))
        assertFalse(filter.shouldBlock("example.com.evil.net"))
        assertFalse(filter.shouldBlock("example.co"))
        assertFalse(filter.shouldBlock("comexample.com"))
    }

    @Test
    fun `exact rule does not match subdomains`() {
        val filter = matcher(rule("example.com", subdomains = false))
        assertTrue(filter.shouldBlock("example.com"))
        assertFalse(filter.shouldBlock("www.example.com"))
    }

    @Test
    fun `allow rule beats block rule`() {
        val filter = matcher(
            rule("example.com"),
            rule("safe.example.com", action = RuleAction.ALLOW),
        )
        assertTrue(filter.shouldBlock("ads.example.com"))
        assertFalse(filter.shouldBlock("safe.example.com"))
        assertFalse(filter.shouldBlock("deep.safe.example.com"))
    }

    @Test
    fun `allow rule wins even when the block rule is more specific`() {
        val filter = matcher(
            rule("example.com", action = RuleAction.ALLOW),
            rule("ads.example.com"),
        )
        assertFalse(filter.shouldBlock("ads.example.com"))
    }

    @Test
    fun `most specific block rule is reported`() {
        val filter = matcher(
            rule("com"),
            rule("example.com"),
            rule("ads.example.com"),
        )
        val verdict = filter.evaluate("x.ads.example.com")
        assertTrue(verdict.blocked)
        assertEquals("ads.example.com", verdict.matchedRule?.pattern)
    }

    @Test
    fun `invalid input is not blocked and says why`() {
        val filter = matcher(rule("example.com"))
        val verdict = filter.evaluate("http://example.com/path")
        assertFalse(verdict.blocked)
        assertEquals(FilterVerdict.Reason.INVALID_DOMAIN, verdict.reason)
        assertNull(verdict.matchedRule)
    }

    @Test
    fun `explains allow matches`() {
        val filter = matcher(rule("example.com", action = RuleAction.ALLOW, source = "user-allowlist"))
        val verdict = filter.evaluate("example.com")
        assertEquals(FilterVerdict.Reason.ALLOW_RULE, verdict.reason)
        assertTrue(verdict.isAllowlisted)
        assertNotNull(verdict.matchedRule)
        assertEquals("user-allowlist", verdict.matchedRule?.origin?.sourceId)
    }

    @Test
    fun `duplicate rules are counted and ignored`() {
        val builder = DomainMatcher.Builder()
        assertTrue(builder.add(rule("example.com")))
        assertFalse(builder.add(rule("example.com")))
        assertEquals(1, builder.duplicateCount)
        assertEquals(1, builder.build().blockRuleCount)
    }

    @Test
    fun `empty matcher blocks nothing`() {
        val filter = matcher()
        assertFalse(filter.shouldBlock("example.com"))
        assertEquals(0, filter.blockRuleCount)
        assertTrue(filter.isEmpty())
    }
}
