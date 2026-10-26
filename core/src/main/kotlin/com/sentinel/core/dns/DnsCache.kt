package com.sentinel.core.dns

/**
 * Bounded, TTL-aware DNS response cache.
 *
 * Deliberately small (a few hundred entries): it exists to absorb the burst of
 * duplicate queries a single page load produces, not to be a resolver cache.
 * Android's own resolver caches above us anyway.
 *
 * Thread safety: all access is synchronised on the map. Contention is
 * negligible at DNS rates, and correctness beats cleverness here.
 */
class DnsCache(
    private val maxEntries: Int = 512,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Key(val name: String, val type: Int, val qClass: Int)

    data class Cached(val message: ByteArray, val elapsedSeconds: Int)

    private data class Entry(
        val message: ByteArray,
        val storedAtMillis: Long,
        val ttlSeconds: Int,
    )

    private val entries = object : LinkedHashMap<Key, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: Key): Cached? {
        val entry = entries[key] ?: return null
        val elapsedMillis = clock() - entry.storedAtMillis
        if (elapsedMillis < 0 || elapsedMillis >= entry.ttlSeconds * 1000L) {
            entries.remove(key)
            return null
        }
        // Hand out a copy: callers patch the ID and TTLs in place.
        return Cached(entry.message.copyOf(), (elapsedMillis / 1000L).toInt())
    }

    @Synchronized
    fun put(key: Key, message: ByteArray, ttlSeconds: Int) {
        if (ttlSeconds <= 0) return
        entries[key] = Entry(
            message = message.copyOf(),
            storedAtMillis = clock(),
            ttlSeconds = ttlSeconds.coerceAtMost(DnsRecordWalker.MAX_CACHE_TTL_SECONDS),
        )
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}
