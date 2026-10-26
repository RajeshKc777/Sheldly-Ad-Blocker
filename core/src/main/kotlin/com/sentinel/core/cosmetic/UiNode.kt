package com.sentinel.core.cosmetic

/**
 * A screen rectangle in the coordinate space the caller supplies (normally
 * screen pixels). Pure Kotlin on purpose: the detector must be unit-testable
 * without Android, a device, or an emulator.
 */
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(other: Bounds): Boolean =
        other.left >= left && other.top >= top &&
            other.right <= right && other.bottom <= bottom

    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/**
 * The only view of the screen the detector is allowed to see.
 *
 * It is a *simplified* tree, not an Android one: text, an accessibility label,
 * bounds, a class name, and children. That keeps the detection logic free of
 * framework types and makes it trivial to feed hand-written trees in tests.
 */
data class UiNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: Bounds = Bounds.EMPTY,
    val className: String? = null,
    val children: List<UiNode> = emptyList(),
)
