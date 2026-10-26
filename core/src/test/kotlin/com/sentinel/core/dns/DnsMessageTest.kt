package com.sentinel.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    @Test
    fun `encodes and parses a query`() {
        val packet = DnsMessage.encodeQuery(0x1234, "Ads.Example.COM")!!
        val parsed = DnsMessage.parseQuery(packet)!!

        assertEquals(0x1234, parsed.id)
        assertEquals("ads.example.com", parsed.question.name)
        assertEquals(DnsMessage.TYPE_A, parsed.question.type)
        assertEquals(DnsMessage.CLASS_IN, parsed.question.qClass)
        assertEquals(packet.size, parsed.questionEndOffset)
    }

    @Test
    fun `rejects short and malformed packets`() {
        assertNull(DnsMessage.parseQuery(ByteArray(0)))
        assertNull(DnsMessage.parseQuery(ByteArray(11)))
        assertNull(DnsMessage.parseQuery(ByteArray(12)))
    }

    @Test
    fun `rejects responses and multi question packets`() {
        val query = DnsMessage.encodeQuery(1, "example.com")!!.copyOf()
        DnsMessage.putU16(query, 2, 0x8000) // set QR
        assertNull(DnsMessage.parseQuery(query))

        val multi = DnsMessage.encodeQuery(1, "example.com")!!.copyOf()
        DnsMessage.putU16(multi, 4, 2)
        assertNull(DnsMessage.parseQuery(multi))
    }

    @Test
    fun `rejects a truncated name`() {
        val packet = ByteArray(14)
        packet[12] = 10 // claims a 10-byte label but only one byte follows
        assertNull(DnsMessage.parseQuery(packet))
    }

    @Test
    fun `compression pointer loops are rejected`() {
        val packet = ByteArray(14)
        packet[12] = 0xC0.toByte()
        packet[13] = 0x0C // points at itself
        assertNull(DnsMessage.readName(packet, 12))
    }

    @Test
    fun `compressed names resolve against earlier offsets`() {
        val packet = ByteArray(27)
        // "example.com" at offset 12
        val name = DnsMessage.encodeQuery(0, "example.com")!!
        name.copyInto(packet, 12, DnsMessage.HEADER_SIZE, DnsMessage.HEADER_SIZE + 13)
        packet[25] = 0xC0.toByte()
        packet[26] = 0x0C

        val decoded = DnsMessage.readName(packet, 25)
        assertNotNull(decoded)
        assertEquals("example.com", decoded!!.first)
        assertEquals(27, decoded.second)
    }

    @Test
    fun `error response echoes the question and sets the rcode`() {
        val query = DnsMessage.encodeQuery(0x4321, "ads.example.com")!!
        val reply = DnsMessage.buildErrorResponse(query, DnsMessage.RCODE_NXDOMAIN)!!

        assertEquals(query.size, reply.size)
        assertEquals(0x4321, DnsMessage.u16(reply, 0))
        assertEquals(DnsMessage.RCODE_NXDOMAIN, DnsTestData.rcode(reply))
        assertTrue(DnsTestData.isResponse(reply))
        assertEquals(1, DnsMessage.u16(reply, 4))
        assertEquals(0, DnsMessage.u16(reply, 6))
        assertEquals(0, DnsMessage.u16(reply, 8))
        assertEquals(0, DnsMessage.u16(reply, 10))

        // RD is preserved, RA is set by the responder.
        val flags = DnsMessage.u16(reply, 2)
        assertTrue(flags and 0x0100 != 0)
        assertTrue(flags and 0x0080 != 0)
        assertEquals("ads.example.com", DnsMessage.parseQuestion(reply)!!.name)
    }

    @Test
    fun `servfail response uses rcode 2`() {
        val reply = DnsMessage.buildErrorResponse(DnsMessage.encodeQuery(1, "example.com")!!, DnsMessage.RCODE_SERVFAIL)!!
        assertEquals(2, DnsTestData.rcode(reply))
        assertFalse(DnsTestData.answerCount(reply) > 0)
    }

    @Test
    fun `rejects names that cannot be encoded`() {
        assertNull(DnsMessage.encodeQuery(1, ""))
        assertNull(DnsMessage.encodeQuery(1, "http://example.com"))
    }

    @Test
    fun `patches the transaction id`() {
        val packet = DnsMessage.encodeQuery(1, "example.com")!!
        DnsMessage.patchId(packet, 0xBEEF)
        assertEquals(0xBEEF, DnsMessage.u16(packet, 0))
    }
}
