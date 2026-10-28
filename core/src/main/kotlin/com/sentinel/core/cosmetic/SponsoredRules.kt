package com.sentinel.core.cosmetic

import java.text.Normalizer
import java.util.Locale

/**
 * Replaceable detection rules.
 *
 * Facebook renames and re-localises its ad label regularly, and the label was
 * already found to be `Ad`, not `Sponsored`, on the tested build (see
 * docs/TESTING.md). Keeping the tokens in a text file means the next change is
 * an asset edit, not a code change.
 *
 * Format: one `key=value` per line, `#` starts a comment:
 *
 * ```
 * label=Ad
 * label=Sponsored
 * label=Gesponsert
 * separator=•
 * max_label_fraction=0.06
 * max_joined_label_fraction=0.12
 * min_container_fraction=0.15
 * max_container_fraction=0.95
 * hide_control=Hide Ad
 * ```
 *
 * All fractions are of the screen height. If the file is empty or unreadable
 * the label set is empty and the detector finds nothing: the failure mode is
 * "hide nothing", never "hide a post we did not understand".
 *
 * `hide_control` names the button Facebook itself puts on an ad post. When one
 * is found inside a matched post, the service presses it instead of covering
 * the post, so Facebook removes the ad and the feed closes the gap. Leaving the
 * key out means the app never presses anything.
 */
class SponsoredRules private constructor(
    val labels: Set<String>,
    val separators: List<String>,
    val maxLabelFraction: Double,
    val maxJoinedLabelFraction: Double,
    val minContainerFraction: Double,
    val maxContainerFraction: Double,
    val hideControls: Set<String>,
) {

    val isUsable: Boolean get() = labels.isNotEmpty()

    /** True when [segment] is one of the configured ad labels. */
    fun isLabel(segment: String): Boolean = normalize(segment) in labels

    /**
     * True when [value] is a control this app may press, e.g. Facebook's own
     * "Hide Ad" button. Compared after the same normalisation as labels, so
     * case and look-alike characters do not matter.
     */
    fun isHideControl(value: String): Boolean = normalize(value) in hideControls

    companion object {

        /** Stripped before comparison: invisible characters used to break naive matching. */
        private val INVISIBLE = charArrayOf('\u200B', '\u200C', '\u200D', '\uFEFF', '\u00AD')

        private val DEFAULT_SEPARATORS = listOf("•", "·", "|", "\n", "\t", " - ")

        val EMPTY = SponsoredRules(
            labels = emptySet(),
            separators = DEFAULT_SEPARATORS,
            maxLabelFraction = 0.06,
            maxJoinedLabelFraction = 0.12,
            minContainerFraction = 0.15,
            maxContainerFraction = 0.95,
            hideControls = emptySet(),
        )

        fun parse(text: String): SponsoredRules {
            val labels = LinkedHashSet<String>()
            val hideControls = LinkedHashSet<String>()
            var separators = DEFAULT_SEPARATORS
            var maxLabel = 0.06
            var maxJoined = 0.12
            var minContainer = 0.15
            var maxContainer = 0.95

            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
                val split = line.indexOf('=')
                if (split <= 0) continue
                val key = line.substring(0, split).trim().lowercase(Locale.ROOT)
                val value = line.substring(split + 1).trim()
                when (key) {
                    "label" -> normalize(value).takeIf { it.isNotEmpty() }?.let(labels::add)
                    "hide_control" -> normalize(value).takeIf { it.isNotEmpty() }?.let(hideControls::add)
                    "separator" -> if (value.isNotEmpty()) {
                        separators = separators + value
                    }
                    "max_label_fraction" -> value.toDoubleOrNull()?.let { maxLabel = it }
                    "max_joined_label_fraction" -> value.toDoubleOrNull()?.let { maxJoined = it }
                    "min_container_fraction" -> value.toDoubleOrNull()?.let { minContainer = it }
                    "max_container_fraction" -> value.toDoubleOrNull()?.let { maxContainer = it }
                }
            }

            return SponsoredRules(
                labels = labels,
                separators = separators,
                maxLabelFraction = maxLabel,
                maxJoinedLabelFraction = maxJoined,
                minContainerFraction = minContainer,
                maxContainerFraction = maxContainer,
                hideControls = hideControls,
            )
        }

        /**
         * NFKC folds full-width look-alikes (`Ｓｐｏｎｓｏｒｅｄ`) into ASCII,
         * invisible characters are removed, whitespace is collapsed, and the
         * result is lower-cased.
         */
        fun normalize(value: String): String {
            var working = value
            if (working.any { it in INVISIBLE }) {
                val builder = StringBuilder(working.length)
                for (ch in working) if (ch !in INVISIBLE) builder.append(ch)
                working = builder.toString()
            }
            working = Normalizer.normalize(working, Normalizer.Form.NFKC)
            working = working.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ')
            working = working.replace(WHITESPACE, " ").trim()
            return working.lowercase(Locale.ROOT)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
