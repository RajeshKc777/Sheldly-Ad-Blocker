package com.sentinel.adblock.cosmetic

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.sentinel.core.cosmetic.Bounds
import kotlin.math.max
import kotlin.math.min

/**
 * Draws opaque covers over posts the detector matched.
 *
 * Why covers instead of scrolling the post away:
 *  - scrolling moves the user's reading position and can trigger the feed to
 *    load more content, which is a side effect on someone else's app;
 *  - covers are local, reversible, and send nothing to Facebook.
 *
 * Why an accessibility overlay: it is the only window type an
 * AccessibilityService may add without a separate "draw over other apps"
 * permission. It is *not* touch-transparent, so a tap on a hidden post does not
 * open the ad.
 *
 * The covers live in screen coordinates, not in the feed's scroll space. The
 * service therefore offsets them by the scroll delta as the feed moves, so an ad
 * that is already hidden stays hidden while scrolling, then a rescan confirms
 * the positions once the feed settles. That trade-off is documented in
 * docs/LIMITATIONS.md.
 */
class AdOverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val covers = ArrayList<View>()
    private var shown: List<Bounds> = emptyList()

    private val coverColor: Int =
        if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) {
            Color.parseColor("#FF242526")
        } else {
            Color.parseColor("#FFFFFFFF")
        }

    fun render(rects: List<Bounds>) {
        if (rects == shown) return
        // Same number of ads: move the existing windows instead of removing and
        // re-adding them, which would flash for a frame on every rescan.
        if (rects.size == covers.size && covers.size == shown.size) {
            shown = rects
            covers.forEachIndexed { index, view -> place(view, rects[index]) }
            return
        }
        clear()
        shown = rects
        for (rect in rects) add(rect)
    }

    val hasCovers: Boolean get() = covers.isNotEmpty()

    /**
     * Moves every cover by [deltaY] pixels without removing it.
     *
     * A cover is a window in screen space; the feed moves underneath it. Offset
     * tracking keeps an already-hidden ad hidden while the user scrolls, instead
     * of dropping the cover and exposing the ad until the next scan.
     */
    fun offsetBy(deltaY: Int) {
        if (deltaY == 0 || covers.isEmpty()) return
        shown = shown.map { bounds ->
            bounds.copy(top = bounds.top + deltaY, bottom = bounds.bottom + deltaY)
        }
        for (view in covers) {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: continue
            params.y += deltaY
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    fun clear() {
        for (view in covers) {
            runCatching { windowManager.removeView(view) }
        }
        covers.clear()
        shown = emptyList()
    }

    private fun add(rect: Bounds) {
        val params = paramsFor(rect) ?: return
        val cover = View(context).apply {
            // No text: the cover should read as empty feed space, not as a badge.
            setBackgroundColor(coverColor)
            isClickable = true
        }
        runCatching { windowManager.addView(cover, params) }.onSuccess { covers.add(cover) }
    }

    private fun place(view: View, rect: Bounds) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val screenHeight = context.resources.displayMetrics.heightPixels
        val top = max(0, rect.top)
        val height = min(rect.bottom, screenHeight) - top
        if (rect.width <= 0 || height <= 0) return
        params.x = max(0, rect.left)
        params.y = top
        params.width = rect.width
        params.height = height
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun paramsFor(rect: Bounds): WindowManager.LayoutParams? {
        val screenHeight = context.resources.displayMetrics.heightPixels
        val top = max(0, rect.top)
        val height = min(rect.bottom, screenHeight) - top
        if (rect.width <= 0 || height <= 0) return null
        return WindowManager.LayoutParams(
            rect.width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(0, rect.left)
            y = top
        }
    }
}
