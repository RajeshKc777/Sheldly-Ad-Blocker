package com.sentinel.core.blocklists

import com.sentinel.core.filtering.ParseStats
import com.sentinel.core.filtering.RuleParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Decides whether a downloaded or imported list is safe to install.
 *
 * The checks are deliberately about resource limits and "is this the right kind
 * of file", not about judging content: a list is rejected only when it is empty
 * of usable rules, unreasonably large, or so full of unsupported syntax that it
 * is almost certainly the wrong format (for example a cosmetic-only browser
 * list). Everything else is reported as a warning so the user can see what was
 * skipped.
 */
object BlocklistValidator {

    data class Limits(
        val maxBytes: Int = 8 * 1024 * 1024,
        val maxLines: Int = 800_000,
        val minAcceptedRules: Int = 1,
    )

    data class Result(
        val accepted: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
    )

    fun validate(
        text: String,
        stats: ParseStats,
        limits: Limits = Limits(),
    ): Result {
        val errors = ArrayList<String>(3)
        val warnings = ArrayList<String>(2)

        val byteCount = text.toByteArray(Charsets.UTF_8).size
        if (byteCount > limits.maxBytes) {
            errors.add("list is ${byteCount / 1024} KiB, limit is ${limits.maxBytes / 1024} KiB")
        }
        if (text.isBlank()) {
            errors.add("list is empty")
        }
        if (stats.totalLines > limits.maxLines) {
            errors.add("list has ${stats.totalLines} lines, limit is ${limits.maxLines}")
        }
        if (stats.accepted < limits.minAcceptedRules) {
            errors.add("list contains no supported domain rules")
        }

        val ruleLines = stats.accepted + stats.rejected
        if (ruleLines > 0) {
            val rejectedRatio = stats.rejected.toDouble() / ruleLines
            if (rejectedRatio > 0.9 && stats.accepted > 0) {
                warnings.add(
                    "most lines were skipped (" +
                        "${(rejectedRatio * 100).toInt()}% unsupported); " +
                        "this may be a browser-only list",
                )
            }
        }

        return Result(errors.isEmpty(), errors, warnings)
    }
}

sealed interface FetchResult {
    data class Success(val body: String, val byteCount: Int) : FetchResult
    data class Failure(val reason: String) : FetchResult
}

fun interface BlocklistFetcher {
    fun fetch(url: String): FetchResult
}

/**
 * HTTPS-only downloader.
 *
 * Redirects are followed manually so that every hop is re-checked for HTTPS:
 * `HttpURLConnection` will happily follow an `https://` -> `http://` redirect,
 * which would let a compromised host downgrade a blocklist into plaintext.
 */
class HttpsBlocklistFetcher(
    private val maxBytes: Int = 8 * 1024 * 1024,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val maxRedirects: Int = 3,
) : BlocklistFetcher {

    override fun fetch(url: String): FetchResult {
        var current = url
        var redirects = 0
        while (true) {
            when (val attempt = requestOnce(current)) {
                is Attempt.Done -> return attempt.result
                is Attempt.Redirect -> {
                    redirects++
                    if (redirects > maxRedirects) return FetchResult.Failure("too many redirects")
                    current = attempt.location
                }
            }
        }
    }

    private fun requestOnce(url: String): Attempt {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return Attempt.Done(FetchResult.Failure("malformed URL"))
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Attempt.Done(FetchResult.Failure("only https:// URLs are allowed"))
        }
        var connection: HttpURLConnection? = null
        try {
            connection = uri.toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", "Sentinel/0.1 (+dns-filter)")
            connection.setRequestProperty("Accept", "text/plain,*/*;q=0.5")

            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: return Attempt.Done(FetchResult.Failure("redirect without Location"))
                return Attempt.Redirect(uri.resolve(location).toString())
            }
            if (status != 200) {
                return Attempt.Done(FetchResult.Failure("upstream returned HTTP $status"))
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxBytes) {
                return Attempt.Done(FetchResult.Failure("declared size exceeds limit"))
            }

            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(64 * 1024)
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        return Attempt.Done(FetchResult.Failure("download exceeded size limit"))
                    }
                    output.write(buffer, 0, read)
                }
                val body = output.toString(Charsets.UTF_8.name())
                return Attempt.Done(FetchResult.Success(body, total))
            }
        } catch (e: IOException) {
            return Attempt.Done(FetchResult.Failure(e.message ?: "network error"))
        } finally {
            connection?.disconnect()
        }
    }

    private sealed interface Attempt {
        data class Done(val result: FetchResult) : Attempt
        data class Redirect(val location: String) : Attempt
    }
}

/** Validated result of an update attempt. */
sealed interface UpdateResult {
    data class Accepted(
        val source: RuleSource,
        val ruleCount: Int,
        val warnings: List<String>,
        val byteCount: Int,
    ) : UpdateResult

    data class Rejected(val reason: String) : UpdateResult
}

/**
 * Downloads, parses, validates and hands back a [RuleSource].
 *
 * It deliberately does *not* install anything: the caller rebuilds the filter
 * and the repository swaps the snapshot atomically. That split is what keeps
 * "downloaded a broken list" from ever becoming "lost a working blocklist".
 */
class BlocklistUpdater(
    private val fetcher: BlocklistFetcher,
    private val parser: RuleParser = RuleParser(),
) {

    fun fetchAndValidate(
        sourceId: String,
        displayName: String,
        url: String,
    ): UpdateResult {
        val fetched = when (val result = fetcher.fetch(url)) {
            is FetchResult.Failure -> return UpdateResult.Rejected(result.reason)
            is FetchResult.Success -> result
        }

        val parsed = parser.parse(sourceId, fetched.body)
        val validation = BlocklistValidator.validate(fetched.body, parsed.stats)
        if (!validation.accepted) {
            return UpdateResult.Rejected(validation.errors.joinToString("; "))
        }
        return UpdateResult.Accepted(
            source = RuleSource(sourceId, displayName, fetched.body),
            ruleCount = parsed.stats.accepted,
            warnings = validation.warnings,
            byteCount = fetched.byteCount,
        )
    }
}
