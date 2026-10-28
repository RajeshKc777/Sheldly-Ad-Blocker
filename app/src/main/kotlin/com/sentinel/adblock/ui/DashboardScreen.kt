package com.sentinel.adblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.adblock.R
import com.sentinel.adblock.monitoring.ProtectionState
import com.sentinel.adblock.ui.theme.sentinelColors
import com.sentinel.core.blocklists.FilterSnapshot

/**
 * The one screen that answers "am I protected, and is it doing anything?".
 *
 * Every number here is a DNS query count. The screen never says "ads blocked"
 * because the app cannot know whether a blocked host would have served an ad,
 * a tracker, or something the user wanted.
 */
@Composable
fun DashboardScreen(
    status: ProtectionState.Status,
    counters: ProtectionState.Counters,
    recent: List<ProtectionState.BlockedEvent>,
    blocklist: FilterSnapshot,
    cosmeticHidden: Long,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ProtectionHero(status = status, onToggle = onToggle)
        }

        if (status.phase == ProtectionState.Phase.ERROR && status.message != null) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Protection is not running",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "DNS queries",
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
                    label = "Blocked share",
                    value = percentOf(counters.blocked, counters.total),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(title = "Facebook sponsored posts") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = cosmeticHidden.grouped(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "ads hidden on screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
                Text(
                    text = "Presses Facebook's own Hide Ad button on posts that Facebook " +
                        "labels as ads, so Facebook removes the post. Nothing is drawn over " +
                        "the feed. Facebook Lite exposes no post text to accessibility. " +
                        "Enable it in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            SectionCard(title = "Loaded rules") {
                KeyValueRow("Block rules", blocklist.blockRuleCount.grouped())
                KeyValueRow("Exception rules", blocklist.allowRuleCount.grouped())
                KeyValueRow(
                    "Session started",
                    if (status.sinceMillis > 0L) formatTime(status.sinceMillis) else "–",
                )
            }
        }

        item {
            SectionCard(title = "Recent blocked lookups") {
                if (recent.isEmpty()) {
                    Text(
                        text = if (status.isActive) {
                            "Nothing blocked yet. Counters start again every time " +
                                "protection starts."
                        } else {
                            "Turn protection on to start counting."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    recent.take(8).forEachIndexed { index, event ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                        BlockedRow(event)
                    }
                }
            }
        }

        item {
            SectionCard(title = "How Shieldly works") {
                Text(
                    text = "Shieldly answers DNS lookups on your device. A domain on a " +
                        "blocklist gets an NXDOMAIN reply, so the app asking for it has " +
                        "nothing to connect to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "It cannot see inside HTTPS traffic, so it cannot remove an ad " +
                        "that is served from the same domain as the page you are reading. " +
                        "It also cannot filter apps that use their own DNS-over-HTTPS " +
                        "resolver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProtectionHero(status: ProtectionState.Status, onToggle: (Boolean) -> Unit) {
    val running = status.isActive
    val brand = sentinelColors()
    val scheme = MaterialTheme.colorScheme

    val gradient = if (running) {
        listOf(brand.heroStart, brand.heroEnd)
    } else {
        listOf(scheme.surface, scheme.surfaceVariant)
    }
    val content = if (running) Color.White else scheme.onSurface
    val subtle = content.copy(alpha = if (running) 0.78f else 0.62f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = content.copy(alpha = if (running) 0.10f else 0.05f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-26).dp)
                    .size(188.dp),
            )
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (running) "Active" else "Inactive",
                        color = if (running) Color.White else content.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = running,
                        onCheckedChange = onToggle,
                        enabled = status.phase != ProtectionState.Phase.STARTING,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = brand.heroStart,
                            checkedTrackColor = Color.White,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = content.copy(alpha = 0.85f),
                            uncheckedTrackColor = content.copy(alpha = 0.12f),
                            uncheckedBorderColor = content.copy(alpha = 0.35f),
                        ),
                    )
                }

                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.displaySmall,
                    color = content,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = when (status.phase) {
                        ProtectionState.Phase.RUNNING ->
                            "Ad, tracker and malware domains are refused for every app " +
                                "on this device. Facebook sponsored posts are handled " +
                                "separately, without the VPN."
                        ProtectionState.Phase.STARTING -> "Waiting for the tunnel to come up."
                        ProtectionState.Phase.ERROR -> "The tunnel stopped. See the error below."
                        ProtectionState.Phase.STOPPED ->
                            "Turn this on to refuse ad, tracker and malware domains for " +
                                "every app on this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtle,
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (running) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        HeroChip(text = "${status.ruleCount.grouped()} rules", content = content)
                        if (status.sinceMillis > 0L) {
                            HeroChip(
                                text = "Since ${formatTime(status.sinceMillis)}",
                                content = content,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, content: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(content.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun BlockedRow(event: ProtectionState.BlockedEvent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.domain,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${event.rule} · ${sourceLabel(event.source)} · " +
                    formatTime(event.atMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
