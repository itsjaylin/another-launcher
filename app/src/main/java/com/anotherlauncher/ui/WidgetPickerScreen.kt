package com.anotherlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.anotherlauncher.widgets.WidgetController
import com.anotherlauncher.widgets.WidgetProvider
import com.anotherlauncher.widgets.WidgetRequirements
import com.anotherlauncher.widgets.WidgetSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun WidgetPickerScreen(
    controller: WidgetController,
    onBack: () -> Unit,
    onProvider: (WidgetProvider) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val providers by produceState<List<WidgetProvider>?>(null, controller) {
        try { value = controller.catalog() } catch (_: SecurityException) { failed = true; value = emptyList() }
    }
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    FullPageSurface("Widgets", onBack) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Find widgets") }, singleLine = true, leadingIcon = { GlyphIcon(Glyph.SEARCH) })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val groups = providers.orEmpty().filter { it.appName.contains(query, true) || it.label.contains(query, true) }
                .groupBy { it.info.provider.packageName }
                // Collapse same-profile aliases sharing a label and footprint (OEMs expose one
                // widget through several receivers); distinct profiles stay separate entries.
                .mapValues { (_, list) ->
                    list.distinctBy { Triple(it.info.profile, it.label, pickerSpans(context, it.info)) }
                }.filterValues { it.isNotEmpty() }
            items(groups.entries.toList(), key = { it.key }) { (packageName, variants) ->
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        ListItem(headlineContent = { Text(variants.first().appName) }, supportingContent = { Text("${variants.size} widget variants") },
                            trailingContent = { Text(if (expanded == packageName) "−" else "+") },
                            modifier = Modifier.clickable { expanded = if (expanded == packageName) null else packageName })
                        if (expanded == packageName || query.isNotBlank()) {
                            val indexed = variants.mapIndexed { index, provider -> index to provider }
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                indexed.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { (index, provider) ->
                                            val (w, h) = pickerSpans(context, provider.info)
                                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.weight(1f)) {
                                                Column(Modifier.clickable { onProvider(provider) }) {
                                                    WidgetPreview(provider)
                                                    ListItem(headlineContent = { Text(provider.label, maxLines = 2) },
                                                        supportingContent = { Text("$w × $h · Variant ${index + 1}") })
                                                }
                                            }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (providers == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (failed) item { Text("Android couldn't provide the widget catalog.") }
        }
    }
}

/**
 * Spans a widget will actually occupy on our grid: the system's target cells floored
 * by the real min-size need via the same [WidgetSizing.initialSpans] math as the drop
 * path, so the picker never promises 2 × 2 for a 4-wide widget (or vice versa).
 */
private fun pickerSpans(context: Context, info: AppWidgetProviderInfo): Pair<Int, Int> {
    if (context.resources.configuration.screenWidthDp <= 0) {
        return (info.targetCellWidth.takeIf { it > 0 } ?: 2) to
            (info.targetCellHeight.takeIf { it > 0 } ?: 2)
    }
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val config = context.resources.configuration
    val cell = min(config.screenWidthDp / 4f, config.screenHeightDp / 6f).coerceAtLeast(1f)
    // minWidth/minHeight arrive in pixels (see WidgetController.requirements).
    val requirements = WidgetRequirements(
        info.minWidth / density, info.minHeight / density,
        info.minWidth / density, info.minHeight / density,
        resizeHorizontal = false, resizeVertical = false,
    )
    return WidgetSizing.initialSpans(requirements, info.targetCellWidth, info.targetCellHeight, cell, cell)
}

/** Real provider artwork via the framework preview image or preview layout; the provider icon fills in when absent. */
@Composable
private fun WidgetPreview(provider: WidgetProvider) {
    val context = LocalContext.current
    val densityDpi = androidx.compose.ui.platform.LocalResources.current.displayMetrics.densityDpi
    val preview by produceState<android.graphics.Bitmap?>(null, provider.key, densityDpi) {
        val fromImage = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = provider.info.loadPreviewImage(context, densityDpi)
                    ?: return@runCatching null
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return@runCatching null
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return@runCatching null
                val scale = (640f / maxOf(width, height)).coerceAtMost(1f)
                drawable.toBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
            }.getOrNull()
        }
        if (fromImage != null) { value = fromImage; return@produceState }
        // Widgets (including Samsung Weather) often publish previewLayout instead of previewImage.
        // minSdk is 31, so previewLayout is always available.
        value = withContext(Dispatchers.Main) {
            runCatching { renderPreviewLayout(context, provider.info) }.getOrNull()
        }
    }
    if (preview != null) {
        Image(preview!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(preview!!.width.toFloat() / preview!!.height))
    } else {
        val icon by produceState<android.graphics.Bitmap?>(null, provider.key, densityDpi) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    provider.info.loadIcon(context, densityDpi)
                        ?.toBitmap(96, 96)
                }.getOrNull()
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
            Text("No preview from this app — tap to add and see it live",
                Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Renders an [AppWidgetProviderInfo.previewLayout] into a bitmap by inflating it
 * with the *provider's* package context (layout IDs are package-local, e.g. Samsung Weather).
 * Must be called on the main thread. Returns null when the provider publishes no layout
 * or the layout cannot be inflated/measured (caller falls back to the provider icon).
 */
@android.annotation.SuppressLint("ResourceType")
private fun renderPreviewLayout(context: Context, info: AppWidgetProviderInfo): Bitmap? {
    val layoutId = info.previewLayout
    if (layoutId == 0) return null
    // Layout IDs are resolved against the owning package, not ours.
    val packageContext = runCatching {
        context.createPackageContext(info.provider.packageName, Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull() ?: return null
    val parent = FrameLayout(packageContext)
    val view = android.view.LayoutInflater.from(packageContext).inflate(layoutId, parent, false) ?: return null
    // Constrain width to the widget's own minWidth (the size class this preview was
    // drawn for) with free height: AT_MOST height plus a narrow width clips the bottom,
    // while a wide natural size strands the fixed-dp artwork in a corner.
    val lp = view.layoutParams
    val wSpec = if (lp != null && lp.width > 0) View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY)
        else View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST)
    val hSpec = if (lp != null && lp.height > 0) View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY)
        else View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
    view.measure(wSpec, hSpec)
    val measuredW = view.measuredWidth
    val measuredH = view.measuredHeight
    if (measuredW <= 0 || measuredH <= 0) return null
    view.measure(
        View.MeasureSpec.makeMeasureSpec(measuredW, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(measuredH, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, measuredW, measuredH)
    val scale = (640f / maxOf(measuredW, measuredH)).coerceAtMost(1f)
    val outW = (measuredW * scale).toInt().coerceAtLeast(1)
    val outH = (measuredH * scale).toInt().coerceAtLeast(1)
    val bitmap = androidx.core.graphics.createBitmap(outW, outH)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    view.draw(canvas)
    return bitmap
}
