package com.sentinel.core.net

/**
 * Parses and builds IPv4 + UDP packets for the TUN device.
 *
 * Only the minimum needed for DNS forwarding is implemented: no reassembly, no
 * IPv6, no TCP. Anything that is not a single unfragmented IPv4/UDP datagram is
 * rejected rather than guessed at.
 */
object Ipv4Udp {

    const val IPV4_HEADER_SIZE = 20
    const val UDP_HEADER_SIZE = 8
    const val PROTOCOL_UDP = 17

    private const val MAX_PACKET_SIZE = 65_535
    private const val DEFAULT_TTL = 64
    private const val FLAG_DONT_FRAGMENT = 0x4000

    data class Datagram(
        val sourceIp: ByteArray,
        val destinationIp: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
    )

    /** @return the datagram, or `null` when [buffer] is not a simple IPv4/UDP packet. */
    fun parse(buffer: ByteArray, length: Int = buffer.size): Datagram? {
        if (length < IPV4_HEADER_SIZE + UDP_HEADER_SIZE) return null
        if (length > buffer.size) return null
        if ((buffer[0].toInt() and 0xF0) != 0x40) return null

        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_SIZE) return null
        if (headerLength + UDP_HEADER_SIZE > length) return null

        val totalLength = u16(buffer, 2)
        if (totalLength < headerLength + UDP_HEADER_SIZE || totalLength > length) return null
        if (buffer[9].toInt() and 0xFF != PROTOCOL_UDP) return null

        // Reject fragments: offset != 0 or MF set. DNS queries are small, so a
        // fragment here means something unusual and we simply do not handle it.
        if (u16(buffer, 6) and 0x3FFF != 0) return null

        val udpOffset = headerLength
        val sourcePort = u16(buffer, udpOffset)
        val destinationPort = u16(buffer, udpOffset + 2)
        val udpLength = u16(buffer, udpOffset + 4)
        if (udpLength < UDP_HEADER_SIZE) return null
        if (udpOffset + udpLength > totalLength) return null

        return Datagram(
            sourceIp = buffer.copyOfRange(12, 16),
            destinationIp = buffer.copyOfRange(16, 20),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = buffer.copyOfRange(udpOffset + UDP_HEADER_SIZE, udpOffset + udpLength),
        )
    }

    /** True when the packet claims to be IPv6 (which this pipeline does not route). */
    fun isIpv6(buffer: ByteArray, length: Int = buffer.size): Boolean =
        length >= 1 && (buffer[0].toInt() and 0xF0) == 0x60

    /** Builds a complete IPv4/UDP packet with correct IP and UDP checksums. */
    fun build(
        sourceIp: ByteArray,
        sourcePort: Int,
        destinationIp: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
        ttl: Int = DEFAULT_TTL,
    ): ByteArray {
        require(sourceIp.size == 4) { "sourceIp must be 4 bytes" }
        require(destinationIp.size == 4) { "destinationIp must be 4 bytes" }
        require(sourcePort in 0..65_535) { "invalid source port" }
        require(destinationPort in 0..65_535) { "invalid destination port" }

        val udpLength = UDP_HEADER_SIZE + payload.size
        val totalLength = IPV4_HEADER_SIZE + udpLength
        require(totalLength <= MAX_PACKET_SIZE) { "packet too large" }

        val packet = ByteArray(totalLength)
        packet[0] = 0x45 // IPv4, 5 words of header
        packet[1] = 0
        DnsU16.put(packet, 2, totalLength)
        DnsU16.put(packet, 4, nextId())
        DnsU16.put(packet, 6, FLAG_DONT_FRAGMENT)
        packet[8] = ttl.toByte()
        packet[9] = PROTOCOL_UDP.toByte()
        sourceIp.copyInto(packet, 12)
        destinationIp.copyInto(packet, 16)

        val udpOffset = IPV4_HEADER_SIZE
        DnsU16.put(packet, udpOffset, sourcePort)
        DnsU16.put(packet, udpOffset + 2, destinationPort)
        DnsU16.put(packet, udpOffset + 4, udpLength)
        payload.copyInto(packet, udpOffset + UDP_HEADER_SIZE)

        DnsU16.put(packet, 10, 0)
        DnsU16.put(packet, 10, internetChecksum(packet, 0, IPV4_HEADER_SIZE))

        val checksum = udpChecksum(sourceIp, destinationIp, packet, udpOffset, udpLength)
        DnsU16.put(packet, udpOffset + 6, if (checksum == 0) 0xFFFF else checksum)
        return packet
    }

    private fun internetChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(data, i).toLong()
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun udpChecksum(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        var sum = 0L
        for (i in 0 until 4 step 2) sum += u16(sourceIp, i).toLong()
        for (i in 0 until 4 step 2) sum += u16(destinationIp, i).toLong()
        sum += PROTOCOL_UDP.toLong()
        sum += udpLength.toLong()

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            sum += u16(packet, i).toLong()
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private val packetId = java.util.concurrent.atomic.AtomicInteger(1)

    private fun nextId(): Int = packetId.getAndIncrement() and 0xFFFF.toInt()

    /** Kept local so this object has no dependency on the DNS package. */
    private object DnsU16 {
        fun put(buffer: ByteArray, offset: Int, value: Int) {
            buffer[offset] = ((value shr 8) and 0xFF).toByte()
            buffer[offset + 1] = (value and 0xFF).toByte()
        }
    }
}
