package com.sentinel.core.net

import com.sentinel.core.dns.DnsTestData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4UdpTest {

    private val sourceIp = byteArrayOf(10, 111, 222.toByte(), 1)
    private val destinationIp = byteArrayOf(10, 111, 222.toByte(), 2)

    @Test
    fun `builds and parses a datagram`() {
        val payload = DnsTestData.query("example.com")
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, payload)

        val parsed = Ipv4Udp.parse(packet)
        assertTrue(parsed != null)
        assertArrayEquals(sourceIp, parsed!!.sourceIp)
        assertArrayEquals(destinationIp, parsed.destinationIp)
        assertEquals(53, parsed.sourcePort)
        assertEquals(40_000, parsed.destinationPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `ip header checksum is valid`() {
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, DnsTestData.query("example.com"))
        var sum = 0L
        var i = 0
        while (i < 20) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFFL, sum)
    }

    @Test
    fun `udp checksum is valid, including odd length payloads`() {
        val payload = DnsTestData.query("example.com") // 29 bytes -> odd UDP length
        assertEquals(1, payload.size % 2)
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, payload)
        assertTrue(udpChecksumValid(packet, payload.size))
    }

    @Test
    fun `udp checksum is valid for even length payloads`() {
        val payload = ByteArray(16) { 1 }
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, payload)
        assertTrue(udpChecksumValid(packet, payload.size))
    }

    @Test
    fun `rejects ipv6`() {
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, DnsTestData.query("example.com"))
        packet[0] = 0x60
        assertNull(Ipv4Udp.parse(packet))
        assertTrue(Ipv4Udp.isIpv6(packet))
    }

    @Test
    fun `rejects non udp protocols`() {
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, DnsTestData.query("example.com"))
        packet[9] = 6 // TCP
        assertNull(Ipv4Udp.parse(packet))
    }

    @Test
    fun `rejects fragments`() {
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, DnsTestData.query("example.com"))
        packet[6] = 0x20 // MF set
        assertNull(Ipv4Udp.parse(packet))
    }

    @Test
    fun `rejects truncated buffers`() {
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, DnsTestData.query("example.com"))
        assertNull(Ipv4Udp.parse(packet, packet.size - 1))
        assertNull(Ipv4Udp.parse(packet.copyOf(10)))
        assertFalse(Ipv4Udp.isIpv6(packet))
    }

    @Test
    fun `rejects impossible header lengths`() {
        val packet = Ipv4Udp.build(sourceIp, 53, destinationIp, 40_000, DnsTestData.query("example.com"))
        packet[0] = 0x44 // IHL = 4 words = 16 bytes, below the 20-byte minimum
        assertNull(Ipv4Udp.parse(packet))
    }

    private fun udpChecksumValid(packet: ByteArray, payloadSize: Int): Boolean {
        val udpLength = 8 + payloadSize
        var sum = 0L
        for (i in 0 until 4 step 2) {
            sum += ((packet[12 + i].toInt() and 0xFF) shl 8) or (packet[13 + i].toInt() and 0xFF)
        }
        for (i in 0 until 4 step 2) {
            sum += ((packet[16 + i].toInt() and 0xFF) shl 8) or (packet[17 + i].toInt() and 0xFF)
        }
        sum += 17
        sum += udpLength
        var i = 20
        val end = 20 + udpLength
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF) == 0L
    }
}
