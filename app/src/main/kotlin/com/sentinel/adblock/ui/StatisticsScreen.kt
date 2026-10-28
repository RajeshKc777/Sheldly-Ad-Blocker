package com.sentinel.adblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.adblock.monitoring.ProtectionState
import com.sentinel.core.blocklists.FilterSnapshot

/**
 * A deliberately boring diagnostics screen.
 *
 * Each row is a counter the DNS pipeline actually increments, with the meaning
 * spelled out. There is no chart, because a chart of DNS lookups would imply a
 * precision about "ads blocked" that the data does not have.
 */
@Composable
fun StatisticsScreen(
    counters: ProtectionState.Counters,
    status: ProtectionState.Status,
    blocklist: FilterSnapshot,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Lookups",
                    value = counters.total.grouped(),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Blocked",
                    value = counters.blocked.grouped(),
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Allowed",
                    value = counters.allowed.grouped(),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(title = "This session") {
                Text(
                    text = "Counters reset every time protection starts and are never " +
                        "written to disk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyValueRow("DNS lookups seen", counters.total.grouped())
                KeyValueRow("Answered from blocklists", counters.blocked.grouped())
                KeyValueRow("Forwarded and answered", counters.allowed.grouped())
                KeyValueRow("Matched an allow rule", counters.allowlistedQueries.grouped())
                KeyValueRow("Answered from cache", counters.cacheHits.grouped())
                KeyValueRow("Upstream failures", counters.upstreamFailures.grouped())
                KeyValueRow("Blocked share", percentOf(counters.blocked, counters.total))
                KeyValueRow(
                    "Running since",
                    if (status.sinceMillis > 0L) formatTime(status.sinceMillis) else "not running",
                )
            }
        }

        item {
            SectionCard(title = "Not filtered by this build") {
                KeyValueRow("IPv6 DNS packets seen", counters.ipv6Observed.grouped())
                Text(
                    text = "The tunnel routes a single IPv4 address. IPv6 DNS traffic " +
                        "takes the device's normal path, so it is counted here but not " +
                        "inspected. Private DNS (DNS-over-TLS) is not filtered either.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard(title = "Parser report") {
                Text(
                    text = "${blocklist.stats.totalLines.grouped()} lines read from " +
                        "${blocklist.sourceIds.size} source(s).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyValueRow("Accepted", blocklist.stats.accepted.grouped())
                KeyValueRow("Blank", blocklist.stats.blankLines.grouped())
                KeyValueRow("Comments", blocklist.stats.commentLines.grouped())
                KeyValueRow("Duplicates", blocklist.stats.duplicates.grouped())
                KeyValueRow("Unsupported syntax", blocklist.stats.unsupported.grouped())
                KeyValueRow("Invalid", blocklist.stats.invalid.grouped())
                Text(
                    text = "Unsupported lines are cosmetic-rule syntax (##), modifiers ($), " +
                        "paths and regex. They are reported rather than silently ignored so " +
                        "the rule count stays trustworthy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            Text(
                text = "A blocked lookup is a DNS name that was answered with NXDOMAIN. It " +
                    "is not a count of advertisements removed.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
