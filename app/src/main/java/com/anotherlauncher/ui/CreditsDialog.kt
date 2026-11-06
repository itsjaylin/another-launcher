package com.anotherlauncher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anotherlauncher.R

data class OpenSourceCredit(
    val name: String,
    val author: String,
    val repositoryUrl: String,
    val description: String,
    val license: String = "MIT License",
)

val CREDITS_LIST = listOf(
    OpenSourceCredit(
        name = "DuoLauncher",
        author = "jakesgoodapps",
        repositoryUrl = "https://github.com/jakesgoodapps/DuoLauncher",
        description = "Zero-padding AppWidgetHostView sizing techniques, post-layout option publication, and dual-screen grid layout patterns.",
        license = "MIT License",
    ),
    OpenSourceCredit(
        name = "adaptive_platform_ui",
        author = "berkaycatak",
        repositoryUrl = "https://github.com/berkaycatak/adaptive_platform_ui",
        description = "Translucent liquid glass UI architecture, layered specular reflections, and multi-gradient border effects.",
        license = "MIT License",
    ),
)

/**
 * About Another Launcher dialog showing version, privacy commitment, and open-source credits.
 */
@Composable
fun AboutLauncherDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "Another Launcher App Icon",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                text = "Another Launcher",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "Version 0.7.0-beta.3",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        GlyphIcon(Glyph.CLOSE, tint = textColor, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // App Description Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Another Launcher is a private, local-first launcher engineered for foldables, tablets, and phones. All data stays strictly on your device with zero telemetry, tracking, or ads.",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                color = secondaryText
                            )
                        }
                    }

                    // Open Source Credits Section
                    Text(
                        text = "Open Source Credits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Text(
                        text = "Another Launcher builds upon open-source innovation. We gratefully acknowledge the following projects whose architectures, widget techniques, and UI styling noticeably shaped this launcher:",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = secondaryText
                    )

                    CREDITS_LIST.forEach { credit ->
                        CreditItemCard(
                            credit = credit,
                            textColor = textColor,
                            secondaryText = secondaryText,
                            onOpenUrl = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(credit.repositoryUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun CreditsDialog(
    onDismiss: () -> Unit,
) = AboutLauncherDialog(onDismiss = onDismiss)

@Composable
private fun CreditItemCard(
    credit: OpenSourceCredit,
    textColor: Color,
    secondaryText: Color,
    onOpenUrl: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenUrl)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credit.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "${credit.author} · ${credit.license}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                GlyphIcon(
                    glyph = Glyph.CHEVRON_RIGHT,
                    modifier = Modifier.size(16.dp),
                    tint = secondaryText
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = credit.description,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                color = secondaryText
            )
        }
    }
}
