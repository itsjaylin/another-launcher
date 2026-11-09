package com.anotherlauncher

import com.anotherlauncher.ui.PagerMap
import org.junit.Assert.*
import org.junit.Test

class PagerMapTest {
    @Test
    fun totalPagesIncludesHubSpreadsAndLibrary() {
        // 1 spread -> Hub(0), Spread 1(1), Library(2) = 3 total pages
        assertEquals(3, PagerMap.totalPages(1))
        // 2 spreads -> Hub(0), Spread 1(1), Spread 2(2), Library(3) = 4 total pages
        assertEquals(4, PagerMap.totalPages(2))
        // 3 spreads -> Hub(0), Spreads 1..3(1..3), Library(4) = 5 total pages
        assertEquals(5, PagerMap.totalPages(3))
    }

    @Test
    fun spreadsCalculationRespectsPerSpreadGrouping() {
        // Single screen (perSpread = 1)
        assertEquals(1, PagerMap.spreads(1, 1))
        assertEquals(2, PagerMap.spreads(2, 1))
        assertEquals(3, PagerMap.spreads(3, 1))

        // Dual/Wide screen (perSpread = 2): Page 1 is on Spread 0 with Hub
        assertEquals(1, PagerMap.spreads(1, 2))
        assertEquals(2, PagerMap.spreads(2, 2))
        assertEquals(2, PagerMap.spreads(3, 2))
        assertEquals(3, PagerMap.spreads(4, 2))
        assertEquals(3, PagerMap.spreads(5, 2))
    }

    @Test
    fun totalPagesUnfoldedIncludesHomeSpreadsAndLibrary() {
        // 1 home spread (Hub + Page 1) + Library = 2 total pages
        assertEquals(2, PagerMap.totalPages(1, 2))
        // 2 home spreads (Hub + Page 1, Page 2) + Library = 3 total pages
        assertEquals(3, PagerMap.totalPages(2, 2))
        // 3 home spreads + Library = 4 total pages
        assertEquals(4, PagerMap.totalPages(3, 2))
    }

    @Test
    fun hubIdentificationAndKey() {
        assertTrue(PagerMap.isHub(0, 1))
        assertFalse(PagerMap.isHub(1, 1))
        assertFalse(PagerMap.isHub(2, 1))
        assertFalse(PagerMap.isHub(0, 2)) // On wide, Hub is part of Spread 0

        val pages = listOf("page-a", "page-b", "page-c")
        assertEquals(PagerMap.HUB_KEY, PagerMap.pageKey(pages, 0, 3, 1))
        assertEquals("page-a", PagerMap.pageKey(pages, 1, 3, 1))
        assertEquals("page-b", PagerMap.pageKey(pages, 2, 3, 1))
    }

    @Test
    fun libraryIdentificationAndKey() {
        val spreadCount = 3
        assertFalse(PagerMap.isLibrary(0, spreadCount, 1))
        assertFalse(PagerMap.isLibrary(1, spreadCount, 1))
        assertFalse(PagerMap.isLibrary(3, spreadCount, 1))
        assertTrue(PagerMap.isLibrary(4, spreadCount, 1)) // 4 = spreadCount + 1

        val pages = listOf("page-a", "page-b", "page-c")
        assertEquals(PagerMap.LIBRARY_KEY, PagerMap.pageKey(pages, 4, 3, 1))

        // Wide mode: library is at index spreadCount
        assertTrue(PagerMap.isLibrary(2, 2, 2))
        assertEquals(PagerMap.LIBRARY_KEY, PagerMap.pageKey(pages, 2, 2, 2))
    }

    @Test
    fun pageIndexMapping() {
        val pages = listOf("page-a", "page-b", "page-c")
        assertEquals(0, PagerMap.pageIndex(pages, PagerMap.HUB_KEY, 3, 1))
        assertEquals(1, PagerMap.pageIndex(pages, "page-a", 3, 1))
        assertEquals(2, PagerMap.pageIndex(pages, "page-b", 3, 1))
        assertEquals(3, PagerMap.pageIndex(pages, "page-c", 3, 1))
        assertEquals(4, PagerMap.pageIndex(pages, PagerMap.LIBRARY_KEY, 3, 1))
    }

    @Test
    fun pageIndexMappingUnfolded() {
        val pages = listOf("page-a", "page-b", "page-c")
        // Both Hub and Page 1 (page-a) are on Spread 0
        assertEquals(0, PagerMap.pageIndex(pages, PagerMap.HUB_KEY, 2, 2))
        assertEquals(0, PagerMap.pageIndex(pages, "page-a", 2, 2))
        // Page 2 (page-b) and Page 3 (page-c) are on Spread 1
        assertEquals(1, PagerMap.pageIndex(pages, "page-b", 2, 2))
        assertEquals(1, PagerMap.pageIndex(pages, "page-c", 2, 2))
        // Library is at index 2 (spreadCount)
        assertEquals(2, PagerMap.pageIndex(pages, PagerMap.LIBRARY_KEY, 2, 2))
    }

    @Test
    fun pageKeyUnfolded() {
        val pages = listOf("page-a", "page-b", "page-c")
        assertEquals("spread-hub-page-a", PagerMap.pageKey(pages, 0, 2, 2))
        assertEquals("page-b", PagerMap.pageKey(pages, 1, 2, 2))
        assertEquals(PagerMap.LIBRARY_KEY, PagerMap.pageKey(pages, 2, 2, 2))
    }

    @Test
    fun homeSpreadValidation() {
        val spreadCount = 3
        assertFalse(PagerMap.isHomeSpread(0, spreadCount, 1))
        assertTrue(PagerMap.isHomeSpread(1, spreadCount, 1))
        assertTrue(PagerMap.isHomeSpread(2, spreadCount, 1))
        assertTrue(PagerMap.isHomeSpread(3, spreadCount, 1))
        assertFalse(PagerMap.isHomeSpread(4, spreadCount, 1))

        // Wide mode: home spreads are 0 until spreadCount
        assertTrue(PagerMap.isHomeSpread(0, 2, 2))
        assertTrue(PagerMap.isHomeSpread(1, 2, 2))
        assertFalse(PagerMap.isHomeSpread(2, 2, 2))
    }
}
