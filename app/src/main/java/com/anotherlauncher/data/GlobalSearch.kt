package com.anotherlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.ContactsContract

data class ContactMatch(val id: Long, val name: String, val lookupKey: String) {
    fun uri(): Uri = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

/** People results for global search. Requires READ_CONTACTS; an empty list is always valid. */
object ContactSearch {
    fun query(context: Context, term: String, limit: Int = 8): List<ContactMatch> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(trimmed))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.LOOKUP_KEY,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val name = cursor.getString(1)?.takeIf(String::isNotBlank) ?: continue
                        add(ContactMatch(cursor.getLong(0), name, cursor.getString(2) ?: ""))
                    }
                }
            } ?: emptyList()
        } catch (_: SecurityException) { emptyList() }
        catch (_: IllegalArgumentException) { emptyList() }
    }
}

/** The chosen web provider only affects web actions; local results remain in the launcher. */
enum class SearchProvider(val label: String, private val baseUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave", "https://search.brave.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=");

    fun url(term: String): String? = term.trim().takeIf(String::isNotEmpty)?.let {
        baseUrl + java.net.URLEncoder.encode(it, "UTF-8")
    }
}

object WebSearch {
    fun intent(term: String, provider: SearchProvider = SearchProvider.GOOGLE): Intent? =
        provider.url(term)?.let { url ->
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/** Installed-app fallback for App Library search. This never opens a browser. */
object PlayStoreSearch {
    fun intent(context: Context, term: String): Intent? {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(trimmed)}"))
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
        } catch (_: Exception) { null }
    }
}
