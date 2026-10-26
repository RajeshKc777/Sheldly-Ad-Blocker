package com.sentinel.adblock.cosmetic

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.sentinel.core.cosmetic.SponsoredRules

/**
 * Small helpers around the system accessibility state.
 *
 * There is no in-app "enable" switch for a reason: an AccessibilityService can
 * only be turned on by the user in system settings. Pretending otherwise would
 * let the UI show "on" while nothing is running.
 */
object FacebookHiding {

    const val KATANA = "com.facebook.katana"
    const val LITE = "com.facebook.lite"

    val packages: Set<String> = setOf(KATANA, LITE)

    const val RULES_ASSET = "cosmetic/facebook_rules.txt"

    /** True when *our* service is in the system's enabled list right now. */
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = FacebookAdHiderService::class.java.name
        return manager
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                serviceInfo.packageName == context.packageName && serviceInfo.name == expected
            }
    }

    /** Opens the system screen where the user can enable or disable the service.
     *
     * Prefers the service's own details page (one switch, no hunting through the
     * list of every accessibility service). That page is addressed by the
     * platform-internal action string because the constant is not part of the
     * public SDK; every fallback path still lands on a usable system screen.
     */
    fun openSettings(context: Context) {
        val component = android.content.ComponentName(context, FacebookAdHiderService::class.java)
        val details = android.content.Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
            .putExtra(android.content.Intent.EXTRA_COMPONENT_NAME, component)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(details) }.isSuccess
        if (!opened) {
            val fallback = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    /** `Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS`, absent from the public SDK. */
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    /** Reads the replaceable rule file from assets. Missing file => empty rules. */
    fun readRules(context: Context): SponsoredRules {
        val text = runCatching {
            context.assets.open(RULES_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return SponsoredRules.EMPTY
        return SponsoredRules.parse(text)
    }
}
