package com.anotherlauncher.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anotherlauncher.data.AppEntry
import com.anotherlauncher.data.AppRepository

/** iOS-style App Library categories derived from package manager metadata and curated heuristics. */
enum class LibraryCategory(val title: String) {
    SUGGESTIONS("Suggestions"),
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity & Finance"),
    CREATIVITY("Photo & Video"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    INFO("Information & Reading"),
    TRAVEL("Travel & Maps"),
    SHOPPING("Shopping & Food"),
    UTILITIES("Utilities"),
    OTHER("Other");

    companion object {
        fun fromAppCategory(appCategory: com.anotherlauncher.data.AppCategory): LibraryCategory = when (appCategory) {
            com.anotherlauncher.data.AppCategory.SOCIAL -> SOCIAL
            com.anotherlauncher.data.AppCategory.BROWSERS -> UTILITIES
            com.anotherlauncher.data.AppCategory.MEDIA -> ENTERTAINMENT
            com.anotherlauncher.data.AppCategory.CREATIVITY -> CREATIVITY
            com.anotherlauncher.data.AppCategory.ENTERTAINMENT -> ENTERTAINMENT
            com.anotherlauncher.data.AppCategory.GAMES -> GAMES
            com.anotherlauncher.data.AppCategory.PRODUCTIVITY -> PRODUCTIVITY
            com.anotherlauncher.data.AppCategory.NEWS -> INFO
            com.anotherlauncher.data.AppCategory.MAPS -> TRAVEL
            com.anotherlauncher.data.AppCategory.SHOPPING -> SHOPPING
            com.anotherlauncher.data.AppCategory.TOOLS -> UTILITIES
            com.anotherlauncher.data.AppCategory.OTHER -> OTHER
        }

        fun of(
            packageName: String,
            platformCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
            appLabel: String = "",
            isGame: Boolean = false,
            pm: PackageManager? = null
        ): LibraryCategory {
            val p0 = packageName.lowercase(java.util.Locale.ROOT)
            val l0 = appLabel.lowercase(java.util.Locale.ROOT)

            // 1. Browsers & Core System Tools always Utilities
            if (hint(p0, "chrome", "firefox", "browser", "opera", "brave", "edge", "duckduckgo", "settings", "myfiles", "files",
                    "clock", "calculator", "contacts", "dialer", "vending", "authenticator", "vpn")) return UTILITIES

            // 2. Hardware / OS game flags
            if (isGame) return GAMES

            // 3. ApplicationInfo declared category
            val declared = if (pm != null) {
                runCatching { pm.getApplicationInfo(packageName, 0).category }.getOrDefault(platformCategory)
            } else platformCategory

            when (declared) {
                ApplicationInfo.CATEGORY_GAME -> return GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return PRODUCTIVITY
                ApplicationInfo.CATEGORY_AUDIO -> return ENTERTAINMENT
                ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO -> {
                    return if (hint(p0, "youtube", "netflix", "tv", "hulu", "disney", "twitch", "stream", "video", "player") ||
                        hint(l0, "youtube", "netflix", "tv", "hulu", "disney", "twitch", "stream", "video player")) {
                        ENTERTAINMENT
                    } else {
                        CREATIVITY
                    }
                }
                ApplicationInfo.CATEGORY_NEWS -> return INFO
                ApplicationInfo.CATEGORY_MAPS -> return TRAVEL
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> return UTILITIES
            }

            // 4. Known package registries & publisher signatures
            if (isSocialApp(p0, l0)) return SOCIAL
            if (isProductivityOrFinanceApp(p0, l0)) return PRODUCTIVITY
            if (isCreativityApp(p0, l0)) return CREATIVITY
            if (isEntertainmentApp(p0, l0)) return ENTERTAINMENT
            if (isGameApp(p0, l0)) return GAMES
            if (isInfoOrReadingApp(p0, l0)) return INFO
            if (isShoppingOrFoodApp(p0, l0)) return SHOPPING
            if (isTravelApp(p0, l0)) return TRAVEL
            if (isUtilitiesApp(p0, l0)) return UTILITIES

            // 5. Semantic token analysis on label & package tokens
            val labelWords = if (l0.isNotBlank()) l0.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet() else emptySet()
            val pkgTokens = p0.split('.', '_').toSet()
            val allWords = labelWords + pkgTokens

            return when {
                allWords.any { it in SOCIAL_WORDS } -> SOCIAL
                allWords.any { it in PRODUCTIVITY_WORDS } -> PRODUCTIVITY
                allWords.any { it in CREATIVITY_WORDS } -> CREATIVITY
                allWords.any { it in ENTERTAINMENT_WORDS } -> ENTERTAINMENT
                allWords.any { it in GAME_WORDS } -> GAMES
                allWords.any { it in INFO_WORDS } -> INFO
                allWords.any { it in SHOPPING_WORDS } -> SHOPPING
                allWords.any { it in TRAVEL_WORDS } -> TRAVEL
                allWords.any { it in UTILITIES_WORDS } -> UTILITIES
                else -> OTHER
            }
        }

        private fun hint(p: String, vararg words: String) = words.any { p.contains(it) }

        private fun isSocialApp(p: String, l: String): Boolean =
            hint(p, "discord", "twitter", "instagram", "facebook", "whatsapp", "telegram", "snapchat",
                "reddit", "linkedin", "tiktok", "threads", "messenger", "signal", "teams", "slack",
                "katana", "orca", "trill", "musically", "bereal", "tumblr", "bluesky", "mastodon",
                "viber", "jp.naver.line", ".line.", "wechat", "skype", "dating", "tinder", "bumble", "hinge") ||
            hint(l, "discord", "twitter", "tweet", "instagram", "facebook", "whatsapp", "telegram", "snapchat",
                "reddit", "linkedin", "tiktok", "threads", "messenger", "signal", "bereal", "tumblr", "bluesky",
                "mastodon", "viber", "wechat", "tinder", "bumble", "hinge")

        private fun isProductivityOrFinanceApp(p: String, l: String): Boolean =
            hint(p, "mail", "email", "gmail", ".gm", "calendar", "office", "docs", "sheets", "slides",
                "notes", "drive", "bank", "pay", "wallet", "finance", "cash", "venmo", "paypal",
                "chatgpt", "claude", "perplexity", "gemini", "bard", "copilot", "notion", "trello",
                "asana", "todoist", "evernote", "obsidian", "keep", "reminder", "tasks", "chase",
                "wellsfargo", "bofa", "citi", "capitalone", "discover", "robinhood", "coinbase",
                "turbotax", "creditkarma", "quickbooks", "spay", "fidelity", "vanguard", "schwab") ||
            hint(l, "mail", "email", "gmail", "outlook", "calendar", "office", "word", "excel", "powerpoint",
                "docs", "sheets", "slides", "notes", "drive", "dropbox", "onedrive", "bank", "banking", "finance",
                "cash app", "venmo", "paypal", "wallet", "chatgpt", "claude", "perplexity", "gemini",
                "notion", "trello", "asana", "todoist", "evernote", "obsidian", "keep notes", "reminder",
                "tasks", "chase", "wells fargo", "bank of america", "citi", "capital one", "discover",
                "robinhood", "coinbase", "turbotax", "credit karma", "quickbooks", "fidelity", "vanguard", "schwab")

        private fun isEntertainmentApp(p: String, l: String): Boolean =
            hint(p, "spotify", "music", "youtube", "netflix", "hulu", "disney", "twitch", "podcast", "podcasts",
                "audible", ".tv.", "tv.", ".tv", "vlc", "videolan", "plexapp", ".plex.", "tv.plex", "crunchyroll", "max", "hbo", "peacock", "paramount",
                "primevideo", "deezer", "tunein", "shazam", "tidal", "pandora", "soundcloud", "sirius", "iheart",
                "stream", "cinema", "movie", "google.android.videos") ||
            hint(l, "spotify", "music", "youtube", "netflix", "hulu", "disney", "twitch", "podcast", "podcasts",
                "audible", "tv", "vlc", "plex", "crunchyroll", "max", "hbo", "peacock", "paramount", "prime video",
                "deezer", "tunein", "shazam", "tidal", "pandora", "soundcloud", "radio", "streaming", "movies")

        private fun isCreativityApp(p: String, l: String): Boolean =
            hint(p, "camera", "gallery", "photo", "photos", "lightroom", "lrmobile", "psmobile", "snapseed", "video", "capcut",
                "canva", "picsart", "vsco", "inshot", "kinemaster", "ibispaint", "painter", "krita", "photoshop") ||
            hint(l, "camera", "gallery", "photo", "photos", "lightroom", "snapseed", "capcut", "canva",
                "picsart", "vsco", "inshot", "kinemaster", "drawing", "paint", "sketch", "photoshop", "editor")

        private fun isGameApp(p: String, l: String): Boolean =
            hint(p, "game", "games", "gaming", "roblox", "minecraft", "chess", "supercell", "clash", "brawl",
                "king.candycrush", "niantic", "pokemongo", "pokemon", "activision", "callofduty", "blizzard",
                "hearthstone", "tencent.ig", "pubg", "valvesoftware", "steam", "innersloth", "spacemafia",
                "mihoyo", "hoyoverse", "genshin", "subwaysurf", "fruitninja", "templerun", "ea.gp.", "rovio",
                "miniclip", "gameloft", "epicgames") ||
            hint(l, "game", "games", "roblox", "minecraft", "chess", "solitaire", "sudoku", "pokemon", "pokémon",
                "clash royale", "clash of clans", "brawl stars", "candy crush", "call of duty", "pubg", "steam",
                "among us", "genshin", "subway surfers", "temple run", "fruit ninja", "fortnite", "casino", "poker", "slots")

        private fun isInfoOrReadingApp(p: String, l: String): Boolean =
            hint(p, "news", "weather", "kindle", "books", "health", "fitness", "wiki", "wikipedia",
                "duolingo", "babbel", "strava", "fitbit", "myfitnesspal", "shealth", "medium", "feedly") ||
            hint(l, "news", "weather", "kindle", "books", "book", "reading", "reader", "health", "fitness",
                "wiki", "wikipedia", "duolingo", "babbel", "strava", "fitbit", "myfitnesspal", "workout", "forecast")

        private fun isShoppingOrFoodApp(p: String, l: String): Boolean =
            hint(p, "mshop", "shopping", "store", "ebay", "doordash", "ubereats", "eats", "grubhub", "food",
                "starbucks", "target", "walmart", "bestbuy", "costco", "samsclub", "instacart", "postmates",
                "mcdonald", "tacobell", "chipotle", "wendys", "chickfila", "dunkin", "dominos", "etsy",
                "shein", "temu", "aliexpress", "wish", "sephora", "ulta") ||
            (hint(p, "amazon") && !hint(p, "kindle", "mp3", "music", "avod", "primevideo", "alexa")) ||
            hint(l, "shop", "shopping", "store", "ebay", "doordash", "uber eats", "grubhub", "food",
                "starbucks", "target", "walmart", "best buy", "costco", "sam's club", "instacart", "postmates",
                "mcdonald's", "taco bell", "chipotle", "wendy's", "chick-fil-a", "dunkin", "domino's", "etsy",
                "shein", "temu", "aliexpress", "market", "delivery") ||
            (hint(l, "amazon") && !hint(l, "kindle", "music", "prime video", "alexa"))

        private fun isTravelApp(p: String, l: String): Boolean =
            (hint(p, "maps", "uber", "lyft", "airline", "travel", "transit", "waze", "airbnb", "booking",
                "expedia", "tripadvisor", "kayak", "hopper", "delta", "united", "southwest", "flight", "citymapper") &&
                !hint(p, "ubereats", "eats")) ||
            (hint(l, "maps", "navigation", "waze", "uber", "lyft", "travel", "trip", "airbnb", "booking.com",
                "expedia", "tripadvisor", "kayak", "hopper", "delta", "united airlines", "american airlines",
                "southwest", "flight", "flights", "hotel", "hotels", "transit", "citymapper") &&
                !hint(l, "uber eats"))

        private fun isUtilitiesApp(p: String, l: String): Boolean =
            hint(p, "settings", "files", "myfiles", "clock", "calculator", "contacts", "dialer", "phone",
                "messaging", "vending", "chrome", "browser", "firefox", "vpn", "authenticator", "speedtest",
                "translate", "scanner", "cleaner", "voicenote", "recorder", "anotherlauncher") ||
            hint(l, "settings", "files", "clock", "calculator", "contacts", "phone", "messages",
                "authenticator", "vpn", "speedtest", "translate", "voice recorder", "recorder", "cleaner")

        private val SOCIAL_WORDS = setOf("social", "chat", "messages", "messaging", "dating", "network", "community")
        private val PRODUCTIVITY_WORDS = setOf("productivity", "notes", "calendar", "mail", "office", "finance", "bank", "invest", "tax")
        private val ENTERTAINMENT_WORDS = setOf("entertainment", "music", "video", "movie", "tv", "audio", "radio", "stream")
        private val CREATIVITY_WORDS = setOf("photo", "camera", "draw", "paint", "art", "design", "editor", "collage")
        private val GAME_WORDS = setOf("game", "arcade", "puzzle", "quest", "rpg", "racing", "casino", "poker", "slots")
        private val TRAVEL_WORDS = setOf("travel", "maps", "navigation", "flight", "hotel", "transit", "taxi")
        private val SHOPPING_WORDS = setOf("shopping", "store", "deals", "coupon", "delivery", "food", "grocery", "restaurant")
        private val INFO_WORDS = setOf("news", "books", "weather", "reading", "wiki", "fitness", "health", "education")
        private val UTILITIES_WORDS = setOf("utilities", "tools", "calculator", "clock", "cleaner", "backup", "vpn", "security")
    }
}

/**
 * 2x2 App Library category tile:
 * - 3 large tap-to-launch icons
 * - 1 mini 2x2 cluster for overflow (or 4th large icon if <= 4 apps)
 * - Frosted glass surface using Another Launcher's design system
 */
@Composable
fun CategoryCard(
    title: String,
    apps: List<AppEntry>,
    repository: AppRepository,
    modifier: Modifier = Modifier,
    onLaunch: (AppEntry) -> Unit,
    onOpen: () -> Unit,
    onWallpaper: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .glass(24, frosted = true)
                .padding(10.dp)
        ) {
            val gap = 6.dp
            val cell = (maxWidth - gap) / 2
            val big = if (apps.size > 4) apps.take(3) else apps.take(4)
            val rest = if (apps.size > 4) apps.drop(3) else emptyList()

            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until 2) {
                            val index = row * 2 + col
                            when {
                                index < big.size -> {
                                    val app = big[index]
                                    Box(
                                        Modifier
                                            .size(cell)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onLaunch(app) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIcon(app, repository, Modifier.fillMaxSize(0.85f))
                                    }
                                }
                                index == 3 && rest.isNotEmpty() -> {
                                    Box(
                                        Modifier
                                            .size(cell)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = onOpen)
                                            .semantics { contentDescription = "Show all ${apps.size} $title apps" }
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val miniCell = (cell - 4.dp) / 2
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            rest.take(4).chunked(2).forEach { pair ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    pair.forEach { miniApp ->
                                                        AppIcon(miniApp, repository, Modifier.size(miniCell * 0.85f))
                                                    }
                                                    if (pair.size == 1) Spacer(Modifier.size(miniCell))
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> Spacer(Modifier.size(cell))
                            }
                        }
                    }
                }
            }
        }
        HomeLabel(
            text = title,
            onWallpaper = onWallpaper,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(onClick = onOpen)
        )
    }
}

