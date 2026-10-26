package com.sentinel.core.cosmetic

/**
 * Finds posts that Facebook has labelled as advertising.
 *
 * ## What "detection" means here
 *
 * Facebook renders its own label (`Ad`, `Sponsored`, …) as a small text node in
 * the post header. The detector looks for a node whose text *starts with* one
 * of the configured labels, then walks up to the nearest repeatable container —
 * normally the RecyclerView item, which is exactly one post — and returns its
 * bounds. It never looks at post content, never guesses from an image, and
 * never hides anything it cannot explain.
 *
 * ## Deliberate limits
 *
 * - A label must be its own segment (`Ad`, `Ad•Shared with: Public`,
 *   `Sponsored•…`). A caption that merely starts with the word "Ad" is not a
 *   match because the remaining text stays part of the same segment.
 * - The label node must be short (a fraction of the screen height). A tall
 *   node that happens to contain the word is not a label.
 * - Walking stops at a list container (`RecyclerView`/`ListView`) so the result
 *   is one post, not the whole feed.
 *
 * Everything above is why this class is pure Kotlin and has no Android import:
 * the rules are testable without a phone, and the service is only a thin
 * adapter from `AccessibilityNodeInfo` to [UiNode].
 */
class SponsoredDetector(private val rules: SponsoredRules = SponsoredRules.EMPTY) {

    data class AdMatch(
        /** The label text that matched, e.g. `Ad`. */
        val label: String,
        /** The post container to cover. */
        val container: Bounds,
        /** Where the label itself was found, for diagnostics and tests. */
        val labelBounds: Bounds,
    )

    /**
     * @param screenHeight height of the visible window in the same units as the
     *   node bounds (pixels). Used to reject implausibly tall "labels".
     */
    fun findAds(root: UiNode, screenHeight: Int): List<AdMatch> {
        if (!rules.isUsable || screenHeight <= 0) return emptyList()

        val maxLabelPx = (screenHeight * rules.maxLabelFraction).toInt()
        val maxJoinedPx = (screenHeight * rules.maxJoinedLabelFraction).toInt()
        val minContainerPx = (screenHeight * rules.minContainerFraction).toInt()
        val maxContainerPx = (screenHeight * rules.maxContainerFraction).toInt()

        val found = LinkedHashMap<String, AdMatch>()
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(root, emptyList()))
        var visited = 0

        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val frame = stack.removeLast()
            visited++
            val node = frame.node

            val label = labelOf(node, maxLabelPx, maxJoinedPx)
            if (label != null) {
                val container = containerFor(frame.ancestors, minContainerPx, maxContainerPx)
                if (container != null) {
                    found.putIfAbsent(
                        key(container),
                        AdMatch(label = label, container = container, labelBounds = node.bounds),
                    )
                }
            }

            if (frame.ancestors.size < MAX_DEPTH) {
                val next = frame.ancestors + node
                // Reversed push keeps traversal in document order (LIFO stack).
                for (child in node.children.asReversed()) stack.addLast(Frame(child, next))
            }
        }

        return found.values.toList()
    }

    private data class Frame(val node: UiNode, val ancestors: List<UiNode>)

    /** Returns the matched label segment, or `null`. */
    private fun labelOf(node: UiNode, maxLabelPx: Int, maxJoinedPx: Int): String? {
        val own = node.text ?: node.contentDescription
        if (own != null) {
            if (node.bounds.height > maxLabelPx) return null
            val segment = firstSegment(own)
            return if (segment != null && rules.isLabel(segment)) segment else null
        }

        // Fragmented label: the words are split across two or three child views.
        if (node.bounds.height > maxJoinedPx) return null
        val joined = joinedChildText(node) ?: return null
        val segment = firstSegment(joined)
        return if (segment != null && rules.isLabel(segment)) segment else null
    }

    /**
     * Text that starts a label, cut at the first configured separator.
     * Returns the original casing so callers can show what Facebook wrote.
     */
    private fun firstSegment(value: String): String? {
        var end = value.length
        for (separator in rules.separators) {
            val index = value.indexOf(separator)
            if (index in 0 until end) end = index
        }
        val segment = value.substring(0, end).trim().trimEnd('.', ':', ',', ';', '-', '–')
        return segment.takeIf { it.isNotEmpty() }
    }

    /**
     * Concatentation of a few text-only children. Only used when the parent has
     * no text of its own, which is how a split label looks in a node dump.
     */
    private fun joinedChildText(node: UiNode): String? {
        if (node.children.size !in 2..4) return null
        if (node.children.any { it.children.isNotEmpty() }) return null
        val builder = StringBuilder(32)
        for (child in node.children) {
            builder.append(child.text ?: child.contentDescription ?: "")
        }
        if (builder.isEmpty() || builder.length > MAX_JOINED_LENGTH) return null
        return builder.toString()
    }

    /** Largest ancestor that is still one post, stopping at the list itself. */
    private fun containerFor(ancestors: List<UiNode>, minPx: Int, maxPx: Int): Bounds? {
        var best: Bounds? = null
        for (ancestor in ancestors.asReversed()) {
            if (isListContainer(ancestor.className)) break
            val bounds = ancestor.bounds
            if (bounds.isEmpty) continue
            if (bounds.height > maxPx) break
            if (bounds.height >= minPx && (best == null || bounds.height > best.height)) {
                best = bounds
            }
        }
        return best
    }

    private fun isListContainer(className: String?): Boolean {
        if (className == null) return false
        return className.contains("RecyclerView") ||
            className.contains("ListView") ||
            className.contains("ViewPager")
    }

    private fun key(bounds: Bounds): String =
        "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"

    private companion object {
        const val MAX_NODES = 4_000
        const val MAX_DEPTH = 60
        const val MAX_JOINED_LENGTH = 32
    }
}
