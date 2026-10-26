package com.sentinel.core.cosmetic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detector is the only part of the Facebook feature that can be verified
 * without a phone, so these tests encode the shapes observed in a real dump
 * (Redmi Note 8 Pro, Android 11, Facebook 4xx) plus the failure modes that
 * matter: a normal post must never be hidden, and a label split into pieces
 * must still be found.
 */
class SponsoredDetectorTest {

    private val screenHeight = 2134

    private val rules = SponsoredRules.parse(
        """
        # test configuration
        label=Ad
        label=Sponsored
        label=प्रायोजित
        """.trimIndent(),
    )

    private val detector = SponsoredDetector(rules)

    // Bounds copied from the real dump so the container heuristic is exercised
    // against the same geometry the device produced.
    private val labelBounds = Bounds(165, 520, 270, 559)
    private val headerBounds = Bounds(0, 424, 1080, 600)
    private val itemBounds = Bounds(0, 424, 1080, 2005)
    private val recyclerBounds = Bounds(0, 76, 1080, 2134)

    private fun feed(vararg posts: UiNode): UiNode = UiNode(
        className = "androidx.recyclerview.widget.RecyclerView",
        bounds = recyclerBounds,
        children = posts.toList(),
    )

    private fun post(label: UiNode?, container: Bounds = itemBounds): UiNode = UiNode(
        className = "android.view.ViewGroup",
        bounds = container,
        children = listOf(
            UiNode(
                className = "android.view.ViewGroup",
                bounds = headerBounds,
                children = listOfNotNull(label),
            ),
        ),
    )

    private fun labelNode(text: String) = UiNode(
        text = text,
        contentDescription = text,
        bounds = labelBounds,
        className = "android.view.ViewGroup",
    )

    @Test
    fun `normal post without a label is not hidden`() {
        val tree = feed(
            post(
                UiNode(
                    text = "Some friend",
                    bounds = Bounds(165, 457, 814, 509),
                    className = "android.widget.Button",
                ),
            ),
        )

        assertTrue(detector.findAds(tree, screenHeight).isEmpty())
    }

    @Test
    fun `ad label with a shared-with suffix is hidden as one post`() {
        val tree = feed(post(labelNode("Ad•Shared with: Public")))

        val matches = detector.findAds(tree, screenHeight)

        assertEquals(1, matches.size)
        assertEquals("Ad", matches.single().label)
        assertEquals(itemBounds, matches.single().container)
        assertEquals(labelBounds, matches.single().labelBounds)
    }

    @Test
    fun `plain sponsored label is hidden`() {
        val matches = detector.findAds(feed(post(labelNode("Sponsored"))), screenHeight)
        assertEquals(1, matches.size)
        assertEquals("Sponsored", matches.single().label)
    }

    @Test
    fun `label split across two child views is still found`() {
        val fragmented = UiNode(
            bounds = Bounds(165, 520, 400, 559),
            className = "android.view.ViewGroup",
            children = listOf(
                UiNode(text = "Spon", bounds = Bounds(165, 520, 260, 559)),
                UiNode(text = "sored", bounds = Bounds(262, 520, 400, 559)),
            ),
        )

        val matches = detector.findAds(feed(post(fragmented)), screenHeight)

        assertEquals(1, matches.size)
        assertEquals("Sponsored", matches.single().label)
    }

    @Test
    fun `full-width look-alike label is normalised and found`() {
        val matches = detector.findAds(feed(post(labelNode("Ｓｐｏｎｓｏｒｅｄ"))), screenHeight)
        assertEquals(1, matches.size)
    }

    @Test
    fun `zero width characters inside the label do not defeat matching`() {
        val matches = detector.findAds(feed(post(labelNode("Spon\u200Bsored"))), screenHeight)
        assertEquals(1, matches.size)
    }

    @Test
    fun `localised label from the config is found`() {
        val matches = detector.findAds(feed(post(labelNode("प्रायोजित•सार्वजनिक"))), screenHeight)
        assertEquals(1, matches.size)
        assertEquals("प्रायोजित", matches.single().label)
    }

