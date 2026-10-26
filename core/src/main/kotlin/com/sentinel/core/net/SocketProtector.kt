package com.sentinel.core.net

import java.net.DatagramSocket

/**
 * Abstracts `VpnService.protect`, which only exists on Android.
 *
 * Calling it is what stops our own upstream DNS traffic from being routed back
 * into the tunnel. The MVP routes only the fake DNS address, so this is not
 * strictly required today, but skipping it would become a loop the moment a
 * broader route is added.
 */
fun interface SocketProtector {
    fun protect(socket: DatagramSocket): Boolean

    companion object {
        /** Used by unit tests and any non-Android host. */
        val NONE = SocketProtector { true }
    }
}
