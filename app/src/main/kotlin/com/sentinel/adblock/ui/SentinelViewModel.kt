package com.sentinel.adblock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.adblock.SentinelApplication
import com.sentinel.adblock.monitoring.ProtectionState
import com.sentinel.adblock.storage.SettingsRepository
import com.sentinel.adblock.vpn.VpnController
import com.sentinel.core.blocklists.FilterSnapshot
import com.sentinel.core.filtering.Domains
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * View model for the whole app (three screens, one small state graph).
 *
 * It owns no networking: the VPN service does that. It only loads the rule set,
 * exposes protection state, and edits user lists, each edit followed by an
 * atomic rule rebuild on a background dispatcher.
 */
class SentinelViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as SentinelApplication).container

    val status: StateFlow<ProtectionState.Status> = ProtectionState.status
    val counters: StateFlow<ProtectionState.Counters> = ProtectionState.counters
    val recent: StateFlow<List<ProtectionState.BlockedEvent>> = ProtectionState.recent
    val settings: StateFlow<SettingsRepository.Settings> = container.settings.state

    private val _blocklist = MutableStateFlow(container.blocklists.snapshot)
    val blocklist: StateFlow<FilterSnapshot> = _blocklist.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { container.blocklists.ensureLoaded() }
            _blocklist.value = snapshot
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun onVpnPermissionDenied() {
        _message.value = "VPN permission was not granted, so nothing is filtered."
    }

    fun setUpstream(upstream: SettingsRepository.Upstream) {
        container.settings.setUpstream(upstream)
        _message.value = "Upstream set to ${upstream.label}. Restart protection to apply."
    }

    fun addAllow(raw: String) = addDomain(raw, allow = true)

    fun addDeny(raw: String) = addDomain(raw, allow = false)

    fun removeAllow(domain: String) = removeDomain(domain, allow = true)

    fun removeDeny(domain: String) = removeDomain(domain, allow = false)

    private fun addDomain(raw: String, allow: Boolean) {
        val normalized = Domains.normalize(raw)
        if (normalized == null) {
            _message.value = "\"$raw\" is not a valid host name."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.settings.state.value
            if (allow) {
                container.settings.setAllowlist(current.allowlist + normalized)
            } else {
                container.settings.setDenylist(current.denylist + normalized)
            }
            applyUserRules()
            _message.value = (if (allow) "Allowed " else "Blocked ") + normalized
        }
    }

    private fun removeDomain(domain: String, allow: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.settings.state.value
            if (allow) {
                container.settings.setAllowlist(current.allowlist - domain)
            } else {
                container.settings.setDenylist(current.denylist - domain)
            }
            applyUserRules()
            _message.value = "Removed $domain"
        }
    }

    private suspend fun applyUserRules() {
        val snapshot = container.blocklists.refreshUserRules()
        _blocklist.value = snapshot
    }

    /**
     * Persists the bypass list and re-establishes the tunnel so it applies at
     * once. Doing it here (not per toggle) keeps the restart to one per edit.
     */
    fun setExcludedApps(packages: Set<String>) {
        container.settings.setExcludedApps(packages)
        VpnController.restart(getApplication())
    }
}
