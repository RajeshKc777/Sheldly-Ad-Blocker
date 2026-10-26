package com.sentinel.adblock.cosmetic

import com.sentinel.core.cosmetic.Bounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The counter is user-visible, so it must not inflate: the service re-scans
 * after every content change and would otherwise count the same post dozens of
 * times per minute.
 */
class CosmeticStatisticsTest {

    private val post = Bounds(0, 424, 1080, 2005)

    @Before
    fun setUp() = CosmeticStatistics.resetForTest()

    @After
    fun tearDown() = CosmeticStatistics.resetForTest()

    @Test
    fun `same post counted once`() {
        assertTrue(CosmeticStatistics.record("Ad", post))
        assertFalse(CosmeticStatistics.record("Ad", post))
        assertEquals(1L, CosmeticStatistics.hidden.value)
    }

    @Test
    fun `different posts are counted separately`() {
        CosmeticStatistics.record("Ad", post)
        CosmeticStatistics.record("Ad", Bounds(0, 2005, 1080, 3600))
        assertEquals(2L, CosmeticStatistics.hidden.value)
    }

    @Test
    fun `label is part of the identity`() {
        CosmeticStatistics.record("Ad", post)
        CosmeticStatistics.record("Sponsored", post)
        assertEquals(2L, CosmeticStatistics.hidden.value)
    }

    @Test
    fun `starts at zero and resets`() {
        assertEquals(0L, CosmeticStatistics.hidden.value)
        CosmeticStatistics.record("Ad", post)
        CosmeticStatistics.resetForTest()
        assertEquals(0L, CosmeticStatistics.hidden.value)
    }
}
