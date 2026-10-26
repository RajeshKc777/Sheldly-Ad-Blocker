package com.sentinel.core.dns

import com.sentinel.core.filtering.CompiledRule
import com.sentinel.core.filtering.DomainMatcher
import com.sentinel.core.filtering.RuleAction
import com.sentinel.core.filtering.RuleOrigin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPipelineTest {

    private class FakeResolver(private val result: ByteArray?) : UpstreamResolver {
        var calls = 0
        var lastPayload: ByteArray? = null

        override fun query(payload: ByteArray): ByteArray? {
            calls++
            lastPayload = payload
            return result?.copyOf()
        }

        override fun close() = Unit
    }

    private val domainFilter = DomainMatcher.Builder().apply {
        add(CompiledRule("ads.example", true, RuleAction.BLOCK, RuleOrigin("test")))
        add(CompiledRule("safe.example", true, RuleAction.ALLOW, RuleOrigin("test")))
    }.build()

    @Test
    fun `blocked queries get NXDOMAIN and never reach upstream`() {
        val resolver = FakeResolver(null)
        val pipeline = DnsPipeline({ domainFilter }, resolver)

        val outcome = pipeline.handle(DnsTestData.query("ads.example"))!!

        assertTrue(outcome.blocked)
        assertFalse(outcome.fromCache)
        assertEquals(DnsMessage.RCODE_NXDOMAIN, DnsTestData.rcode(outcome.response))
        assertEquals("ads.example", outcome.question)
        assertEquals("ads.example", outcome.matchedRule?.pattern)
        assertEquals(0, resolver.calls)
    }

    @Test
    fun `subdomains of a blocked domain are blocked`() {
        val pipeline = DnsPipeline({ domainFilter }, FakeResolver(null))
        assertTrue(pipeline.handle(DnsTestData.query("cdn.ads.example"))!!.blocked)
    }

    @Test
    fun `ordinary queries are forwarded unchanged`() {
        val query = DnsTestData.query("good.example", id = 0x11)
        val upstream = DnsTestData.response(query, ttl = 120)
        val resolver = FakeResolver(upstream)
        val pipeline = DnsPipeline({ domainFilter }, resolver)

        val outcome = pipeline.handle(query)!!

        assertFalse(outcome.blocked)
        assertEquals(1, resolver.calls)
        assertArrayEquals(query, resolver.lastPayload)
        assertEquals(1, DnsTestData.answerCount(outcome.response))
        assertEquals(0x11, DnsMessage.u16(outcome.response, 0))
    }

    @Test
    fun `allowlisted queries are forwarded and flagged`() {
        val query = DnsTestData.query("safe.example")
        val pipeline = DnsPipeline({ domainFilter }, FakeResolver(DnsTestData.response(query, ttl = 60)))

        val outcome = pipeline.handle(query)!!
        assertFalse(outcome.blocked)
        assertTrue(outcome.allowlisted)
        assertEquals("safe.example", outcome.matchedRule?.pattern)
    }

    @Test
    fun `upstream failure becomes SERVFAIL, never a block`() {
        val pipeline = DnsPipeline({ domainFilter }, FakeResolver(null))

        val outcome = pipeline.handle(DnsTestData.query("good.example"))!!

        assertFalse(outcome.blocked)
        assertTrue(outcome.upstreamFailed)
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsTestData.rcode(outcome.response))
    }

    @Test
    fun `malformed packets are dropped`() {
        val pipeline = DnsPipeline({ domainFilter }, FakeResolver(null))
        assertNull(pipeline.handle(ByteArray(0)))
        assertNull(pipeline.handle(ByteArray(8)))
    }

    @Test
    fun `repeated queries are served from cache without touching upstream`() {
        val first = DnsTestData.query("cached.example", id = 1)
        val resolver = FakeResolver(DnsTestData.response(first, ttl = 300))
        val cache = DnsCache()
        val pipeline = DnsPipeline({ domainFilter }, resolver, cache)

        val firstOut = pipeline.handle(first)!!
        assertFalse(firstOut.fromCache)

        val second = DnsTestData.query("cached.example", id = 2)
        val secondOut = pipeline.handle(second)!!

        assertTrue(secondOut.fromCache)
        assertEquals(1, resolver.calls)
        assertEquals(2, DnsMessage.u16(secondOut.response, 0))
    }

    @Test
    fun `live filter changes are picked up without rebuilding the pipeline`() {
        var current = DomainMatcher.Builder().build()
        val pipeline = DnsPipeline({ current }, FakeResolver(null))
        val query = DnsTestData.query("ads.example")

        assertFalse(pipeline.handle(query)!!.blocked)

        current = domainFilter
        assertTrue(pipeline.handle(query)!!.blocked)
    }

    @Test
    fun `AAAA queries are cached separately from A queries`() {
        val resolver = FakeResolver(null)
        val pipeline = DnsPipeline({ domainFilter }, resolver)

        val a = DnsTestData.query("cached.example", id = 1, type = DnsMessage.TYPE_A)
        val aaaa = DnsTestData.query("cached.example", id = 2, type = DnsMessage.TYPE_AAAA)
        pipeline.handle(a)
        pipeline.handle(aaaa)

        assertEquals(2, resolver.calls)
    }

    @Test
    fun `blocked responses keep a valid question section`() {
        val query = DnsTestData.query("ads.example", id = 0x7777)
        val outcome = DnsPipeline({ domainFilter }, FakeResolver(null)).handle(query)!!
        assertNotNull(DnsMessage.parseQuestion(outcome.response))
        assertEquals("ads.example", DnsMessage.parseQuestion(outcome.response)!!.name)
    }
}
