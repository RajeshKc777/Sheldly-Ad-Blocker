package com.sentinel.adblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.sentinel.adblock.MainActivity
import com.sentinel.adblock.R
import com.sentinel.adblock.SentinelApplication
import com.sentinel.adblock.monitoring.ProtectionState
import com.sentinel.core.dns.DnsCache
import com.sentinel.core.dns.DnsMessage
import com.sentinel.core.dns.DnsPipeline
import com.sentinel.core.dns.UpstreamResolver
import com.sentinel.core.dns.UdpUpstreamResolver
import com.sentinel.core.net.Ipv4Udp
import com.sentinel.core.net.SocketProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS-only local VPN.
 *
 * ## What it actually does
 *
 * The tunnel is deliberately *narrow*. Only one address is routed into it — a
 * fake DNS server (`10.111.222.1`) that is also handed to the system as the
 * device's resolver. Every other packet, on every other port, keeps using the
 * device's real network and never enters this process.
 *
 * That single decision is what makes the MVP honest and safe:
 *  - no TCP stack, no UDP relay, no packet forwarding to invent;
 *  - ordinary internet access cannot be broken by a filtering bug;
 *  - we never silently discard traffic, because we never receive it.
 *
 * The cost: anything that bypasses the system resolver (hard-coded DNS servers,
 * DNS-over-HTTPS, DNS-over-TLS inside another app) is not filtered. That is
 * documented in docs/LIMITATIONS.md rather than papered over.
 *
 * ## Failure behaviour
 *
 * Fail-open for transport (upstream timeout -> SERVFAIL, app sees a normal DNS
 * failure), fail-closed for the claim of protection (no rules -> refuse to
 * start). A tunnel is only reported RUNNING after `establish()` returned a
 * descriptor and the reader thread is live.
 */
class AdBlockVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    @Volatile
    private var tunnel: Tunnel? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnController.ACTION_STOP) {
            stopProtection()
            return START_NOT_STICKY
        }
        ensureForeground()
        scope.launch {
            startMutex.withLock { startProtection() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopTunnel()
        ProtectionState.setError("VPN access was revoked. Another VPN app may have taken over.")
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
        scope.cancel()
        if (ProtectionState.status.value.phase != ProtectionState.Phase.ERROR) {
            ProtectionState.setStopped()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun startProtection() {
        if (tunnel != null) return

        ProtectionState.setStarting()

        val app = application as? SentinelApplication
        if (app == null) {
            failAndStop("Internal error: application container is unavailable.")
            return
        }
        val container = app.container
        val snapshot = container.blocklists.ensureLoaded()
        if (!snapshot.isActive) {
            failAndStop("No blocklist rules are loaded, so protection would block nothing. It was not started.")
            return
        }

        val upstream = container.settings.state.value.upstream
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
            .addRoute(DNS_VIP, 32)
            .addDnsServer(DNS_VIP)
            .setBlocking(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        try {
            // Our own traffic (e.g. future list downloads) must not re-enter the filter.
            builder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            // Impossible for our own package; never fail a tunnel over it.
        }

        val descriptor: ParcelFileDescriptor? = try {
            builder.establish()
        } catch (_: Exception) {
            null
        }
        if (descriptor == null) {
            failAndStop("VPN permission was denied or revoked. Nothing is being filtered.")
            return
        }

        val resolver: UpstreamResolver = try {
            UdpUpstreamResolver(
                serverAddress = InetAddress.getByName(upstream.address),
                protector = SocketProtector { socket -> protect(socket) },
            )
        } catch (e: Exception) {
            runCatching { descriptor.close() }
            failAndStop("Could not open the upstream DNS connection (${e.message}).")
            return
        }

        val pipeline = DnsPipeline(
            filter = { container.blocklists.snapshot },
            resolver = resolver,
            cache = DnsCache(),
        )

        val newTunnel = Tunnel(descriptor, resolver, pipeline)
        tunnel = newTunnel
        newTunnel.start()

        ProtectionState.setRunning(snapshot.blockRuleCount)
        updateNotification(snapshot.blockRuleCount)
    }

    private fun stopProtection() {
        stopTunnel()
        ProtectionState.setStopped()
        stopSelf()
    }

    private fun failAndStop(message: String) {
        ProtectionState.setError(message)
        stopTunnel()
        stopSelf()
    }

    private fun stopTunnel() {
        val current = tunnel ?: return
        tunnel = null
        current.close()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun ensureForeground() {
        val notification = buildNotification("Starting DNS filtering")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(ruleCount: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification("$ruleCount domain rules active"))
        }
    }

    /**
     * Owns the TUN reader, the worker pool and the upstream resolver.
     * One instance per protection session; closing it is idempotent.
     */
    private inner class Tunnel(
        private val descriptor: ParcelFileDescriptor,
        private val resolver: UpstreamResolver,
        private val pipeline: DnsPipeline,
    ) {
        private val running = AtomicBoolean(false)
        private val writeLock = Any()

        private val workers = ThreadPoolExecutor(
            2,
            8,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(256),
            { runnable -> Thread(runnable, "sentinel-dns").apply { isDaemon = true } },
            ThreadPoolExecutor.CallerRunsPolicy(),
        )

        private var reader: Thread? = null

        fun start() {
            running.set(true)
            reader = Thread({ readLoop() }, "sentinel-tun").apply { start() }
        }

        private fun readLoop() {
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(READ_BUFFER_SIZE)

            try {
                while (running.get()) {
                    val length = try {
                        input.read(buffer)
                    } catch (_: IOException) {
                        -1
                    }
                    if (length <= 0) break

                    if (Ipv4Udp.isIpv6(buffer, length)) {
                        ProtectionState.recordIpv6()
                        continue
                    }

                    val datagram = Ipv4Udp.parse(buffer, length) ?: continue
                    if (datagram.destinationPort != DNS_PORT) continue

                    val payload = datagram.payload
                    val clientIp = datagram.sourceIp
                    val clientPort = datagram.sourcePort

                    val task = Runnable {
                        val outcome = pipeline.handle(payload) ?: return@Runnable
                        val fitsMtu = IPV4_HEADER + UDP_HEADER + outcome.response.size <= MTU
                        val responsePayload = if (fitsMtu) {
                            outcome.response
                        } else {
                            DnsMessage.buildTruncatedResponse(payload) ?: return@Runnable
                        }
                        val reply = Ipv4Udp.build(
                            sourceIp = DNS_VIP_BYTES,
                            sourcePort = DNS_PORT,
                            destinationIp = clientIp,
                            destinationPort = clientPort,
                            payload = responsePayload,
                        )
                        try {
                            synchronized(writeLock) { output.write(reply) }
                        } catch (_: IOException) {
                            return@Runnable
                        }
                        ProtectionState.record(outcome)
                    }

                    try {
                        workers.execute(task)
                    } catch (_: RejectedExecutionException) {
                        // Pool is shutting down; the client will retry.
                    }
                }
            } finally {
                runCatching { input.close() }
                runCatching { output.close() }
            }
        }

        fun close() {
            running.set(false)
            runCatching { descriptor.close() }
            workers.shutdownNow()
            resolver.close()
            reader?.interrupt()
        }
    }

    private companion object {
        const val DNS_PORT = 53
        const val VPN_ADDRESS = "10.111.222.2"
        const val VPN_PREFIX_LENGTH = 24
        const val DNS_VIP = "10.111.222.1"
        val DNS_VIP_BYTES = byteArrayOf(10, 111, 222.toByte(), 1)
        const val MTU = 1500
        const val IPV4_HEADER = 20
        const val UDP_HEADER = 8
        const val READ_BUFFER_SIZE = 32_767
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "protection"
    }
}
