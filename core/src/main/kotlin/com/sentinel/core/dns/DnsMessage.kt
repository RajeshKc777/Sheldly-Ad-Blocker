package com.sentinel.core.dns

import com.sentinel.core.filtering.Domains

/**
 * Minimal, defensive DNS wire-format handling (RFC 1035).
 *
 * This is not a general-purpose DNS library. It does exactly what a filtering
 * forwarder needs: read the question, echo it back in a syntactically valid
 * reply, read record TTLs, and patch bytes in place. Every read is bounds
 * checked, every length field is validated, and name decompression is limited
 * so a hostile packet cannot cause a loop or an unbounded allocation.
 *
 * Why hand-rolled instead of a library: the operations are tiny and the
 * dependency budget for a security-sensitive component is better spent on
 * nothing at all. dnsjava is the obvious alternative and is worth re-evaluating
 * if DNS-over-HTTPS/TCP or DNSSEC validation is added (see docs/RESEARCH.md).
 */
object DnsMessage {

    const val HEADER_SIZE = 12
    const val MAX_NAME_LENGTH = 255
    const val MAX_LABEL_LENGTH = 63

    const val TYPE_A = 1
    const val TYPE_CNAME = 5
    const val TYPE_SOA = 6
    const val TYPE_AAAA = 28
    const val TYPE_OPT = 41

    const val CLASS_IN = 1

    const val RCODE_NOERROR = 0
    const val RCODE_FORMERR = 1
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_NOTIMP = 4
    const val RCODE_REFUSED = 5

    private const val FLAG_QR = 0x8000
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080
    private const val FLAG_CD = 0x0010
    private const val OPCODE_MASK = 0x7800

    private const val MAX_POINTER_JUMPS = 8

    data class Question(val name: String, val type: Int, val qClass: Int)

    data class Query(
        val id: Int,
        val flags: Int,
        val question: Question,
        /** Offset just past the question section; the rest is not needed. */
        val questionEndOffset: Int,
    )

    /** Parses an incoming query. Returns `null` for anything malformed or unsupported. */
    fun parseQuery(payload: ByteArray): Query? {
        if (payload.size < HEADER_SIZE) return null
        val flags = u16(payload, 2)
        if (flags and FLAG_QR != 0) return null // this is a response, not a query
        if (u16(payload, 4) != 1) return null // we only handle single-question queries
        if (u16(payload, 6) != 0) return null // queries must not carry answers

        val parsed = readName(payload, HEADER_SIZE) ?: return null
        val afterName = parsed.second
        if (afterName + 4 > payload.size) return null
        val type = u16(payload, afterName)
        val qClass = u16(payload, afterName + 2)

        val normalized = Domains.normalize(parsed.first)
            ?: parsed.first.trimEnd('.').lowercase()

        return Query(
            id = u16(payload, 0),
            flags = flags,
            question = Question(normalized, type, qClass),
            questionEndOffset = afterName + 4,
        )
    }

    /** Parses just the question section of any message (query or response). */
    fun parseQuestion(payload: ByteArray): Question? {
        if (payload.size < HEADER_SIZE) return null
        val parsed = readName(payload, HEADER_SIZE) ?: return null
        val afterName = parsed.second
        if (afterName + 4 > payload.size) return null
        return Question(parsed.first, u16(payload, afterName), u16(payload, afterName + 2))
    }

    /**
     * Builds an error/empty reply that echoes the original question verbatim.
     * Used for blocked domains (NXDOMAIN) and upstream failures (SERVFAIL).
     */
    fun buildErrorResponse(queryPayload: ByteArray, rcode: Int): ByteArray? {
        val query = parseQuery(queryPayload) ?: return null
        val end = query.questionEndOffset
        if (end > queryPayload.size) return null

        val reply = queryPayload.copyOfRange(0, end)
        putU16(reply, 0, query.id)
        var flags = query.flags and (FLAG_RD or FLAG_CD or OPCODE_MASK)
        flags = flags or FLAG_QR or FLAG_RA or (rcode and 0x0F)
        putU16(reply, 2, flags)
        putU16(reply, 4, 1) // QDCOUNT: the echoed question
        putU16(reply, 6, 0) // ANCOUNT
        putU16(reply, 8, 0) // NSCOUNT
        putU16(reply, 10, 0) // ARCOUNT (drops any EDNS OPT record on purpose)
        return reply
    }

