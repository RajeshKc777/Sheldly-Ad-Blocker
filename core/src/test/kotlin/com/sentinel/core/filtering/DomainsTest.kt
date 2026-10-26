package com.sentinel.core.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {

    @Test
    fun `lowercases and strips the trailing root dot`() {
        assertEquals("www.example.com", Domains.normalize("WWW.Example.COM."))
        assertEquals("example.com", Domains.normalize("  example.com  "))
    }

    @Test
    fun `converts internationalised names to punycode`() {
        assertEquals("xn--mnchen-3ya.de", Domains.normalize("münchen.de"))
    }

    @Test
    fun `rejects empty and non host input`() {
        assertNull(Domains.normalize(""))
        assertNull(Domains.normalize("   "))
        assertNull(Domains.normalize("."))
        assertNull(Domains.normalize("http://example.com"))
        assertNull(Domains.normalize("example.com/ads"))
        assertNull(Domains.normalize("example com"))
        assertNull(Domains.normalize("exam ple.com"))
        assertNull(Domains.normalize("*.example.com"))
        assertNull(Domains.normalize("a..b.com"))
        assertNull(Domains.normalize(".example.com"))
    }

    @Test
    fun `rejects illegal labels`() {
        assertNull(Domains.normalize("-leading.com"))
        assertNull(Domains.normalize("trailing-.com"))
        assertNull(Domains.normalize("bad_label!.com"))
        assertNull(Domains.normalize("x".repeat(64) + ".com"))
    }

    @Test
    fun `accepts long but legal names`() {
        val label = "a".repeat(63)
        val host = "$label.$label.$label.${"a".repeat(50)}"
        assertTrue(host.length <= Domains.MAX_HOSTNAME_LENGTH)
        assertEquals(host, Domains.normalize(host))
    }

    @Test
    fun `rejects names longer than 253 characters`() {
        val label = "a".repeat(63)
        val tooLong = "$label.$label.$label.$label.$label.$label"
        assertTrue(tooLong.length > Domains.MAX_HOSTNAME_LENGTH)
        assertNull(Domains.normalize(tooLong))
    }

    @Test
    fun `detects ip literals`() {
        assertTrue(Domains.isIpLiteral("192.168.0.1"))
        assertTrue(Domains.isIpLiteral("0.0.0.0"))
        assertTrue(Domains.isIpLiteral("::1"))
        assertTrue(Domains.isIpLiteral("2001:db8::1"))
        assertFalse(Domains.isIpLiteral("256.1.1.1"))
        assertFalse(Domains.isIpLiteral("1.2.3"))
        assertFalse(Domains.isIpLiteral("example.com"))
        assertFalse(Domains.isIpLiteral(""))
    }

    @Test
    fun `suffixes are label aligned`() {
        assertEquals(
            listOf("a.b.example.com", "b.example.com", "example.com", "com"),
            Domains.suffixes("a.b.example.com"),
        )
        assertEquals(listOf("com"), Domains.suffixes("com"))
    }
}
