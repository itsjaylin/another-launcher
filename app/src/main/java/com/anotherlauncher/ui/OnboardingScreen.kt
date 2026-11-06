package com.anotherlauncher.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import com.anotherlauncher.R
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anotherlauncher.LauncherViewModel
import com.anotherlauncher.data.WeatherLocation
import com.anotherlauncher.data.detectCurrentWeatherLocation
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.notificationDotsGranted
import kotlinx.coroutines.launch

private val SuccessGreen = Color(0xFF34C759)

@Composable
fun OnboardingScreen(
    model: LauncherViewModel,
    fold: FoldState,
    onComplete: () -> Unit,
    isTourMode: Boolean = false,
    onCloseTour: () -> Unit = onComplete
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isDark) textColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val tertiaryText = if (isDark) textColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    val pagerState = rememberPagerState(pageCount = { 5 })

    // Lifecycle observer to re-check permissions upon returning from system settings or dialogs
    var resumeTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission state checkers
    val isDefaultHome = remember(resumeTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            roles?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else false
    }

    val isNotificationGranted = remember(resumeTrigger) {
        context.notificationDotsGranted()
    }

    val isLocationGranted = remember(resumeTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val isContactsGranted = remember(resumeTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    val isSignalGranted = remember(resumeTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    // Permission launch handlers
    val homeRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resumeTrigger++
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resumeTrigger++
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        resumeTrigger++
        if (grants.values.any { it }) {
            model.autoDetectWeather(context)
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        resumeTrigger++
        if (granted) model.setContacts(true)
    }

    val signalPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTrigger++
    }

    var showPrivacyPolicy by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showPrivacyPolicy) {
            showPrivacyPolicy = false
        } else if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else if (isTourMode) {
            onCloseTour()
        }
    }

    // Scrim overlay over wallpaper
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    if (isTourMode) Color.Black.copy(alpha = 0.72f)
                    else Color.Black.copy(alpha = 0.65f)
                } else {
                    if (isTourMode) Color.White.copy(alpha = 0.82f)
                    else Color.White.copy(alpha = 0.75f)
                }
            )
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar / Skip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tour mode or step label
                Text(
                    text = if (isTourMode) "Another Launcher tour" else "Welcome",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tertiaryText
                )

                if (isTourMode) {
                    IconButton(onClick = onCloseTour) {
                        GlyphIcon(Glyph.CLOSE, tint = textColor, modifier = Modifier.size(18.dp))
                    }
                } else if (pagerState.currentPage < 4) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(3) }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Skip to setup",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage(textColor, secondaryText)
                    1 -> OnboardingArchitecturePage(textColor, secondaryText)
                    2 -> OnboardingGesturesPage(textColor, secondaryText)
                    3 -> OnboardingPermissionsPage(
                        textColor = textColor,
                        secondaryText = secondaryText,
                        isDefaultHome = isDefaultHome,
                        isNotificationGranted = isNotificationGranted,
                        isLocationGranted = isLocationGranted,
                        isContactsGranted = isContactsGranted,
                        isSignalGranted = isSignalGranted,
                        onRequestHome = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roles = context.getSystemService(RoleManager::class.java)
                                if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                                    homeRoleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            } else {
                                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onRequestNotification = {
                            if (!isNotificationGranted) {
                                try {
                                    notificationLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        },
                        onRequestLocation = {
                            if (!isLocationGranted) {
                                locationPermissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ))
                            }
                        },
                        onRequestContacts = {
                            if (!isContactsGranted) {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        onRequestSignal = {
                            if (!isSignalGranted) {
                                signalPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            }
                        },
                        onOpenPrivacyPolicy = {
                            showPrivacyPolicy = true
                        }
                    )
                    4 -> OnboardingReadyPage(
                        textColor = textColor,
                        secondaryText = secondaryText,
                        isTourMode = isTourMode,
                        onEnter = onComplete
                    )
                }
            }

            // Bottom Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        GlyphIcon(Glyph.BACK, tint = textColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Back",
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val active = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (active) 20.dp else 6.dp,
                            animationSpec = tween(250),
                            label = "dot_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.primary else tertiaryText,
                            animationSpec = tween(250),
                            label = "dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                        )
                    }
                }

                // Next or Finish Button
                if (pagerState.currentPage < 4) {
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 0) "Get started" else "Next",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        GlyphIcon(Glyph.CHEVRON_RIGHT, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isTourMode) "Done" else "Enter",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        GlyphIcon(Glyph.CHECK, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }
}

