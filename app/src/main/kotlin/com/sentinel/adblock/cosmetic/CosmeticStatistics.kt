package com.sentinel.adblock.cosmetic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Session counter for on-screen hiding.
 *
 * Kept separate from the DNS counters on purpose: a hidden Facebook post and a
 * blocked DNS name are different events with different guarantees, and merging
 * them would let one number imply the other. Nothing here is persisted, so a
 * force-stop erases it.
 *
 * The dedupe set stores a label plus a rectangle, never post text, author
 * names, or anything else read from the screen.
 */
object CosmeticStatistics {

    private const val MAX_TRACKED = 400

    private val seen = LinkedHashSet<String>()

    private val _hidden = MutableStateFlow(0L)

    /** Number of distinct sponsored posts hidden in this service session. */
    val hidden: StateFlow<Long> = _hidden.asStateFlow()

    /**
     * Records a post that was hidden. Returns `false` when the same post was
     * already counted, which is the common case: the scan runs again after every
     * content change.
     */
    fun record(label: String, bounds: com.sentinel.core.cosmetic.Bounds): Boolean {
        val key = "$label:${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        if (!seen.add(key)) return false
        if (seen.size > MAX_TRACKED) {
            seen.clear()
            seen.add(key)
        }
        _hidden.update { it + 1 }
        return true
    }

    /** Test-only hook. */
    internal fun resetForTest() {
        seen.clear()
        _hidden.value = 0L
    }
}
