package com.sentinel.adblock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * Entry point for the experimental Facebook hider.
 *
 * There is no way for an app to enable its own AccessibilityService, so nothing
 * on this card pretends to be a real switch: the card body, the switch itself
 * and the button all open the system screen where the toggle lives. The switch
 * mirrors the system state and never flips locally.
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

    /**
     * The only thing this screen can do is send the user to the system page, so
     * the card body, the switch and the button all do the same thing: open the
     * system screen where the toggle actually lives. The switch is deliberately
     * not a local on/off state: Android owns that bit, and faking it here would
     * let the UI say "on" while nothing runs.
     */
    fun openSystemSwitch() {
        FacebookHiding.openSettings(context)
    }

    SectionCard(
        title = "Facebook sponsored posts",
        modifier = modifier.clickable(onClick = ::openSystemSwitch),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "On in Accessibility settings"
                    else "Off. Tap to open the system switch",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Independent of Protection: this works with the VPN off. " +
                        "Protection blocks domains by DNS; this presses Facebook's own " +
                        "Hide Ad button on posts Facebook labels as ads, so Facebook " +
                        "removes them and nothing is drawn over the feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "Experimental: it breaks when Facebook changes its layout.",
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
                // Same action as the button and the card: this row never changes
                // the switch locally, because only Android can grant the service.
                onCheckedChange = { openSystemSwitch() },
            )
        }

        OutlinedButton(
            onClick = ::openSystemSwitch,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text(
                if (enabled) "Open the system switch"
                else "Enable in Accessibility settings",
            )
        }

        TextButton(
            onClick = { showDisclosure = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 0.dp,
                end = 8.dp,
                top = 4.dp,
                bottom = 0.dp,
            ),
        ) {
            Text("What it can see on screen")
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
            text = "Android keeps a service's own toggle page system-only, so Shieldly can " +
                "open Accessibility but cannot flip the switch for you. Under " +
                "\u201CDownloaded apps\u201D tap \u201CShieldly sponsored-post hider\u201D and " +
                "turn it on, then come back: the switch above updates by itself.",
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