    /**
     * An empty reply with TC set, asking the client to retry over TCP.
     *
     * Used when a response is too large to fit in one IPv4/UDP packet on the
     * tunnel. Handing back a truncated reply is far better than writing a
     * fragment-sized packet the client will silently drop.
     */
    fun buildTruncatedResponse(queryPayload: ByteArray): ByteArray? {
        val reply = buildErrorResponse(queryPayload, RCODE_NOERROR) ?: return null
        putU16(reply, 2, u16(reply, 2) or 0x0200) // TC
        return reply
    }

    /** Builds a query, used by the diagnostics screen and by tests. */
    fun encodeQuery(id: Int, name: String, type: Int = TYPE_A): ByteArray? {
        val labels = Domains.normalize(name)?.split('.') ?: return null
        val nameBytes = ArrayList<Byte>(name.length + 2)
        for (label in labels) {
            if (label.length > MAX_LABEL_LENGTH) return null
            nameBytes.add(label.length.toByte())
            for (ch in label) nameBytes.add(ch.code.toByte())
        }
        nameBytes.add(0)
        if (nameBytes.size > MAX_NAME_LENGTH) return null

        val packet = ByteArray(HEADER_SIZE + nameBytes.size + 4)
        putU16(packet, 0, id and 0xFFFF)
        putU16(packet, 2, FLAG_RD)
        putU16(packet, 4, 1)
        for (i in nameBytes.indices) packet[HEADER_SIZE + i] = nameBytes[i]
        val tail = HEADER_SIZE + nameBytes.size
        putU16(packet, tail, type)
        putU16(packet, tail + 2, CLASS_IN)
        return packet
    }

    /** Overwrites the transaction ID in place. */
    fun patchId(payload: ByteArray, id: Int) {
        if (payload.size >= 2) putU16(payload, 0, id and 0xFFFF)
    }

    /**
     * Decodes a compressed name.
     * @return the name and the offset immediately after it in the original buffer.
     */
    fun readName(buffer: ByteArray, start: Int): Pair<String, Int>? {
        if (start < 0 || start >= buffer.size) return null
        val builder = StringBuilder(32)
        var offset = start
        var resumeAt = -1
        var jumps = 0
        var wireLength = 0

        while (true) {
            if (offset >= buffer.size) return null
            val length = buffer[offset].toInt() and 0xFF
            when {
                length == 0 -> {
                    if (resumeAt < 0) resumeAt = offset + 1
                    break
                }

                length and 0xC0 == 0xC0 -> {
                    if (offset + 1 >= buffer.size) return null
                    val pointer = ((length and 0x3F) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
                    if (resumeAt < 0) resumeAt = offset + 2
                    if (++jumps > MAX_POINTER_JUMPS) return null
                    if (pointer >= buffer.size || pointer == offset) return null
                    offset = pointer
                }

                length and 0xC0 != 0 -> return null // reserved label types

                length > MAX_LABEL_LENGTH -> return null

                else -> {
                    if (offset + 1 + length > buffer.size) return null
                    if (builder.isNotEmpty()) builder.append('.')
                    for (i in 0 until length) {
                        val byte = buffer[offset + 1 + i].toInt() and 0xFF
                        if (byte == 0x2E) return null // a literal dot inside a label is not a host name
                        builder.append(byte.toChar())
                    }
                    wireLength += length + 1
                    if (wireLength > MAX_NAME_LENGTH) return null
                    offset += 1 + length
                }
            }
        }
        if (builder.isEmpty()) return null
        return builder.toString() to resumeAt
    }

    fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    fun putU16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    fun u32(buffer: ByteArray, offset: Int): Long =
        ((buffer[offset].toLong() and 0xFF) shl 24) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
            (buffer[offset + 3].toLong() and 0xFF)

    fun putU32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }
}
