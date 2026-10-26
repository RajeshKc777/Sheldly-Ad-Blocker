package com.sentinel.core.dns

/** Builders for realistic DNS packets used across the test suite. */
object DnsTestData {

    fun query(name: String, id: Int = 0x1234, type: Int = DnsMessage.TYPE_A): ByteArray =
        requireNotNull(DnsMessage.encodeQuery(id, name, type)) { "could not encode $name" }

    fun questionEnd(query: ByteArray): Int =
        requireNotNull(DnsMessage.parseQuery(query)).questionEndOffset

    /**
     * A minimal NOERROR response with one A answer.
     * The answer name is a compression pointer to the question at offset 12.
     */
    fun response(
        query: ByteArray,
        ttl: Int,
        address: ByteArray = byteArrayOf(93, 184.toByte(), 216.toByte(), 34),
    ): ByteArray {
        val end = questionEnd(query)
        val answer = ByteArray(12)
        answer[0] = 0xC0.toByte()
        answer[1] = 0x0C
        DnsMessage.putU16(answer, 2, DnsMessage.TYPE_A)
        DnsMessage.putU16(answer, 4, DnsMessage.CLASS_IN)
        DnsMessage.putU32(answer, 6, ttl.toLong())
        DnsMessage.putU16(answer, 10, address.size)

        val out = query.copyOf(end + 12 + address.size)
        DnsMessage.putU16(out, 2, DnsMessage.u16(out, 2) or 0x8080) // QR + RA
        DnsMessage.putU16(out, 6, 1) // one answer
        answer.copyInto(out, end)
        address.copyInto(out, end + 12)
        return out
    }

    fun rcode(response: ByteArray): Int = DnsMessage.u16(response, 2) and 0x0F

    fun isResponse(response: ByteArray): Boolean = (response[2].toInt() and 0x80) != 0

    fun answerCount(response: ByteArray): Int = DnsMessage.u16(response, 6)
}
