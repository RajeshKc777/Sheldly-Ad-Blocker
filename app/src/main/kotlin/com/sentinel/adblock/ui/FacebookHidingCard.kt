package com.sentinel.adblock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sentinel.adblock.cosmetic.FacebookHiding

/**
 * Disclosure + entry point for the experimental Facebook hider.
 *
 * There is no way for an app to enable its own AccessibilityService, and this
 * screen does not pretend otherwise: the switch opens a plain-language
 * disclosure, then the system's Accessibility settings, where the user makes
 * the actual decision.
 */
@Composable
fun FacebookHidingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(FacebookHiding.isEnabled(context)) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val rulesUsable = remember(context) { FacebookHiding.readRules(context).isUsable }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = FacebookHiding.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SectionCard(title = "Facebook sponsored posts", modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "On in Accessibility settings" else "Off — tap to open the system switch",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Hides posts Facebook labels as ads by pressing Facebook's own " +
                        "Hide Ad button: Facebook removes the post, so nothing is drawn " +
                        "over the feed. Experimental: it breaks when Facebook changes its " +
                        "layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "Facebook Lite is not supported: its feed exposes no text to " +
                        "accessibility, so there is nothing to detect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { want ->
                    if (want) {
                        showDisclosure = true
                    } else {
                        // Disabling is also a system action; there is no API to revoke it.
                        FacebookHiding.openSettings(context)
                    }
                },
            )
        }

        if (!rulesUsable) {
            Text(
                text = "The detection rule file is missing or empty, so nothing would be " +
                    "hidden. Reinstall the app or restore assets/cosmetic/facebook_rules.txt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            text = "Shieldly presses Facebook's own Hide Ad button: Facebook removes the " +
                "post and closes the gap in the feed, so no block or overlay is drawn " +
                "over it. An ad that offers no such button is left alone rather than " +
                "covered. Android requires you to confirm this in system settings, so the " +
                "switch opens Accessibility settings rather than turning anything on itself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (showDisclosure) {
        DisclosureDialog(
            onDismiss = { showDisclosure = false },
            onOpenSettings = {
                showDisclosure = false
                FacebookHiding.openSettings(context)
            },
        )
    }
}

@Composable
private fun DisclosureDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you turn this on") },
        text = {
            Column {
                Text(
                    text = "This uses an accessibility service, so Android gives it broad " +
                        "access. Read this before enabling it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Bullet(
                    "What it can see",
                    "Everything Facebook and Facebook Lite draw on screen while it is on: " +
                        "post text, names, buttons, and images' descriptions. Android does " +
                        "not allow a narrower view.",
                )
                Bullet(
                    "What it does",
                    "Looks for the small ad label in a post header. If Facebook shows its " +
                        "own Hide Ad button on that post, it presses it, and Facebook removes " +
                        "the post and reflows the feed. If that button is absent, the post " +
                        "is left alone. It counts how many posts it handled.",
                )
                Bullet(
                    "What it never does",
                    "It does not store, log, or send screen text. It has no network " +
                        "access and takes no screenshots. It never scrolls, opens, likes, " +
                        "or navigates: the only tap it performs is on the Hide Ad button " +
                        "Facebook itself shows on an ad post, once per post.",
                )
                Bullet(
                    "What can go wrong",
                    "Facebook changes its layout often. If the label changes, hiding " +
                        "silently stops working. A false match would hide a normal post; " +
                        "the labels live in one file, not in the detector, so a fix is a " +
                        "one-line edit and a rebuild.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("Open Accessibility settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun Bullet(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
