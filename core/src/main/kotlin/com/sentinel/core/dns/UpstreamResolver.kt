package com.sentinel.core.dns

import com.sentinel.core.net.SocketProtector
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Blocking DNS transport. Implementations must be safe for concurrent use. */
interface UpstreamResolver : Closeable {
    /**
     * Sends a raw DNS query and returns the raw response, or `null` on timeout,
     * network error or an invalid reply. Callers translate `null` into SERVFAIL;
     * they must not treat it as "blocked".
     */
    fun query(payload: ByteArray): ByteArray?
}

/**
 * Plain UDP forwarder to a single upstream server addressed by IP literal.
 *
 * Why an IP literal: resolving a *name* for the upstream resolver would itself
 * require DNS, and that lookup would arrive back at this resolver. IP literals
 * break the cycle without needing a bootstrap hack.
 *
 * Why a socket pool: a UDP socket has one receive queue, so two threads reading
 * from the same socket can steal each other's replies. One socket per in-flight
 * query, capped by the pool size, keeps replies matched to queries without any
 * ID demultiplexing.
 */
class UdpUpstreamResolver(
    private val serverAddress: InetAddress,
    private val port: Int = 53,
    private val protector: SocketProtector = SocketProtector.NONE,
    private val timeoutMs: Int = 4_000,
    poolSize: Int = 4,
    private val maxResponseBytes: Int = 4_096,
) : UpstreamResolver {

    private val pool = ArrayBlockingQueue<DatagramSocket>(poolSize)
    private val closed = AtomicBoolean(false)

    init {
        repeat(poolSize) { openSocket()?.let { pool.offer(it) } }
    }

    override fun query(payload: ByteArray): ByteArray? {
        if (closed.get()) return null
        if (payload.isEmpty()) return null

        val socket = try {
            pool.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } ?: return null

        var healthy = true
        try {
            socket.send(DatagramPacket(payload, payload.size))
            val buffer = ByteArray(maxResponseBytes)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val body = response.data.copyOf(response.length)
            if (!isValidResponse(payload, body)) return null
            return body
        } catch (_: SocketTimeoutException) {
            return null
        } catch (_: IOException) {
            healthy = false
            return null
        } finally {
            if (healthy && !closed.get()) {
                if (!pool.offer(socket)) socket.close()
            } else {
                runCatching { socket.close() }
                if (!closed.get()) openSocket()?.let { pool.offer(it) }
            }
        }
    }

    override fun close() {
        closed.set(true)
        while (true) {
            val socket = pool.poll() ?: break
            runCatching { socket.close() }
        }
    }

    private fun openSocket(): DatagramSocket? = try {
        val socket = DatagramSocket()
        // Must be protected before connecting, or the socket can be routed into our own VPN.
        protector.protect(socket)
        socket.connect(serverAddress, port)
        socket.soTimeout = timeoutMs
        socket
    } catch (_: Exception) {
        null
    }

    companion object {
        /**
         * Rejects replies that do not belong to the query. Without this an
         * off-path attacker (or a confused server) could inject answers into
         * the cache.
         */
        fun isValidResponse(query: ByteArray, response: ByteArray): Boolean {
            if (query.size < DnsMessage.HEADER_SIZE) return false
            if (response.size < DnsMessage.HEADER_SIZE) return false
            if (response[0] != query[0] || response[1] != query[1]) return false
            if ((response[2].toInt() and 0x80) == 0) return false // QR must be set
            if (DnsMessage.u16(response, 4) < 1) return false // no question echoed

            val expected = DnsMessage.parseQuestion(query) ?: return false
            val actual = DnsMessage.parseQuestion(response) ?: return false
            return expected.type == actual.type &&
                expected.qClass == actual.qClass &&
                expected.name.equals(actual.name, ignoreCase = true)
        }
    }
}
