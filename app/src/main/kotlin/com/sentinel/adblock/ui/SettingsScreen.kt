package com.sentinel.adblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sentinel.adblock.monitoring.ProtectionState
import com.sentinel.adblock.storage.SettingsRepository
import com.sentinel.core.blocklists.FilterSnapshot

@Composable
fun SettingsScreen(
    settings: SettingsRepository.Settings,
    blocklist: FilterSnapshot,
    status: ProtectionState.Status,
    onUpstream: (SettingsRepository.Upstream) -> Unit,
    onCoverTheme: (SettingsRepository.CoverTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Upstream DNS resolver") {
                Text(
                    text = "Queries that are not blocked are forwarded to this resolver " +
                        "over UDP, port 53. It sees every domain your device looks up. " +
                        "Change it if you prefer a different provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsRepository.Upstream.entries.forEach { upstream ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.upstream == upstream,
                                onClick = { onUpstream(upstream) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.upstream == upstream,
                            onClick = { onUpstream(upstream) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = upstream.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = upstream.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (status.isActive) {
                    Text(
                        text = "Restart protection to use a newly selected resolver.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            FacebookHidingCard()
        }

        item {
            SectionCard(title = "Cover colour") {
                Text(
                    text = "The blank block drawn over a hidden Facebook post. Facebook has " +
                        "its own light/dark switch, separate from the system theme, so pick " +
                        "whichever matches your Facebook feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsRepository.CoverTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.coverTheme == theme,
                                onClick = { onCoverTheme(theme) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.coverTheme == theme,
                            onClick = { onCoverTheme(theme) },
                        )
                        Text(
                            text = theme.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Privacy") {
                Text(
                    text = "• Lookup counters and the recent-blocked list live in memory " +
                        "only. They are cleared when protection restarts.\n" +
                        "• Nothing is written to disk except your own allow/block rules " +
                        "and the resolver choice.\n" +
                        "• No analytics, no crash reporting, no account, no ads.\n" +
                        "• The app has no server of its own. DNS traffic goes straight to " +
                        "the resolver above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Filter build") {
                KeyValueRow("Sources", blocklist.sourceIds.joinToString().ifEmpty { "none" })
                KeyValueRow("Block rules", blocklist.blockRuleCount.grouped())
                KeyValueRow("Exception rules", blocklist.allowRuleCount.grouped())
                KeyValueRow("Parsed lines", blocklist.stats.accepted.grouped())
                KeyValueRow("Duplicates dropped", blocklist.stats.duplicates.grouped())
                KeyValueRow("Unsupported lines", blocklist.stats.unsupported.grouped())
                KeyValueRow("Invalid lines", blocklist.stats.invalid.grouped())
            }
        }

        item {
            SectionCard(title = "About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 46.dp)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Shieldly",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )

                Text(
                    text = "DEVELOPED BY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Rajesh Katuwal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = "© 2026 Rajesh Katuwal. All rights reserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Shieldly filters DNS locally on this device. It does not " +
                        "decrypt HTTPS traffic or remove content from inside a page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}
