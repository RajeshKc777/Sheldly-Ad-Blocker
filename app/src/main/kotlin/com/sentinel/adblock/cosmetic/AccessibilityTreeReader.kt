package com.sentinel.adblock.cosmetic

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.core.cosmetic.Bounds
import com.sentinel.core.cosmetic.UiNode

/**
 * Converts the live accessibility tree into the simplified tree the detector
 * understands.
 *
 * This is the only place that touches `AccessibilityNodeInfo`, so the detection
 * rules stay pure Kotlin. The walk is budgeted: a pathological or very deep
 * tree stops instead of burning CPU, and a partial tree simply means fewer
 * matches on that pass.
 */
object AccessibilityTreeReader {

    private const val MAX_NODES = 1_500
    private const val MAX_DEPTH = 40

    fun read(root: AccessibilityNodeInfo): UiNode? {
        var budget = MAX_NODES

        fun convert(node: AccessibilityNodeInfo, depth: Int): UiNode? {
            if (budget <= 0) return null
            budget--

            val rect = Rect()
            node.getBoundsInScreen(rect)

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
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                className = node.className?.toString(),
                children = children,
            )
        }

        return convert(root, 0)
    }
}
