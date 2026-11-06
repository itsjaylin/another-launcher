package com.anotherlauncher.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anotherlauncher.data.HomeEditor
import com.anotherlauncher.data.HomeItem
import com.anotherlauncher.data.HomePage
import com.anotherlauncher.data.WeatherState
import com.anotherlauncher.widgets.WidgetController
import com.anotherlauncher.widgets.WidgetSizing

/** Page 0 is a dedicated weather and native-widget surface. Search lives in the App Library. */
@Composable
fun HubPage(
    weather: WeatherState,
    weatherUnit: String? = null,
    onWeatherSettings: () -> Unit,
    hubPage: HomePage,
    widgets: WidgetController,
    editing: Boolean,
    reducedMotion: Boolean,
    drag: LauncherDragState,
    targets: HomeDropTargets,
    onWidgetMenu: (HomeItem) -> Unit,
    onAddWidgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val contentMaxWidth = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        WidgetSizing.MAX_CELL_DP.dp * HomeEditor.COLUMNS + 32.dp
    } else {
        680.dp
    }

    Box(modifier.fillMaxSize().testTag("widget-hub"), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = contentMaxWidth).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HubWeatherCard(weather = weather, weatherUnit = weatherUnit, onClick = onWeatherSettings,
                modifier = Modifier.testTag("hub-weather"))
            HubWidgetSection(
                page = hubPage, widgets = widgets, editing = editing,
                reducedMotion = reducedMotion, drag = drag, targets = targets,
                onWidgetMenu = onWidgetMenu, onAddWidgets = onAddWidgets,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
