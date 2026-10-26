package com.sentinel.adblock.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.sentinel.adblock.monitoring.ProtectionState

/**
 * Thin wrapper around service lifecycle so the UI never talks to VpnService
 * internals. It also keeps the "ask for consent" step explicit: [prepareIntent]
 * returning a non-null Intent means the system still needs the user's approval.
 */
object VpnController {

    const val ACTION_START = "com.sentinel.adblock.action.START"
    const val ACTION_STOP = "com.sentinel.adblock.action.STOP"

    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, AdBlockVpnService::class.java).setAction(ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stopping uses stopService(), not startService(ACTION_STOP): stopping a
     * service is always allowed, while starting one from the background is
     * restricted. The service tears itself down in onDestroy().
     */
    fun stop(context: Context) {
        context.stopService(Intent(context, AdBlockVpnService::class.java))
        ProtectionState.setStopped()
    }
}
