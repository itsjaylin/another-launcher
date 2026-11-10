package com.anotherlauncher.ui

/** One foreground surface; only Pages and Wallpaper can return to Settings. */
enum class HomeSurface { HOME, SETTINGS, WIDGETS, PAGES, WEATHER, WALLPAPER }

data class HomeNavigation(
    val surface: HomeSurface = HomeSurface.HOME,
    val returnToSettings: Boolean = false,
    val replacementItemId: String? = null,
) {
    init {
        require(!returnToSettings || surface == HomeSurface.PAGES || surface == HomeSurface.WALLPAPER)
        require(replacementItemId == null || surface == HomeSurface.WIDGETS)
    }

    val fullPage: Boolean get() = surface != HomeSurface.HOME
    val showsSettings: Boolean get() = surface == HomeSurface.SETTINGS ||
        (surface == HomeSurface.WALLPAPER && returnToSettings)

    fun open(next: HomeSurface, replacement: String? = null): HomeNavigation = HomeNavigation(
        next,
        returnToSettings = (next == HomeSurface.PAGES || next == HomeSurface.WALLPAPER) && showsSettings,
        replacementItemId = replacement,
    )

    fun dismiss(): HomeNavigation = HomeNavigation(if (returnToSettings) HomeSurface.SETTINGS else HomeSurface.HOME)
}
