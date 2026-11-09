package com.anotherlauncher

import com.anotherlauncher.data.PinOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PinOrderTest {
    private val ids = listOf("a", "b", "c", "d")
    @Test fun dragToBoundaryAccountsForOriginalSlot() {
        assertEquals(listOf("b", "a", "c", "d"), PinOrder.insert(ids, "a", 2, 6))
        assertEquals(listOf("a", "d", "b", "c"), PinOrder.insert(ids, "d", 1, 6))
    }
    @Test fun fullDockAllowsReorderButRejectsNewApp() {
        assertEquals(ids, PinOrder.insert(ids, "new", 0, 4))
        assertEquals(listOf("d", "a", "b", "c"), PinOrder.insert(ids, "d", 0, 4))
    }
    @Test fun repinningNeverDuplicatesAndCanAppend() {
        assertEquals(listOf("b", "c", "d", "a"), PinOrder.insert(ids, "a", Int.MAX_VALUE, 6))
        assertEquals(listOf("a"), PinOrder.insert(emptyList(), "a", Int.MAX_VALUE, 6))
    }
}
