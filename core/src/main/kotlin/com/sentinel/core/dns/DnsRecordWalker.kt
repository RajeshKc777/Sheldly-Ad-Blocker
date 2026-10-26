package com.sentinel.core.dns

/**
 * Walks the answer section of a DNS message to read and rewrite TTLs.
 *
 * This exists because caching a response and replaying it verbatim would hand
 * clients a fresh TTL every time, letting a 60-second record live for hours.
 * Rewriting the stored TTL to "remaining" keeps the cache honest.
 *
 * Resource records whose type is OPT (41) are skipped: that field is a set of
 * flags, not a TTL.
 */
object DnsRecordWalker {

    /** @return true when every record was walked successfully. */
    inline fun forEachAnswer(
        message: ByteArray,
        action: (ttlOffset: Int, ttl: Int, type: Int) -> Unit,
    ): Boolean {
        if (message.size < DnsMessage.HEADER_SIZE) return false
        val questionCount = DnsMessage.u16(message, 4)
        val answerCount = DnsMessage.u16(message, 6)

        var offset = DnsMessage.HEADER_SIZE
        repeat(questionCount) {
            offset = skipName(message, offset) ?: return false
            if (offset + 4 > message.size) return false
            offset += 4
        }

        repeat(answerCount) {
            offset = skipName(message, offset) ?: return false
            if (offset + 10 > message.size) return false
            val type = DnsMessage.u16(message, offset)
            val ttlOffset = offset + 4
            val ttl = DnsMessage.u32(message, ttlOffset)
            val rdLength = DnsMessage.u16(message, offset + 8)
            if (type != DnsMessage.TYPE_OPT) {
                action(ttlOffset, ttl.toInt(), type)
            }
            offset += 10 + rdLength
            if (offset > message.size) return false
        }
        return true
    }

    /** Smallest answer TTL in seconds, or `null` when the message has no usable answers. */
    fun minAnswerTtl(message: ByteArray): Int? {
        var min = Int.MAX_VALUE
        var seen = false
        val ok = forEachAnswer(message) { _, ttl, _ ->
            seen = true
            if (ttl < min) min = ttl
        }
        if (!ok || !seen) return null
        return min.coerceIn(0, MAX_CACHE_TTL_SECONDS)
    }

    /** Subtracts [elapsedSeconds] from every answer TTL, clamped at zero. */
    fun rewriteAnswerTtls(message: ByteArray, elapsedSeconds: Int): Boolean {
        if (elapsedSeconds <= 0) return true
        return forEachAnswer(message) { ttlOffset, ttl, _ ->
            val remaining = (ttl - elapsedSeconds).coerceAtLeast(0)
            DnsMessage.putU32(message, ttlOffset, remaining.toLong())
        }
    }

    /** Upper bound so a buggy or hostile upstream cannot pin an entry for a week. */
    const val MAX_CACHE_TTL_SECONDS = 86_400

    /** Skips a (possibly compressed) name, returning the offset just past it in place. */
    fun skipName(buffer: ByteArray, start: Int): Int? {
        var offset = start
        while (true) {
            if (offset >= buffer.size) return null
            val length = buffer[offset].toInt() and 0xFF
            if (length == 0) return offset + 1
            if (length and 0xC0 == 0xC0) {
                return if (offset + 1 < buffer.size) offset + 2 else null
            }
            if (length and 0xC0 != 0) return null
            offset += 1 + length
            if (offset > buffer.size) return null
        }
    }
}
