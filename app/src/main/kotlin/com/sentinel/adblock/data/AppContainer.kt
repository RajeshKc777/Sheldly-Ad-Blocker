package com.sentinel.adblock.data

import android.content.Context
import com.sentinel.adblock.blocklists.Blocklists
import com.sentinel.adblock.storage.SettingsRepository

/**
 * Holds the long-lived objects the UI and the VPN service share.
 *
 * Everything here is safe to construct on the main thread: no file or network
 * IO happens until [Blocklists.ensureLoaded] is called from a background
 * dispatcher by the service or the view model.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = SettingsRepository(appContext)

    val blocklists = Blocklists(appContext, settings)
}
