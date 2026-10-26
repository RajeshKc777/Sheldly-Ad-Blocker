package com.sentinel.adblock.blocklists

import android.content.Context
import com.sentinel.adblock.storage.SettingsRepository
import com.sentinel.core.blocklists.BlocklistRepository
import com.sentinel.core.blocklists.FilterEngine
import com.sentinel.core.blocklists.FilterSnapshot
import com.sentinel.core.blocklists.RuleSource
import com.sentinel.core.filtering.AllowlistManager
import java.io.IOException

/**
 * Owns the active filter and the rule text it was built from.
 *
 * The asset is read once, on a background thread, the first time protection is
 * started (or the dashboard loads). Rebuilding is cheap afterwards and always
 * swaps the repository snapshot atomically, so a DNS query in flight never sees
 * a half-built rule set.
 */
class Blocklists(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private val repository = BlocklistRepository()
    private val allowlist = AllowlistManager()
    private val engine = FilterEngine(repository, allowlist)

    private var loaded = false

    /** Cheap, allocation-free read for the DNS hot path. */
    val snapshot: FilterSnapshot get() = repository.current

    @Synchronized
    fun ensureLoaded(): FilterSnapshot {
        if (loaded) return repository.current
        val outcome = engine.rebuild(listOf(builtInSource()))
        loaded = true
        return outcome.snapshot
    }

    @Synchronized
    fun refreshUserRules(): FilterSnapshot {
        if (!loaded) return ensureLoaded()
        applyUserRulesFromSettings()
        return engine.refreshUserRules().snapshot
    }

    private fun applyUserRulesFromSettings() {
        val current = settings.state.value
        allowlist.replaceAllow(current.allowlist)
        allowlist.replaceDeny(current.denylist)
    }

    private fun builtInSource(): RuleSource {
        val text = try {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            ""
        }
        return RuleSource(BUILT_IN_ID, "Bundled test list", text)
    }

    companion object {
        const val BUILT_IN_ID = "builtin-test-list"
        const val ASSET_PATH = "blocklist/test-blocklist.txt"
    }
}