// -------------------------------------------------------------------------------------------------
// PAGE 0: WELCOME
// -------------------------------------------------------------------------------------------------
@Composable
private fun OnboardingWelcomePage(textColor: Color, secondaryText: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon hero
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "Another Launcher",
            modifier = Modifier.size(84.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Another Launcher",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            letterSpacing = (-0.5).sp
        )

        Text(
            text = "Crafted for foldables",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "A fluid, modern home screen tailored for dual screens. Private, customizable, and free of ads or tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Key Value Pillars
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingPillarCard(
                glyph = Glyph.FOLD,
                title = "Designed for foldables",
                description = "Transitions smoothly between your cover screen and wide unfolded display.",
                textColor = textColor,
                secondaryText = secondaryText
            )
            OnboardingPillarCard(
                glyph = Glyph.PALETTE,
                title = "Frosted glass aesthetic",
                description = "Translucent liquid surfaces that reflect your wallpaper with depth and clarity.",
                textColor = textColor,
                secondaryText = secondaryText
            )
            OnboardingPillarCard(
                glyph = Glyph.WIDGETS,
                title = "Private and ad-free",
                description = "No ads or subscriptions. Layout and app search stay on your device; weather and web search contact their providers.",
                textColor = textColor,
                secondaryText = secondaryText
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// PAGE 1: ARCHITECTURE (3 TIERS)
// -------------------------------------------------------------------------------------------------
@Composable
private fun OnboardingArchitecturePage(textColor: Color, secondaryText: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "A three-part home screen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Text(
            text = "Everything connects with a simple swipe.",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Tier Flow Diagram Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(18, frosted = true)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierPill("The Hub", "Swipe right", Glyph.WIDGETS, textColor, secondaryText)
            GlyphIcon(Glyph.CHEVRON_RIGHT, tint = secondaryText.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            TierPill("Home", "Center", Glyph.HOME, textColor, secondaryText, isHighlight = true)
            GlyphIcon(Glyph.CHEVRON_RIGHT, tint = secondaryText.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            TierPill("Library", "Swipe left", Glyph.APPS, textColor, secondaryText)
        }

        Spacer(Modifier.height(14.dp))

        // Cards for each tier
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingTierCard(
                tierBadge = "SWIPE RIGHT",
                title = "The Hub",
                glyph = Glyph.WIDGETS,
                summary = "Your widget page. Check the weather forecast and add, arrange, or remove your favorite widgets.",
                textColor = textColor,
                secondaryText = secondaryText
            )

            OnboardingTierCard(
                tierBadge = "CENTER",
                title = "Home screen",
                glyph = Glyph.HOME,
                summary = "Your primary space for everyday apps and widgets. Unfold your device to view dual pages side by side.",
                textColor = textColor,
                secondaryText = secondaryText
            )

            OnboardingTierCard(
                tierBadge = "SWIPE LEFT",
                title = "App library",
                glyph = Glyph.APPS,
                summary = "Tap Search to find apps, search the web, or find contacts and settings. Swipe left past your last home page to browse categories or the alphabetical list.",
                textColor = textColor,
                secondaryText = secondaryText
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// PAGE 2: GESTURES & POWER FEATURES
// -------------------------------------------------------------------------------------------------
@Composable
private fun OnboardingGesturesPage(textColor: Color, secondaryText: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Customizable and gesture-driven",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Text(
            text = "Simple gestures to personalize your layout.",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingGestureCard(
                glyph = Glyph.STACK,
                title = "Widget stacks",
                description = "Drag matching widgets together to cycle through them like a deck of cards.",
                textColor = textColor,
                secondaryText = secondaryText
            )

            OnboardingGestureCard(
                glyph = Glyph.EDIT,
                title = "Resize from any edge",
                description = "Press and hold a widget, then drag the corner handles to fit your grid.",
                textColor = textColor,
                secondaryText = secondaryText
            )

            OnboardingGestureCard(
                glyph = Glyph.APPS,
                title = "Swap apps instantly",
                description = "Drag one app over another to swap their positions without creating accidental folders.",
                textColor = textColor,
                secondaryText = secondaryText
            )

            OnboardingGestureCard(
                glyph = Glyph.PAGES,
                title = "Manage pages",
                description = "Press and hold the wallpaper to add, reorder, or set your default home page.",
                textColor = textColor,
                secondaryText = secondaryText
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// PAGE 3: PERMISSIONS & INTEGRATIONS
// -------------------------------------------------------------------------------------------------
@Composable
private fun OnboardingPermissionsPage(
    textColor: Color,
    secondaryText: Color,
    isDefaultHome: Boolean,
    isNotificationGranted: Boolean,
    isLocationGranted: Boolean,
    isContactsGranted: Boolean,
    isSignalGranted: Boolean,
    onRequestHome: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestSignal: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Quick setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Text(
            text = "These features are optional. Choose what to enable, or continue without them. Each card explains how its data is used.",
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Default Home
            PermissionItemCard(
                glyph = Glyph.HOME,
                title = "Set as default home",
                description = "Make Another Launcher your primary home screen so home gestures work smoothly.",
                isGranted = isDefaultHome,
                grantedLabel = "Default home",
                actionLabel = "Set default",
                onAction = onRequestHome,
                textColor = textColor,
                secondaryText = secondaryText
            )

            // 2. Notification Badges
            PermissionItemCard(
                glyph = Glyph.BELL,
                title = "Notification dots",
                description = PermissionExplanation.NOTIFICATIONS,
                isGranted = isNotificationGranted,
                grantedLabel = "Enabled",
                actionLabel = "Enable dots",
                onAction = onRequestNotification,
                textColor = textColor,
                secondaryText = secondaryText
            )

            // 3. Live Weather Location
            PermissionItemCard(
                glyph = Glyph.LOCATION,
                title = "Live weather",
                description = PermissionExplanation.LOCATION,
                isGranted = isLocationGranted,
                grantedLabel = "Allowed",
                actionLabel = "Use location",
                onAction = onRequestLocation,
                textColor = textColor,
                secondaryText = secondaryText
            )

            // 4. Contacts Search
            PermissionItemCard(
                glyph = Glyph.CONTACT,
                title = "Search contacts",
                description = PermissionExplanation.CONTACTS,
                isGranted = isContactsGranted,
                grantedLabel = "Allowed",
                actionLabel = "Enable search",
                onAction = onRequestContacts,
                textColor = textColor,
                secondaryText = secondaryText
            )

            // 5. Cellular Signal
            PermissionItemCard(
                glyph = Glyph.SIGNAL,
                title = "Cellular signal indicator",
                description = PermissionExplanation.PHONE_STATE,
                isGranted = isSignalGranted,
                grantedLabel = "Allowed",
                actionLabel = "Show signal",
                onAction = onRequestSignal,
                textColor = textColor,
                secondaryText = secondaryText
            )
        }

        Spacer(Modifier.height(14.dp))
        TextButton(
            onClick = onOpenPrivacyPolicy,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Privacy Policy & Data Safety",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// PAGE 4: READY TO LAUNCH
// -------------------------------------------------------------------------------------------------
@Composable
private fun OnboardingReadyPage(
    textColor: Color,
    secondaryText: Color,
    isTourMode: Boolean,
    onEnter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .glass(22, frosted = true),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.CHECK, tint = SuccessGreen, modifier = Modifier.size(36.dp))
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (isTourMode) "Tour complete" else "You're all set",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Text(
            text = if (isTourMode)
                "You can revisit this tour anytime in settings."
            else
                "Your home screen is ready. Enjoy a faster, cleaner experience on every screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Pro Tips Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(20, frosted = true)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "GOOD TO KNOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TipRow("Press and hold wallpaper", "Opens page management, settings, and wallpaper options.", textColor, secondaryText)
            TipRow("Tap time or battery", "Opens quick settings for Wi-Fi, battery details, and display options.", textColor, secondaryText)
            TipRow("Private by design", "No accounts, no ads, and no background tracking. Your data is yours alone.", textColor, secondaryText)
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onEnter,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = if (isTourMode) "Return to settings" else "Start using Another Launcher",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            GlyphIcon(Glyph.CHEVRON_RIGHT, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// REUSABLE SUBCOMPONENTS
// -------------------------------------------------------------------------------------------------

@Composable
private fun OnboardingPillarCard(
    glyph: Glyph,
    title: String,
    description: String,
    textColor: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(18, frosted = true)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun OnboardingTierCard(
    tierBadge: String,
    title: String,
    glyph: Glyph,
    summary: String,
    textColor: Color,
    secondaryText: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(18, frosted = true)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(glyph, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = tierBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun OnboardingGestureCard(
    glyph: Glyph,
    title: String,
    description: String,
    textColor: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(18, frosted = true)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, tint = textColor, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PermissionItemCard(
    glyph: Glyph,
    title: String,
    description: String,
    isGranted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    textColor: Color,
    secondaryText: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(16, frosted = true).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(
                    if (isGranted) SuccessGreen.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape
                ), contentAlignment = Alignment.Center
            ) {
                GlyphIcon(glyph, tint = if (isGranted) SuccessGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = textColor)
            if (isGranted) {
                Surface(shape = RoundedCornerShape(12.dp), color = SuccessGreen.copy(alpha = 0.15f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GlyphIcon(Glyph.CHECK, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                        Text(grantedLabel, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                }
            }
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = secondaryText)
        if (!isGranted) {
            FilledTonalButton(onClick = onAction, modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TierPill(
    name: String,
    page: String,
    glyph: Glyph,
    textColor: Color,
    secondaryText: Color,
    isHighlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (isHighlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    else Color.Transparent,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                glyph = glyph,
                tint = if (isHighlight) MaterialTheme.colorScheme.primary else textColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
        Text(
            text = page,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryText,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TipRow(
    title: String,
    description: String,
    textColor: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(description, style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
    }
}
