package com.anotherlauncher.data

enum class AppCategory(val title: String) {
    SOCIAL("Social"), BROWSERS("Browsers"), MEDIA("Media & photos"), GAMES("Games"),
    PRODUCTIVITY("Productivity"), NEWS("Reading"), MAPS("Travel"), TOOLS("Utilities"),
    CREATIVITY("Photo & Video"), ENTERTAINMENT("Entertainment"), SHOPPING("Shopping & Food"),
    OTHER("Other");

    companion object {
        /** Android ApplicationInfo categories, followed by conservative package-token fallback. */
        fun classify(
            platformCategory: Int,
            packageName: String,
            label: String = "",
            isGame: Boolean = false
        ): AppCategory {
            if (isGame || platformCategory == 0) return GAMES

            val browserFamilies = listOf("com.android.chrome", "com.chrome.", "org.mozilla.firefox", "org.mozilla.fenix",
                "org.mozilla.focus", "com.sec.android.app.sbrowser", "com.brave.browser", "com.opera.", "com.vivaldi.browser")
            if (browserFamilies.any { prefix -> packageName == prefix || packageName.startsWith(if (prefix.endsWith('.')) prefix else "$prefix.") }) return BROWSERS

            return when (platformCategory) {
                0 -> GAMES
                1, 2, 3 -> MEDIA
                4 -> SOCIAL
                5 -> NEWS
                6 -> MAPS
                7 -> PRODUCTIVITY
                8 -> TOOLS
                else -> {
                    val pkgTokens = packageName.lowercase(java.util.Locale.ROOT).split('.', '_').toSet()
                    val labelTokens = if (label.isNotBlank()) {
                        label.lowercase(java.util.Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
                    } else emptySet()

                    when {
                        pkgTokens.any { it in SOCIAL_TOKENS } || labelTokens.any { it in SOCIAL_TOKENS } -> SOCIAL
                        pkgTokens.any { it in MEDIA_TOKENS } || labelTokens.any { it in MEDIA_TOKENS } -> MEDIA
                        pkgTokens.any { it in GAMES_TOKENS } || labelTokens.any { it in GAMES_TOKENS } -> GAMES
                        pkgTokens.any { it in PRODUCTIVITY_TOKENS } || labelTokens.any { it in PRODUCTIVITY_TOKENS } -> PRODUCTIVITY
                        pkgTokens.any { it in NEWS_TOKENS } || labelTokens.any { it in NEWS_TOKENS } -> NEWS
                        pkgTokens.any { it in MAPS_TOKENS } || labelTokens.any { it in MAPS_TOKENS } -> MAPS
                        pkgTokens.any { it in SHOPPING_TOKENS } || labelTokens.any { it in SHOPPING_TOKENS } -> SHOPPING
                        pkgTokens.any { it in TOOLS_TOKENS } || labelTokens.any { it in TOOLS_TOKENS } -> TOOLS
                        else -> OTHER
                    }
                }
            }
        }

        /** Exact tokens only: "camerashop" must stay Other. Manual overrides win at the UI layer. */
        private val SOCIAL_TOKENS = setOf(
            "dialer", "contacts", "messaging", "messages", "mms", "sms", "chat",
            "whatsapp", "telegram", "signal", "discord", "instagram", "facebook", "katana", "orca", "twitter",
            "snapchat", "tiktok", "reddit", "pinterest", "linkedin", "messenger", "wechat", "viber", "line",
            "tumblr", "threads", "mastodon", "bluesky", "skype", "vk", "vkontakte", "weibo", "qq", "social",
            "bereal", "tinder", "bumble", "hinge"
        )
        private val MEDIA_TOKENS = setOf(
            "camera", "gallery", "photos", "photo", "music", "audio", "video",
            "youtube", "spotify", "netflix", "vlc", "podcast", "podcasts", "soundcloud", "tv", "disney", "hulu",
            "primevideo", "deezer", "tunein", "shazam", "media", "stream", "plex", "crunchyroll", "max",
            "peacock", "paramount", "audible", "tidal", "pandora", "snapseed", "lightroom", "capcut", "canva", "picsart"
        )
        private val GAMES_TOKENS = setOf(
            "game", "games", "gaming", "play", "arcade", "puzzle", "roblox", "minecraft", "chess",
            "solitaire", "sudoku", "crossword", "clash", "brawl", "pokemon", "hearthstone", "pubg",
            "steam", "fortnite", "genshin", "racing", "race"
        )
        private val PRODUCTIVITY_TOKENS = setOf(
            "calendar", "email", "mail", "gmail", "gm", "notes", "note", "office",
            "docs", "drive", "onenote", "evernote", "todoist", "notion", "tasks", "dropbox", "sheets", "slides",
            "word", "excel", "powerpoint", "teams", "slack", "zoom", "meet", "keep", "reminder", "obsidian",
            "trello", "asana", "chatgpt", "claude", "perplexity", "gemini", "bank", "banking", "finance",
            "cash", "paypal", "venmo", "wallet", "pay", "turbotax", "creditkarma", "robinhood", "coinbase"
        )
        private val NEWS_TOKENS = setOf(
            "news", "reader", "feeds", "feedly", "pocket", "kindle", "books", "book",
            "magazine", "medium", "weather", "forecast", "radar", "duolingo", "babbel", "wiki", "wikipedia",
            "fitness", "fitbit", "strava"
        )
        private val MAPS_TOKENS = setOf(
            "maps", "navigation", "transit", "waze", "uber", "lyft", "booking",
            "airbnb", "citymapper", "hotel", "hotels", "expedia", "kayak", "tripadvisor", "airline", "flight"
        )
        private val SHOPPING_TOKENS = setOf(
            "shop", "shopping", "store", "amazon", "ebay", "target", "walmart", "bestbuy", "costco",
            "etsy", "shein", "temu", "aliexpress", "doordash", "ubereats", "grubhub", "instacart", "postmates",
            "starbucks", "mcdonalds", "food", "delivery", "eats"
        )
        private val TOOLS_TOKENS = setOf(
            "settings", "calculator", "clock", "deskclock", "files", "filemanager",
            "file", "myfiles", "weather", "flashlight", "torch", "keyboard", "gboard", "launcher", "vault", "recorder",
            "scanner", "cleaner", "authenticator", "backup", "wifi", "anotherlauncher", "another launcher", "vpn", "speedtest", "translate"
        )
    }
}