/** Full-screen overlay sheet for expanding a category folder. */
@Composable
fun CategoryFolderOverlay(
    category: LibraryCategory,
    apps: List<AppEntry>,
    repository: AppRepository,
    drag: LauncherDragState,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isUnfolded = minOf(screenWidth, screenHeight) >= 600

    val maxCardWidth: Dp
    val cardWidthFraction: Float
    val maxCardHeight: Dp
    val folderCols: Int
    val tileIconSize: Dp
    val cardPaddingH: Dp

    when {
        isUnfolded && isLandscape -> {
            // Unfolded landscape: balanced width (not overly wide)
            maxCardWidth = 620.dp
            cardWidthFraction = 0.72f
            maxCardHeight = 520.dp
            folderCols = 5
            tileIconSize = 54.dp
            cardPaddingH = 20.dp
        }
        isUnfolded -> {
            // Unfolded portrait: wider and taller
            maxCardWidth = 600.dp
            cardWidthFraction = 0.88f
            maxCardHeight = 700.dp
            folderCols = 5
            tileIconSize = 56.dp
            cardPaddingH = 20.dp
        }
        isLandscape -> {
            // Folded landscape
            maxCardWidth = 540.dp
            cardWidthFraction = 0.85f
            maxCardHeight = 320.dp
            folderCols = 5
            tileIconSize = 48.dp
            cardPaddingH = 16.dp
        }
        else -> {
            // Folded portrait (compact cover screen)
            maxCardWidth = 395.dp
            cardWidthFraction = 0.88f
            maxCardHeight = 500.dp
            folderCols = if (screenWidth < 340) 3 else 4
            tileIconSize = 52.dp
            cardPaddingH = 18.dp
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                Modifier
                    .widthIn(max = maxCardWidth)
                    .fillMaxWidth(cardWidthFraction)
                    .heightIn(max = maxCardHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .glass(28, frosted = true),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = contentColor
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalHomeColors provides HomeColors(darkText = !isDark)
                ) {
                    Column(Modifier.padding(horizontal = cardPaddingH, vertical = 18.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                category.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                GlyphIcon(Glyph.CLOSE, tint = contentColor)
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(folderCols),
                            modifier = Modifier.weight(1f, fill = false).heightIn(min = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isUnfolded) 12.dp else 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (isUnfolded) 8.dp else 6.dp)
                        ) {
                            items(apps, key = { it.id }) { app ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AppTile(
                                        app = app,
                                        repository = repository,
                                        drag = drag,
                                        onClick = { onLaunch(app); onDismiss() },
                                        onMenu = { onMenu(app); onDismiss() },
                                        onWallpaper = false,
                                        iconSize = tileIconSize,
                                        modifier = Modifier.testTag("folder-app-tile")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
