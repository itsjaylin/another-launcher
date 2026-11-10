package com.anotherlauncher.ui

import com.anotherlauncher.data.HomeLayout

/** Prefer the home page the user left; never reveal Hub/Library as an app drop destination. */
object LibraryDragNavigation {
    fun destination(layout: HomeLayout, lastHomePage: String?): String =
        lastHomePage?.takeIf { id -> layout.pages.any { it.id == id } } ?: layout.resolvedDefaultPageId()
}
