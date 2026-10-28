package com.sentinel.adblock.cosmetic

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.adblock.BuildConfig
import com.sentinel.core.cosmetic.Bounds
import com.sentinel.core.cosmetic.SponsoredDetector
import com.sentinel.core.cosmetic.SponsoredRules
import java.util.concurrent.Executors

/**
 * Experimental, opt-in hider for Facebook posts labelled as advertising.
 *
 * ## What it does
 *
 * On a content change in Facebook it looks for Facebook's own "Hide Ad" button
 * and presses it. That is the only control this app ever presses: the button
 * exists only on a sponsored post, so finding it is itself proof that the post
 * is an ad. Facebook then removes the post and reflows the feed, so nothing is
 * left behind: no blank block, no overlay, no trace.
 *
 * Two paths, same action:
 *
 * 1. **Immediate**: the node that just changed is walked first, without waiting
 *    for a debounce. This is the fast path that makes the ad disappear before it
 *    can be read.
 * 2. **Full scan**: the visible tree is walked and the button inside each post
 *    matched by its ad label is pressed. This catches posts that were already on
 *    screen when the service started, and posts preloaded below the fold.
 *
 * There is deliberately **no cover/overlay fallback**: a post that exposes no
 * "Hide Ad" button is left alone rather than replaced by a visible block.
 *
 * ## What it never does
 *
 * - no text, names, or screenshots are stored or transmitted;
 * - no scrolling, liking, opening or navigating: the only action dispatched is
 *   a click on the "Hide Ad" control Facebook renders on an ad post, at most
 *   once per post;
 * - no other package is observed (`accessibility_service_config.xml` scopes it
 *   to the two Facebook packages);
 * - no work happens while the screen is off.
 *
 * ## Cost
 *
 * A full tree walk is the expensive part. Scans are debounced, run only for the
 * packages above, and back off when a scan takes too long or the device is in
 * battery saver. The fast path is budgeted and rate-limited so chattering events
 * stay cheap.
 */
class FacebookAdHiderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-cosmetic").apply { isDaemon = true }
    }

    private var rules: SponsoredRules = SponsoredRules.EMPTY
    private var detector: SponsoredDetector = SponsoredDetector(SponsoredRules.EMPTY)

    private var debounceMs = NORMAL_DEBOUNCE_MS

    /** Throttles scans regardless of how chatty Facebook's events are. */
    private var lastScanAt = 0L

    /** Drops results from a scan that a newer content change has superseded. */
    private var generation = 0

    /**
     * Hide Ad button rectangle -> when it was pressed. A button is pressed at
     * most once, so a control that survives its click cannot be hammered in a
     * loop. Touched from the main thread (fast path) and the worker (full scan),
     * so every access is synchronised.
     */
    private val hideAttempts = HashMap<String, Long>()

    /** Rate limit for the fast path, so a surviving button is not hammered. */
    private var lastQuickPressAt = 0L

    /** Rate limit for the fast-path walk itself, so chattering events stay cheap. */
    private var lastQuickWalkAt = 0L

    private val scanRunnable = Runnable { runScan() }

    override fun onServiceConnected() {
        super.onServiceConnected()

        rules = FacebookHiding.readRules(this)
        detector = SponsoredDetector(rules)

        if (!rules.isUsable) {
            // No screen text is logged, only the fact that configuration failed.
            Log.w(TAG, "Sponsored rules are missing or empty; hiding is inactive.")
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "hider connected: fast-path build")

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

        val eventAt = SystemClock.uptimeMillis()

        // Fast path: the node that just changed usually contains the post that
        // appeared, including Facebook's own "Hide Ad" button. Pressing it here
        // skips the debounce entirely, because that wait is what made the ad visible
        // long enough to read.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val source = runCatching { event.source }.getOrNull()
            if (source != null && pressHideControlNow(source, eventAt)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "fast press in ${SystemClock.uptimeMillis() - eventAt}ms")
                }
                scheduleScan(baseDelay = QUICK_RESCAN_MS)
                return
            }
        }

        // Posts enter the feed as it scrolls, so both a scroll and a content
        // change are reasons for a full scan.
        scheduleScan(baseDelay = if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 0L else currentDelay())
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
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
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
        val screenHeight = resources.displayMetrics.heightPixels
        val scanGeneration = ++generation

        // Tree walking and the button press are the expensive parts; keeping
        // them off the main thread means Facebook's own scrolling never waits.
        worker.execute {
            val started = SystemClock.uptimeMillis()
            val snapshot = AccessibilityTreeReader.read(root, rules) ?: return@execute
            val matches = detector.findAds(snapshot.tree, screenHeight)

            val now = SystemClock.uptimeMillis()
            prunePresses(now)

            val handled = ArrayList<Pair<String, Bounds>>(matches.size)
            for (match in matches) {
                val control = snapshot.hideControls.firstOrNull { hit ->
                    contains(match.container, hit.bounds)
                } ?: continue

                val key = keyOf(control.bounds)
                if (wasPressed(key)) continue

                val pressed = runCatching {
                    control.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                rememberPress(key, now)
                if (pressed) handled.add(match.label to match.container)
            }
            val cost = SystemClock.uptimeMillis() - started

            handler.post {
                if (scanGeneration != generation) return@post
                lastScanAt = SystemClock.uptimeMillis()

                // Adaptive backoff: a slow phone should not be walked several
                // times a second, but recover as soon as scans are cheap again.
                debounceMs = if (cost > SLOW_SCAN_MS) SLOW_DEBOUNCE_MS else NORMAL_DEBOUNCE_MS

                if (handled.isNotEmpty() && BuildConfig.DEBUG) {
                    Log.d(TAG, "scan pressed ${handled.size} in ${cost}ms")
                }
                for ((label, bounds) in handled) CosmeticStatistics.record(label, bounds)
            }
        }
    }

    /**
     * Fast path: walks the subtree that just changed and presses a "Hide Ad"
     * control the moment it is visible.
     *
     * This runs on the accessibility callback thread, before the debounced full
     * scan, and is what removes the flash of an ad entering the feed. The walk
     * is breadth-first so a control near the top of a new post is found before
     * the depth of the first one is exhausted, and the node budget keeps the
     * callback a few milliseconds long.
     *
     * Pressing is safe without a container match: Facebook renders "Hide Ad"
     * only on a sponsored post, never on a normal one ("Hide post" is a
     * different control and is never matched).
     */
    private fun pressHideControlNow(source: AccessibilityNodeInfo, now: Long): Boolean {
        if (now - lastQuickPressAt < QUICK_PRESS_MIN_GAP_MS) return false
        if (now - lastQuickWalkAt < QUICK_WALK_MIN_GAP_MS) return false
        lastQuickWalkAt = now

        // Starting at the parent covers one more sibling level: when the node
        // that changed is the ad's timestamp, the Hide Ad button is a sibling.
        val start = runCatching { source.parent }.getOrNull() ?: source

        var budget = QUICK_SCAN_NODES
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(start)

        while (queue.isNotEmpty() && budget-- > 0) {
            val node = queue.removeFirst()
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }

            if (label != null && node.isClickable && rules.isHideControl(label)) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (rect.isEmpty) return false
                val key = keyOf(Bounds(rect.left, rect.top, rect.right, rect.bottom))
                if (wasPressed(key)) return false

                val pressed = runCatching {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)

                lastQuickPressAt = now
                rememberPress(key, now)
                if (pressed) {
                    CosmeticStatistics.record(
                        label,
                        Bounds(rect.left, rect.top, rect.right, rect.bottom),
                    )
                }
                return pressed
            }

            if (budget <= 0) break
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return false
    }

    /** [inner] sits entirely within [outer]. */
    private fun contains(outer: Bounds, inner: Bounds): Boolean =
        inner.left >= outer.left &&
            inner.top >= outer.top &&
            inner.right <= outer.right &&
            inner.bottom <= outer.bottom

    private fun keyOf(bounds: Bounds): String =
        "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"

    private fun wasPressed(key: String): Boolean = synchronized(hideAttempts) {
        hideAttempts.containsKey(key)
    }

    private fun rememberPress(key: String, at: Long) = synchronized(hideAttempts) {
        hideAttempts[key] = at
    }

    private fun prunePresses(now: Long) = synchronized(hideAttempts) {
        hideAttempts.entries.removeAll { now - it.value > HIDE_COOLDOWN_MS }
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
        const val NORMAL_DEBOUNCE_MS = 60L
        const val SLOW_DEBOUNCE_MS = 900L
        const val MAX_SCAN_LATENCY_MS = 250L
        const val SLOW_SCAN_MS = 80L

        /** Nodes visited by the fast path before it gives up and waits for a scan. */
        const val QUICK_SCAN_NODES = 240

        /** Minimum gap between fast-path walks; content events can be chatty. */
        const val QUICK_WALK_MIN_GAP_MS = 25L

        /** Minimum gap between fast-path presses, so a stuck button is not hammered. */
        const val QUICK_PRESS_MIN_GAP_MS = 250L

        /** Rescan after a fast-path press, to refresh the counter. */
        const val QUICK_RESCAN_MS = 250L

        /**
         * A button rectangle is not pressed again within this window. Short on
         * purpose: a new ad can occupy the same screen position as the previous
         * one, so a long window would silently skip it.
         */
        const val HIDE_COOLDOWN_MS = 2_000L
    }
}
