package com.anotherlauncher

import com.anotherlauncher.data.SearchProvider
import org.junit.Assert.*
import org.junit.Test

class WebSearchTest {
    @Test fun eachProviderUsesItsOwnHttpsEndpointAndEncodesTheQuery() {
        val expectedHosts = mapOf(
            SearchProvider.GOOGLE to "www.google.com",
            SearchProvider.DUCKDUCKGO to "duckduckgo.com",
            SearchProvider.BRAVE to "search.brave.com",
            SearchProvider.STARTPAGE to "www.startpage.com",
        )
        expectedHosts.forEach { (provider, host) ->
            val url = provider.url("cats & dogs")!!
            assertTrue(url.startsWith("https://$host/"))
            assertTrue(url.contains("cats+%26+dogs"))
        }
    }

    @Test fun blankQueriesDoNotLaunchWebSearch() {
        SearchProvider.entries.forEach { assertNull(it.url("   ")) }
    }
}
