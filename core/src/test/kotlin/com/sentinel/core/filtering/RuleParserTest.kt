package com.sentinel.core.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParserTest {

    private val parser = RuleParser()

    private val sample = """
        ! Adblock-style comment
        # hosts-style comment
        [Adblock Plus 2.0]
        0.0.0.0 ads.example.com
        127.0.0.1 a.com b.com
        ||tracker.example^
        @@||allowed.example^
        plain.example
        example.org##.ad
        ||x.com${'$'}third-party
        |https://y.com
        /^ads?\./
        0.0.0.0 localhost
    """.trimIndent()

    @Test
    fun `parses the supported subset and counts the rest`() {
        val parsed = parser.parse("sample", sample)

        assertEquals(6, parsed.stats.accepted)
        assertEquals(4, parsed.stats.unsupported)
        assertEquals(1, parsed.stats.ignoredLines)
        assertEquals(3, parsed.stats.commentLines)
        assertEquals(0, parsed.stats.duplicates)
        assertEquals(0, parsed.stats.invalid)
        assertEquals(6, parsed.rules.size)
    }

    @Test
    fun `hosts lines with several hosts are all parsed`() {
        val parsed = parser.parse("hosts", "127.0.0.1 a.com b.com")
        assertEquals(setOf("a.com", "b.com"), parsed.rules.map { it.pattern }.toSet())
    }

    @Test
    fun `exception rules are allow rules`() {
        val parsed = parser.parse("sample", sample)
        val allow = parsed.rules.first { it.pattern == "allowed.example" }
        assertEquals(RuleAction.ALLOW, allow.action)
        assertTrue(allow.matchSubdomains)

        val block = parsed.rules.first { it.pattern == "tracker.example" }
        assertEquals(RuleAction.BLOCK, block.action)
    }

    @Test
    fun `records the source id and line number`() {
        val parsed = parser.parse("my-list", "! hi\n||ads.example^")
        val rule = parsed.rules.single()
        assertEquals("my-list", rule.origin.sourceId)
        assertEquals(2, rule.origin.line)
    }

    @Test
    fun `reports unsupported cosmetic and option rules with reasons`() {
        val parsed = parser.parse("sample", sample)
        val reasons = parsed.issues.map { it.reason }
        assertTrue(reasons.any { it.contains("cosmetic") })
        assertTrue(reasons.any { it.contains("option") })
        assertTrue(reasons.any { it.contains("URL anchor") })
        assertTrue(reasons.any { it.contains("regular expression") })
    }

    @Test
    fun `duplicate rules are detected`() {
        val parsed = parser.parse("dupes", "example.com\nexample.com\n||example.com^")
        assertEquals(1, parsed.stats.accepted)
        assertEquals(2, parsed.stats.duplicates)
        assertEquals(1, parsed.rules.size)
    }

    @Test
    fun `rejects hosts lines whose first column is not an address`() {
        val parsed = parser.parse("bad", "not-an-ip example.com")
        assertEquals(1, parsed.stats.invalid)
        assertEquals(0, parsed.stats.accepted)
    }

    @Test
    fun `rejects malformed host names`() {
        val parsed = parser.parse("bad", "||exa mple.com^")
        assertEquals(0, parsed.stats.accepted)
        assertTrue(parsed.stats.invalid > 0)
    }

    @Test
    fun `handles an empty document`() {
        val parsed = parser.parse("empty", "")
        assertEquals(0, parsed.stats.accepted)
        assertEquals(0, parsed.rules.size)
    }

    @Test
    fun `normalises case and trailing dots from rules`() {
        val parsed = parser.parse("case", "||ADS.Example.COM.^")
        assertEquals("ads.example.com", parsed.rules.single().pattern)
    }

    @Test
    fun `inline comment after a hosts entry is dropped`() {
        val parsed = parser.parse("inline", "0.0.0.0 ads.example.com # tracker")
        assertEquals("ads.example.com", parsed.rules.single().pattern)
        assertNotNull(parsed.rules.single())
    }

    @Test
    fun `pre-parsing directives are unsupported, not silently ignored`() {
        val parsed = parser.parse("dir", "!#include foo.txt")
        assertTrue(parsed.stats.unsupported >= 1)
        assertEquals(0, parsed.stats.accepted)
    }
}
