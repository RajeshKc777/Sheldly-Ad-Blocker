package com.sentinel.adblock.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * User-visible settings.
 *
 * Why SharedPreferences rather than DataStore: the VPN service needs the
 * upstream resolver synchronously, on a background thread, at the moment the
 * tunnel is established, and settings here are a handful of scalars written by
 * the user. DataStore would force a suspending read inside a lifecycle that is
 * already delicate. If settings ever grow into a synced or transactional store,
 * this class is the single place to change.
 *
 * Nothing here is browsing history: the allow/deny lists are user-authored.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    data class Settings(
        val upstream: Upstream = Upstream.CLOUDFLARE,
        val allowlist: Set<String> = emptySet(),
        val denylist: Set<String> = emptySet(),
        /**
         * Packages whose traffic must leave the device directly instead of
         * entering the tunnel. Banking and payment apps refuse to run while a
         * VPN is active; excluding them means they never see one.
         */
        val excludedApps: Set<String> = emptySet(),
    )

    /**
     * Upstream resolvers are addressed by IP literal on purpose: resolving a
     * host name for the resolver would itself need the resolver.
     */
    enum class Upstream(val label: String, val address: String) {
        CLOUDFLARE("Cloudflare", "1.1.1.1"),
        GOOGLE("Google", "8.8.8.8"),
        QUAD9("Quad9", "9.9.9.9"),
        ADGUARD("AdGuard DNS", "94.140.14.14"),
        ;

        companion object {
            fun fromKey(key: String?): Upstream =
                entries.firstOrNull { it.name == key } ?: CLOUDFLARE
        }
    }

    fun setUpstream(upstream: Upstream) {
        prefs.edit().putString(KEY_UPSTREAM, upstream.name).apply()
        _state.update { it.copy(upstream = upstream) }
    }

    fun setAllowlist(domains: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWLIST, HashSet(domains)).apply()
        _state.update { it.copy(allowlist = domains.toSet()) }
    }

    fun setDenylist(domains: Set<String>) {
        prefs.edit().putStringSet(KEY_DENYLIST, HashSet(domains)).apply()
        _state.update { it.copy(denylist = domains.toSet()) }
    }

    /**
     * Package names are stored verbatim: an app that is uninstalled and later
     * reinstalled keeps its bypass without the user having to notice.
     */
    fun setExcludedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_APPS, HashSet(packages)).apply()
        _state.update { it.copy(excludedApps = packages.toSet()) }
    }

    private fun read(): Settings = Settings(
        upstream = Upstream.fromKey(prefs.getString(KEY_UPSTREAM, null)),
        allowlist = prefs.getStringSet(KEY_ALLOWLIST, null)?.toSet() ?: emptySet(),
        denylist = prefs.getStringSet(KEY_DENYLIST, null)?.toSet() ?: emptySet(),
        excludedApps = prefs.getStringSet(KEY_EXCLUDED_APPS, null)?.toSet() ?: emptySet(),
    )

    private companion object {
        const val PREFS_NAME = "sentinel_settings"
        const val KEY_UPSTREAM = "upstream"
        const val KEY_ALLOWLIST = "allowlist"
        const val KEY_DENYLIST = "denylist"
        const val KEY_EXCLUDED_APPS = "excluded_apps"
    }
}
