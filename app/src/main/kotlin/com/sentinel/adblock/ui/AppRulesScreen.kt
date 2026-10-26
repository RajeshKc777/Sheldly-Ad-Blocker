package com.sentinel.adblock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sentinel.adblock.storage.SettingsRepository
import com.sentinel.core.blocklists.FilterSnapshot
import com.sentinel.core.filtering.Domains
import com.sentinel.core.filtering.FilterVerdict

/**
 * User-authored rules plus a checker that explains any single decision.
 *
 * The checker matters more than it looks: without it, a user whose favourite app
 * broke has no way to find out which rule did it. It reads the same immutable
 * snapshot the DNS path reads, so what it prints is exactly what the filter does.
 */
@Composable
fun AppRulesScreen(
    settings: SettingsRepository.Settings,
    blocklist: FilterSnapshot,
    onAddAllow: (String) -> Unit,
    onAddDeny: (String) -> Unit,
    onRemoveAllow: (String) -> Unit,
    onRemoveDeny: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newRule by rememberSaveable { mutableStateOf("") }
    var ruleMode by rememberSaveable { mutableStateOf(RuleMode.ALLOW) }
    var checkInput by rememberSaveable { mutableStateOf("") }

    val allowDomains = remember(settings.allowlist) { settings.allowlist.sorted() }
    val denyDomains = remember(settings.denylist) { settings.denylist.sorted() }

    fun submitRule() {
        val value = newRule.trim()
        if (value.isEmpty()) return
        if (ruleMode == RuleMode.ALLOW) onAddAllow(value) else onAddDeny(value)
        newRule = ""
    }

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
            SectionCard(title = "How rules combine") {
                Text(
                    text = "Your own rules are checked first. An allow rule beats every " +
                        "blocklist; a block rule adds a domain the bundled list does not " +
                        "cover. Rules match the domain itself and all of its subdomains.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Check a domain") {
                OutlinedTextField(
                    value = checkInput,
                    onValueChange = { checkInput = it },
                    label = { Text("e.g. ads.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (checkInput.isNotBlank()) {
                    Text(
                        text = describeVerdict(checkInput, blocklist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            SectionCard(title = "Add a rule") {
                OutlinedTextField(
                    value = newRule,
                    onValueChange = { newRule = it },
                    label = { Text("example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitRule() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        label = "Allow",
                        selected = ruleMode == RuleMode.ALLOW,
                        onClick = { ruleMode = RuleMode.ALLOW },
                    )
                    ModeButton(
                        label = "Block",
                        selected = ruleMode == RuleMode.BLOCK,
                        onClick = { ruleMode = RuleMode.BLOCK },
                    )
                }
                Button(
                    onClick = { submitRule() },
                    enabled = newRule.isNotBlank(),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(if (ruleMode == RuleMode.ALLOW) "Allow this domain" else "Block this domain")
                }
            }
        }

        item {
            Text(
                text = "Your exceptions (${allowDomains.size})",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (allowDomains.isEmpty()) {
            item { EmptyRuleHint("No exceptions yet. Add one when a blocklist breaks an app you trust.") }
        } else {
            items(allowDomains, key = { "allow-$it" }) { domain ->
                RuleRow(domain = domain, isException = true, onRemove = { onRemoveAllow(domain) })
            }
        }

        item {
            Text(
                text = "Your extra blocks (${denyDomains.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (denyDomains.isEmpty()) {
            item { EmptyRuleHint("No extra blocks yet. Anything on the bundled list is already blocked.") }
        } else {
            items(denyDomains, key = { "deny-$it" }) { domain ->
                RuleRow(domain = domain, isException = false, onRemove = { onRemoveDeny(domain) })
            }
        }
    }
}

private enum class RuleMode { ALLOW, BLOCK }

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun RuleRow(domain: String, isException: Boolean, onRemove: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isException) {
                        "Allowed, including subdomains"
                    } else {
                        "Blocked, including subdomains"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun EmptyRuleHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun describeVerdict(raw: String, snapshot: FilterSnapshot): String {
    val normalized = Domains.normalize(raw)
        ?: return "\"${raw.trim()}\" is not a valid host name, so it would not be filtered."
    val verdict = snapshot.evaluate(normalized)
    return when (verdict.reason) {
        FilterVerdict.Reason.BLOCK_RULE ->
            "$normalized is blocked by ${verdict.matchedRule} from " +
                "${sourceLabel(verdict.matchedRule?.origin?.sourceId ?: "")}."
        FilterVerdict.Reason.ALLOW_RULE ->
            "$normalized is allowed by ${verdict.matchedRule} from " +
                "${sourceLabel(verdict.matchedRule?.origin?.sourceId ?: "")}. " +
                "Exceptions beat every blocklist."
        FilterVerdict.Reason.NO_MATCH ->
            "$normalized is not on any list, so it resolves normally."
        FilterVerdict.Reason.INVALID_DOMAIN ->
            "$normalized is not a valid host name, so it would not be filtered."
    }
}
