package com.sentinel.core.blocklists

import com.sentinel.core.filtering.ParseStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistUpdaterTest {

    @Test
    fun `accepts a valid list`() {
        val updater = BlocklistUpdater(
            fetcher = {
                FetchResult.Success("||ads.example^\n0.0.0.0 tracker.example\n", 40)
            },
        )
        val result = updater.fetchAndValidate("my-list", "My list", "https://lists.example/list.txt")

        assertTrue(result is UpdateResult.Accepted)
        val accepted = result as UpdateResult.Accepted
        assertEquals(2, accepted.ruleCount)
        assertEquals("my-list", accepted.source.id)
        assertEquals(40, accepted.byteCount)
    }

    @Test
    fun `rejects an empty body`() {
        val updater = BlocklistUpdater(fetcher = { FetchResult.Success("", 0) })
        val result = updater.fetchAndValidate("id", "Name", "https://lists.example/list.txt")
        assertTrue(result is UpdateResult.Rejected)
        assertTrue((result as UpdateResult.Rejected).reason.contains("empty"))
    }

    @Test
    fun `rejects a body with no usable rules`() {
        val updater = BlocklistUpdater(fetcher = { FetchResult.Success("example.com##.ad", 15) })
        val result = updater.fetchAndValidate("id", "Name", "https://lists.example/list.txt")
        assertTrue(result is UpdateResult.Rejected)
        assertTrue((result as UpdateResult.Rejected).reason.contains("no supported domain rules"))
    }

    @Test
    fun `passes through fetch failures`() {
        val updater = BlocklistUpdater(fetcher = { FetchResult.Failure("connection reset") })
        val result = updater.fetchAndValidate("id", "Name", "https://lists.example/list.txt")
        assertTrue(result is UpdateResult.Rejected)
        assertEquals("connection reset", (result as UpdateResult.Rejected).reason)
    }

    @Test
    fun `https fetcher refuses plain http without touching the network`() {
        val result = HttpsBlocklistFetcher().fetch("http://lists.example/list.txt")
        assertTrue(result is FetchResult.Failure)
        assertTrue((result as FetchResult.Failure).reason.contains("https"))
    }

    @Test
    fun `https fetcher refuses malformed urls`() {
        val result = HttpsBlocklistFetcher().fetch("not a url")
        assertTrue(result is FetchResult.Failure)
    }

    @Test
    fun `validator enforces the size limit`() {
        val result = BlocklistValidator.validate(
            text = "||ads.example^",
            stats = ParseStats(totalLines = 1, accepted = 1),
            limits = BlocklistValidator.Limits(maxBytes = 4),
        )
        assertTrue(!result.accepted)
        assertTrue(result.errors.any { it.contains("limit") })
    }

    @Test
    fun `validator warns about mostly unsupported lists but still accepts them`() {
        val result = BlocklistValidator.validate(
            text = "||ads.example^\n" + (1..50).joinToString("\n") { "site$it.example##.banner" },
            stats = ParseStats(totalLines = 51, accepted = 1, unsupported = 50),
        )
        assertTrue(result.accepted)
        assertTrue(result.warnings.any { it.contains("unsupported") })
    }

    @Test
    fun `validator rejects lists with too many lines`() {
        val result = BlocklistValidator.validate(
            text = "||ads.example^",
            stats = ParseStats(totalLines = 1_000, accepted = 1),
            limits = BlocklistValidator.Limits(maxLines = 100),
        )
        assertTrue(!result.accepted)
    }
}
