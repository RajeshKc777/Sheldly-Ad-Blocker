package com.sentinel.adblock.cosmetic

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sentinel.core.cosmetic.SponsoredDetector
import com.sentinel.core.cosmetic.SponsoredRules
import java.util.concurrent.Executors

/**
 * Experimental, opt-in hider for Facebook posts labelled as advertising.
 *
 * ## What it does
 *
 * On a content change in Facebook or Facebook Lite it walks the visible
 * accessibility tree, asks [SponsoredDetector] which posts are ads, and covers
 * those posts with an opaque accessibility overlay. The ad has still been
 * downloaded and rendered underneath; this only removes it from *view*. The UI
 * therefore says "ads hidden on screen", never "ads blocked".
 *
 * ## What it never does
 *
 * - no text, names, or screenshots are stored or transmitted;
 * - no gestures are dispatched and no clicks are performed on Facebook's UI;
 * - no other package is observed (`accessibility_service_config.xml` scopes it
 *   to the two Facebook packages);
 * - no work happens while the screen is off.
 *
 * ## Cost
 *
 * A full tree walk is the expensive part. Scans are debounced, run only for the
 * two packages above, and back off when a scan takes too long or the device is
 * in battery saver.
 */
class FacebookAdHiderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-cosmetic").apply { isDaemon = true }
    }

    private var detector: SponsoredDetector = SponsoredDetector(SponsoredRules.EMPTY)
    private var overlays: AdOverlayController? = null

    private var debounceMs = NORMAL_DEBOUNCE_MS

    /** Throttles scans regardless of how chatty Facebook's events are. */
    private var lastScanAt = 0L

    /**
     * Last time the active window really was Facebook. The watchdog uses this
     * instead of [lastScanAt] so a null root is not mistaken for "still here".
     */
    private var lastFacebookRootAt = 0L

    /** Drops results from a scan that a newer content change has superseded. */
    private var generation = 0

    /**
     * Covers are global windows. Facebook-only event scoping means the service
     * is never told when the user leaves Facebook, so without this watchdog a
     * cover would float over whatever app is opened next.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            val now = SystemClock.uptimeMillis()
            when {
                // Another app is in front: remove immediately.
                activePackage != null && activePackage !in FacebookHiding.packages ->
                    overlays?.clear()

                // If node access is scoped to Facebook, leaving the app yields a
                // null root rather than another package. Fall back to a grace
                // period measured from the last time Facebook was really active.
                activePackage == null && now - lastFacebookRootAt > NULL_ROOT_GRACE_MS ->
                    overlays?.clear()
            }
            if (overlays?.hasCovers == true) {
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private val scanRunnable = Runnable { runScan() }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val rules = FacebookHiding.readRules(this)
        detector = SponsoredDetector(rules)
        overlays = AdOverlayController(this)

        if (!rules.isUsable) {
            // No screen text is logged, only the fact that configuration failed.
            Log.w(TAG, "Sponsored rules are missing or empty; hiding is inactive.")
        }

        // The service can be enabled while Facebook is already on screen and
        // idle, so do not wait for the next content change to hide anything.
        scheduleScan(baseDelay = 0L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }
        if (!isScreenOn()) return
        if (event.packageName?.toString() !in FacebookHiding.packages) return

        // Covers live in screen coordinates. Rather than dropping them on every
        // scroll (which left the ad visible until the feed stopped moving), move
        // them with the list, then rescan to catch posts that scrolled into view.
        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                overlays?.clear()
                scheduleScan(baseDelay = 0L)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // A delta of zero means we cannot know how far the list moved;
                // a cover in the wrong place is worse than no cover.
                val delta = event.scrollDeltaY
                if (delta != 0) overlays?.offsetBy(delta) else overlays?.clear()
                scheduleScan(baseDelay = currentDelay())
            }

            else -> scheduleScan(baseDelay = currentDelay())
        }
    }

    private fun currentDelay(): Long =
        if (isPowerSaveMode()) SLOW_DEBOUNCE_MS else debounceMs

    /**
     * Posts a scan, collapsing bursts of events into one. [baseDelay] is
     * shortened to zero once the last scan is older than [MAX_SCAN_LATENCY_MS],
     * so a continuous fling still gets scanned instead of postponing forever.
     */
    private fun scheduleScan(baseDelay: Long) {
        val overdue = SystemClock.uptimeMillis() - lastScanAt > MAX_SCAN_LATENCY_MS
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, if (overdue) 0L else baseDelay)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(scanRunnable)
        overlays?.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        handler.removeCallbacks(watchdog)
        overlays?.clear()
        overlays = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun runScan() {
        // Mark before any early return, otherwise an unreadable root would make
        // every event look overdue and scans would run back to back.
        lastScanAt = SystemClock.uptimeMillis()

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString()
        if (packageName !in FacebookHiding.packages) return
        lastFacebookRootAt = lastScanAt

        val screenHeight = resources.displayMetrics.heightPixels
        val scanGeneration = ++generation

        // Tree walking is the expensive part; keeping it off the main thread
        // means Facebook's own scrolling never waits for our scan.
        worker.execute {
            val started = SystemClock.uptimeMillis()
            val tree = AccessibilityTreeReader.read(root) ?: return@execute
            val matches = detector.findAds(tree, screenHeight)
            val cost = SystemClock.uptimeMillis() - started

            handler.post {
                if (scanGeneration != generation) return@post
                lastScanAt = SystemClock.uptimeMillis()

                // Adaptive backoff: a slow phone should not be walked several
                // times a second, but recover as soon as scans are cheap again.
                debounceMs = if (cost > SLOW_SCAN_MS) SLOW_DEBOUNCE_MS else NORMAL_DEBOUNCE_MS

                for (match in matches) {
                    CosmeticStatistics.record(match.label, match.container)
                }
                val controller = overlays
                controller?.render(matches.map { it.container })
                if (controller?.hasCovers == true) {
                    handler.removeCallbacks(watchdog)
                    handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
                }
            }
        }
    }

    private fun isScreenOn(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return true
        return power.isInteractive
    }

    private fun isPowerSaveMode(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        return power.isPowerSaveMode
    }

    private companion object {
        const val TAG = "SentinelCosmetic"
        const val NORMAL_DEBOUNCE_MS = 150L
        const val SLOW_DEBOUNCE_MS = 900L
        const val MAX_SCAN_LATENCY_MS = 900L
        const val SLOW_SCAN_MS = 80L
        const val WATCHDOG_INTERVAL_MS = 500L
        const val NULL_ROOT_GRACE_MS = 3_000L
    }
}
