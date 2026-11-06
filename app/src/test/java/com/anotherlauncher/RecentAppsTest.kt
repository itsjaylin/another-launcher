package com.anotherlauncher

import com.anotherlauncher.data.RecentApps
import org.junit.Assert.*
import org.junit.Test

class RecentAppsTest {

    data class DummyApp(val id: String, val label: String)

    @Test
    fun computeRecentsPrioritizesRecordedRecentApps() {
        val appA = DummyApp("pkg.a", "App A")
        val appB = DummyApp("pkg.b", "App B")
        val appC = DummyApp("pkg.c", "App C")
        val appD = DummyApp("pkg.d", "App D")
        val appE = DummyApp("pkg.e", "App E")
        val all = listOf(appA, appB, appC, appD, appE)

        val recents = RecentApps.computeRecents(all, listOf("pkg.c", "pkg.a"), count = 4) { it.id }
        assertEquals(4, recents.size)
        assertEquals("pkg.c", recents[0].id)
        assertEquals("pkg.a", recents[1].id)
        assertEquals("pkg.b", recents[2].id)
        assertEquals("pkg.d", recents[3].id)
    }

    @Test
    fun computeRecentsHandlesSufficientRecordedApps() {
        val appA = DummyApp("pkg.a", "App A")
        val appB = DummyApp("pkg.b", "App B")
        val appC = DummyApp("pkg.c", "App C")
        val appD = DummyApp("pkg.d", "App D")
        val appE = DummyApp("pkg.e", "App E")
        val all = listOf(appA, appB, appC, appD, appE)

        val recents = RecentApps.computeRecents(all, listOf("pkg.e", "pkg.d", "pkg.c", "pkg.b"), count = 4) { it.id }
        assertEquals(listOf("pkg.e", "pkg.d", "pkg.c", "pkg.b"), recents.map { it.id })
    }

    @Test
    fun computeRecentsWithEmptyRecentsDefaultsToTopApps() {
        val appA = DummyApp("pkg.a", "App A")
        val appB = DummyApp("pkg.b", "App B")
        val appC = DummyApp("pkg.c", "App C")
        val appD = DummyApp("pkg.d", "App D")
        val all = listOf(appA, appB, appC, appD)

        val recents = RecentApps.computeRecents(all, emptyList(), count = 4) { it.id }
        assertEquals(listOf("pkg.a", "pkg.b", "pkg.c", "pkg.d"), recents.map { it.id })
    }

    @Test
    fun uninstalledAppsAreFilteredFromRecents() {
        val appA = DummyApp("pkg.a", "App A")
        val appB = DummyApp("pkg.b", "App B")
        val all = listOf(appA, appB)

        // "pkg.ghost" was uninstalled
        val recents = RecentApps.computeRecents(all, listOf("pkg.ghost", "pkg.b"), count = 4) { it.id }
        assertEquals("pkg.b", recents[0].id)
        assertEquals("pkg.a", recents[1].id)
    }

    @Test
    fun recordIdMovesAppToFrontAndEnforcesLimit() {
        var list = listOf("app1", "app2", "app3")
        list = RecentApps.recordId(list, "app2")
        assertEquals(listOf("app2", "app1", "app3"), list)

        list = RecentApps.recordId(list, "app4")
        assertEquals(listOf("app4", "app2", "app1", "app3"), list)

        // Enforce max limit of 3
        list = RecentApps.recordId(list, "app5", max = 3)
        assertEquals(listOf("app5", "app4", "app2"), list)

        // Blank ID is ignored
        assertEquals(list, RecentApps.recordId(list, "   "))
    }
}
