package com.sentinel.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCacheTest {

    private val key = DnsCache.Key("example.com", DnsMessage.TYPE_A, DnsMessage.CLASS_IN)

    @Test
    fun `returns entries within their ttl`() {
        var now = 0L
        val cache = DnsCache(clock = { now })
        cache.put(key, DnsTestData.response(DnsTestData.query("example.com"), ttl = 60), 60)

        now = 30_000
        val hit = cache.get(key)
        assertNotNull(hit)
        assertEquals(30, hit!!.elapsedSeconds)
    }

    @Test
    fun `expires entries at their ttl`() {
        var now = 0L
        val cache = DnsCache(clock = { now })
        cache.put(key, DnsTestData.response(DnsTestData.query("example.com"), ttl = 60), 60)

        now = 59_999
        assertNotNull(cache.get(key))

        now = 60_000
        assertNull(cache.get(key))
        assertEquals(0, cache.size())
    }

    @Test
    fun `refuses non positive ttls`() {
        val cache = DnsCache()
        cache.put(key, DnsTestData.response(DnsTestData.query("example.com"), ttl = 0), 0)
        assertEquals(0, cache.size())
    }

    @Test
    fun `is bounded`() {
        val cache = DnsCache(maxEntries = 2)
        repeat(3) { index ->
            val message = DnsTestData.response(DnsTestData.query("host$index.example"), ttl = 60)
            cache.put(DnsCache.Key("host$index.example", DnsMessage.TYPE_A, DnsMessage.CLASS_IN), message, 60)
        }
        assertEquals(2, cache.size())
    }

    @Test
    fun `hands out copies so callers cannot corrupt the cache`() {
        val cache = DnsCache()
        val message = DnsTestData.response(DnsTestData.query("example.com"), ttl = 60)
        cache.put(key, message, 60)

        val first = cache.get(key)!!.message
        DnsMessage.patchId(first, 0xAAAA)

        val second = cache.get(key)!!.message
        assertTrue(DnsMessage.u16(second, 0) != 0xAAAA)
    }
}
