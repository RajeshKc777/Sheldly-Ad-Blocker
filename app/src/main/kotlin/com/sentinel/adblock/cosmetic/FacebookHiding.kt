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
     * Measured on MIUI (Android 11) and AOSP 16: the per-service details page
     * (`Settings$AccessibilityDetailsSettingsActivity`) is guarded by
     * `android.permission.OPEN_ACCESSIBILITY_DETAILS_SETTINGS`, which is held by
     * system apps only — launching it from here throws SecurityException. So the
     * attempt is kept (some OEM builds honour it) and the fallback is the real
     * path: the Accessibility list, where the service is one tap away under
     * "Downloaded apps". Nothing in-app can flip that switch.
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
