package com.sentinel.adblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onExcludedApps: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showExceptions by remember { mutableStateOf(false) }
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
            SectionCard(title = "What Protection does") {
                Text(
                    text = "Protection asks Android for a local VPN and answers DNS for " +
                        "the whole device. When any app asks where an ad, tracker or " +
                        "known malware domain lives, Shieldly replies NXDOMAIN, so the " +
                        "connection is never opened. It covers browsers, in-app " +
                        "browsers, games and other apps alike.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "It does not inspect traffic, decrypt HTTPS, or look inside a " +
                        "page. It also cannot remove Facebook sponsored posts: those are " +
                        "served by facebook.com itself, which has to stay reachable for " +
                        "the app to work. That is what the sponsored-post hider is for, " +
                        "and it runs without the VPN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Apps that refuse to run behind a VPN — banking, wallets — can " +
                        "be excluded further down. Their traffic then leaves unfiltered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

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
            SectionCard(title = "Apps that bypass the filter") {
                Text(
                    text = "Banking and payment apps often refuse to run while a VPN " +
                        "is active. Apps excluded here send their traffic straight out " +
                        "through your normal connection, so they never see a VPN — and " +
                        "their own ads are not filtered either. Protection restarts " +
                        "automatically when you change this list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyValueRow("Excluded apps", settings.excludedApps.size.grouped())
                Button(
                    onClick = { showExceptions = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = if (settings.excludedApps.isEmpty()) "Choose apps"
                        else "Change apps",
                    )
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
                    text = "Shieldly filters DNS locally on this device and never decrypts " +
                        "HTTPS. The optional Facebook hider reads the Facebook feed's " +
                        "accessibility tree to press Hide Ad, and only while it is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }

    if (showExceptions) {
        AppExceptionsDialog(
            excluded = settings.excludedApps,
            onDismiss = { showExceptions = false },
            onApply = onExcludedApps,
        )
    }
}
