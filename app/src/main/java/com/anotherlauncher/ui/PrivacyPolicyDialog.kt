package com.anotherlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full in-app Privacy Policy dialog providing transparent disclosure of all data practices,
 * available from onboarding and Settings without adding a permission step.
 */
@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.70f) else MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(28.dp))
                .glass(28, emphasized = true, frosted = true)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Privacy Policy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Another Launcher · Version 0.7.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        GlyphIcon(Glyph.CLOSE, tint = textColor, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PolicySection(
                        title = "1. Our Privacy Commitment",
                        content = "Another Launcher is built around privacy and local-first execution. " +
                                "The launcher has no accounts, advertisements, analytics, or tracking SDKs. " +
                                "Layouts, contacts search, and app categorization stay on your device. Optional weather requests are described below.",
                        textColor = textColor,
                        secondaryText = secondaryText
                    )

                    PolicySection(
                        title = "2. Permissions & Data Handling",
                        content = "Permissions support these optional features:\n\n" +
                                "• Location (Optional): Android lets you allow approximate or precise location. The selected city and coordinates are saved locally, included in exported backups, and sent to Open-Meteo for weather. You can choose a city manually instead. The launcher does not track your movements.\n\n" +
                                "• Contacts (Optional): Used solely to match names and telephone numbers for App Library search. Contacts remain local on your device and are never uploaded.\n\n" +
                                "• Telephony & Phone State (Optional): Used only to display cellular signal bars and connection type (5G/LTE) in the status pill.\n\n" +
                                "• Notification Access (Optional): Used solely to place red unread badge dots on app icons. Notification message bodies and sender details are never read, stored, or transmitted.\n\n" +
                                "• Installed Apps: Scoped via standard launcher intents to populate your home screen grid and App Library.",
                        textColor = textColor,
                        secondaryText = secondaryText
                    )

                    PolicySection(
                        title = "3. Third-Party Services",
                        content = "Weather forecasts send the selected coordinates to Open-Meteo; city searches send the city query to its geocoding API. " +
                                "Android's geocoding service may resolve your device coordinates to a city name. " +
                                "Open-Meteo may log IP addresses and coordinates for troubleshooting under its privacy policy (open-meteo.com/en/terms). " +
                                "The launcher adds no account or advertising identifier. Web search opens your browser with your query and is governed by the selected search provider's privacy policy.",
                        textColor = textColor,
                        secondaryText = secondaryText
                    )

                    PolicySection(
                        title = "4. Data Storage & Portability",
                        content = "Your layout preferences, home pages, folders, and category overrides are stored in private local Android DataStore files on your device. " +
                                "You can export and import complete, human-readable JSON backups at any time via Android's Storage Access Framework. " +
                                "Uninstalling removes private app data; exported backups and system wallpapers remain until you delete or replace them.",
                        textColor = textColor,
                        secondaryText = secondaryText
                    )

                    PolicySection(
                        title = "5. Children's Privacy",
                        content = "Another Launcher is a general home screen launcher for Android devices and does not knowingly collect any data from children or any other users.",
                        textColor = textColor,
                        secondaryText = secondaryText
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Got It")
                }
            }
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String,
    textColor: Color,
    secondaryText: Color,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                color = secondaryText
            )
        }
    }
}
