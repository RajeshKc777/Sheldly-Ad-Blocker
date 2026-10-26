package com.sentinel.adblock

import android.app.Application
import com.sentinel.adblock.data.AppContainer

/**
 * Process-wide composition root. Small on purpose: there is no DI framework
 * here, only three objects with clear lifetimes.
 */
class SentinelApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
