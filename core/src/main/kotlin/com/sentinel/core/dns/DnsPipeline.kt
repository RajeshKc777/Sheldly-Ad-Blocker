package com.sentinel.core.dns

import com.sentinel.core.filtering.CompiledRule
import com.sentinel.core.filtering.DomainFilter

/**
 * The DNS hot path: classify, answer from cache, or forward upstream.
 *
 * Ports of this class stay deliberately small and side-effect free apart from
 * the cache, so it can be tested with a fake resolver and a fake clock.
 */
class DnsPipeline(
    private val filter: () -> DomainFilter,
    private val resolver: UpstreamResolver,
    private val cache: DnsCache = DnsCache(),
    private val blockedResponseCode: Int = DnsMessage.RCODE_NXDOMAIN,
) {

    data class Outcome(
        val response: ByteArray,
        val blocked: Boolean,
        val allowlisted: Boolean,
        val question: String,
        val matchedRule: CompiledRule?,
        val fromCache: Boolean,
        val upstreamFailed: Boolean,
    )

    /**
     * @return the DNS payload to send back, or `null` when the query is so
     * malformed that not even an error reply can be constructed. Callers drop
     * those packets; the client will time out and retry, which is the correct
     * fail-open behaviour for garbage we cannot reason about.
     */
    fun handle(queryPayload: ByteArray): Outcome? {
        val query = DnsMessage.parseQuery(queryPayload) ?: return null

        val verdict = filter().evaluate(query.question.name)
        if (verdict.blocked) {
            val response = DnsMessage.buildErrorResponse(queryPayload, blockedResponseCode)
                ?: return null
            return Outcome(
                response = response,
                blocked = true,
                allowlisted = false,
                question = query.question.name,
                matchedRule = verdict.matchedRule,
                fromCache = false,
                upstreamFailed = false,
            )
        }

        val key = DnsCache.Key(query.question.name, query.question.type, query.question.qClass)
        val cached = cache.get(key)
        if (cached != null) {
            DnsMessage.patchId(cached.message, query.id)
            DnsRecordWalker.rewriteAnswerTtls(cached.message, cached.elapsedSeconds)
            return Outcome(
                response = cached.message,
                blocked = false,
                allowlisted = verdict.isAllowlisted,
                question = query.question.name,
                matchedRule = verdict.matchedRule,
                fromCache = true,
                upstreamFailed = false,
            )
        }

        val upstream = resolver.query(queryPayload)
        if (upstream == null) {
            val response = DnsMessage.buildErrorResponse(queryPayload, DnsMessage.RCODE_SERVFAIL)
                ?: return null
            return Outcome(
                response = response,
                blocked = false,
                allowlisted = verdict.isAllowlisted,
                question = query.question.name,
                matchedRule = verdict.matchedRule,
                fromCache = false,
                upstreamFailed = true,
            )
        }

        DnsMessage.patchId(upstream, query.id)
        val ttl = DnsRecordWalker.minAnswerTtl(upstream)
        if (ttl != null && ttl > 0) cache.put(key, upstream, ttl)

        return Outcome(
            response = upstream,
            blocked = false,
            allowlisted = verdict.isAllowlisted,
            question = query.question.name,
            matchedRule = verdict.matchedRule,
            fromCache = false,
            upstreamFailed = false,
        )
    }
}
