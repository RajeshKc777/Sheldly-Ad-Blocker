package com.sentinel.adblock.cosmetic

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.core.cosmetic.Bounds
import com.sentinel.core.cosmetic.SponsoredRules
import com.sentinel.core.cosmetic.UiNode

/**
 * Converts the live accessibility tree into the simplified tree the detector
 * understands, and collects the buttons the service is allowed to press.
 *
 * This is the only place that touches `AccessibilityNodeInfo`, so the detection
 * rules stay pure Kotlin. The walk is budgeted: a pathological or very deep
 * tree stops instead of burning CPU, and a partial tree simply means fewer
 * matches on that pass.
 *
 * The collected [HideControl] handles point at the live tree and are only valid
 * for the scan that produced them. They never leave the worker thread.
 */
object AccessibilityTreeReader {

    private const val MAX_NODES = 1_500
    private const val MAX_DEPTH = 40

    data class Snapshot(
        val tree: UiNode,
        val hideControls: List<HideControl>,
    )

    /**
     * A pressable control found in the tree, e.g. the `Hide Ad` button Facebook
     * renders on a sponsored post.
     */
    data class HideControl(
        val label: String,
        val bounds: Bounds,
        val node: AccessibilityNodeInfo,
    )

    fun read(root: AccessibilityNodeInfo, rules: SponsoredRules): Snapshot? {
        var budget = MAX_NODES
        val hideControls = ArrayList<HideControl>(2)

        fun convert(node: AccessibilityNodeInfo, depth: Int): UiNode? {
            if (budget <= 0) return null
            budget--

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom)

            // Facebook exposes one direct "Hide Ad" button per sponsored post,
            // so this list is normally 0-2 entries. Requiring `isClickable`
            // keeps the affordance honest: Shieldly only presses things the
            // user could press themselves.
            val own = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (own != null && !bounds.isEmpty && node.isClickable && rules.isHideControl(own)) {
                hideControls.add(HideControl(own, bounds, node))
            }

            val children = if (depth < MAX_DEPTH && node.childCount > 0 && budget > 0) {
                val list = ArrayList<UiNode>(node.childCount)
                for (index in 0 until node.childCount) {
                    if (budget <= 0) break
                    val child = node.getChild(index) ?: continue
                    convert(child, depth + 1)?.let(list::add)
                }
                list
            } else {
                emptyList()
            }

            return UiNode(
                text = node.text?.toString()?.takeIf { it.isNotBlank() },
                contentDescription = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
                bounds = bounds,
                className = node.className?.toString(),
                children = children,
            )
        }

        val tree = convert(root, 0) ?: return null
        return Snapshot(tree = tree, hideControls = hideControls)
    }
}
