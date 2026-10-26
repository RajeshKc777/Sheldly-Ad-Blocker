package com.sentinel.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsRecordWalkerTest {

    @Test
    fun `reads the minimum answer ttl`() {
        val response = DnsTestData.response(DnsTestData.query("example.com"), ttl = 300)
        assertEquals(300, DnsRecordWalker.minAnswerTtl(response))
    }

    @Test
    fun `returns null when there are no answers`() {
        val query = DnsTestData.query("example.com")
        assertNull(DnsRecordWalker.minAnswerTtl(query))
    }

    @Test
    fun `rewrites ttls to the remaining lifetime`() {
        val response = DnsTestData.response(DnsTestData.query("example.com"), ttl = 300)
        assertTrue(DnsRecordWalker.rewriteAnswerTtls(response, 100))
        assertEquals(200, DnsRecordWalker.minAnswerTtl(response))
    }

    @Test
    fun `never rewrites a ttl below zero`() {
        val response = DnsTestData.response(DnsTestData.query("example.com"), ttl = 10)
        DnsRecordWalker.rewriteAnswerTtls(response, 999)
        assertEquals(0, DnsRecordWalker.minAnswerTtl(response))
    }

    @Test
    fun `rejects truncated messages`() {
        val response = DnsTestData.response(DnsTestData.query("example.com"), ttl = 300)
        val truncated = response.copyOf(response.size - 4)
        assertFalse(DnsRecordWalker.forEachAnswer(truncated) { _, _, _ -> })
        assertNull(DnsRecordWalker.minAnswerTtl(truncated))
    }

    @Test
    fun `clamps absurd ttls`() {
        val response = DnsTestData.response(DnsTestData.query("example.com"), ttl = 2_000_000_000)
        assertEquals(DnsRecordWalker.MAX_CACHE_TTL_SECONDS, DnsRecordWalker.minAnswerTtl(response))
    }
}