    @Test
    fun `caption starting with the word ad is not a label`() {
        val caption = UiNode(
            text = "Ad revenue grew 20% this quarter",
            bounds = Bounds(33, 600, 1047, 700),
            className = "android.view.ViewGroup",
        )

        val tree = feed(
            UiNode(
                className = "android.view.ViewGroup",
                bounds = itemBounds,
                children = listOf(
                    UiNode(
                        className = "android.view.ViewGroup",
                        bounds = headerBounds,
                        children = listOf(caption),
                    ),
                ),
            ),
        )

        assertTrue(detector.findAds(tree, screenHeight).isEmpty())
    }

    @Test
    fun `word that merely starts with ad is not a label`() {
        assertTrue(detector.findAds(feed(post(labelNode("Advertisement"))), screenHeight).isEmpty())
    }

    @Test
    fun `tall node containing the label word is rejected`() {
        val tall = UiNode(
            text = "Ad",
            bounds = Bounds(0, 424, 1080, 1400),
            className = "android.view.ViewGroup",
        )

        assertTrue(detector.findAds(feed(post(tall)), screenHeight).isEmpty())
    }

    @Test
    fun `two ads produce two covers`() {
        val second = Bounds(0, 2005, 1080, 3600)
        val tree = UiNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            bounds = recyclerBounds,
            children = listOf(
                post(labelNode("Ad•Shared with: Public")),
                post(labelNode("Sponsored"), container = second),
            ),
        )

        val matches = detector.findAds(tree, screenHeight)
        assertEquals(2, matches.size)
        assertEquals(listOf(itemBounds, second), matches.map { it.container })
    }

    @Test
    fun `duplicate labels in one post produce one cover`() {
        val duplicate = UiNode(
            className = "android.view.ViewGroup",
            bounds = itemBounds,
            children = listOf(
                UiNode(
                    className = "android.view.ViewGroup",
                    bounds = headerBounds,
                    children = listOf(
                        labelNode("Ad•Shared with: Public"),
                        labelNode("Sponsored"),
                    ),
                ),
            ),
        )

        assertEquals(1, detector.findAds(feed(duplicate), screenHeight).size)
    }

    @Test
    fun `empty tree yields nothing`() {
        assertTrue(detector.findAds(UiNode(), screenHeight).isEmpty())
    }

    @Test
    fun `zero screen height yields nothing`() {
        assertTrue(detector.findAds(feed(post(labelNode("Ad"))), 0).isEmpty())
    }

    @Test
    fun `rules without labels disable detection`() {
        val empty = SponsoredDetector(SponsoredRules.EMPTY)
        assertTrue(empty.findAds(feed(post(labelNode("Ad"))), screenHeight).isEmpty())
    }

    @Test
    fun `container falls back to the largest ancestor when no list is present`() {
        val tree = UiNode(
            className = "android.widget.FrameLayout",
            bounds = Bounds(0, 0, 1080, 2134),
            children = listOf(post(labelNode("Ad"))),
        )

        val matches = detector.findAds(tree, screenHeight)
        assertEquals(1, matches.size)
        assertEquals(itemBounds, matches.single().container)
    }

    @Test
    fun `rules file without labels is not usable`() {
        val parsed = SponsoredRules.parse("# only comments\nmax_label_fraction=0.1")
        assertTrue(parsed.labels.isEmpty())
        assertTrue(!parsed.isUsable)
    }

    @Test
    fun `rules parser accepts labels and ignores malformed lines`() {
        val parsed = SponsoredRules.parse(
            """
            label=Ad
            label =  Sponsored
            nonsense
            max_label_fraction=not-a-number
            """.trimIndent(),
        )

        assertEquals(setOf("ad", "sponsored"), parsed.labels)
        assertEquals(0.06, parsed.maxLabelFraction, 0.0001)
    }
}
