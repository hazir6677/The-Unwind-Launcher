package com.unwind.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The pop-up that opens when you hold OK on an app in the home row.
 *
 *  - App info opens Android's own page for the app (storage, permissions, force stop).
 *  - Move picks the app up so Left and Right carry it along the row; OK puts it down.
 *  - Remove from Home unpins it (it stays in All apps).
 *  - Uninstall hands the app to Android's uninstall screen. Apps built into the TV can't be uninstalled.
 */
@Composable
fun AppMenuDialog(
    app: AppEntry,
    isSystem: Boolean,
    focus: FocusRequester,
    onInfo: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onUninstall: () -> Unit,
    onBlockedUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    // You are still holding OK when this opens. Ignore clicks for a moment so letting go doesn't pick an item.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(app.pkg) {
        delay(150)
        runCatching { focus.requestFocus() }
        delay(350)
        armed = true
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
            // the dim sits between the wallpaper and the glass, so the glass has to know about it
            CompositionLocalProvider(LocalGlassDim provides 0.55f) {
                PopupPanel(app.label, width = 320.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PopupRow(
                            "App info",
                            subtitle = "Storage, permissions, force stop",
                            icon = Icons.Rounded.Info,
                            modifier = Modifier.focusRequester(focus),
                            onClick = { if (armed) onInfo() },
                        )
                        PopupRow(
                            "Move",
                            subtitle = "Slide it along the row with the arrows",
                            icon = Icons.Rounded.OpenWith,
                            onClick = { if (armed) onMove() },
                        )
                        PopupRow(
                            "Remove from Home",
                            subtitle = "Stays in All apps",
                            icon = Icons.Rounded.RemoveCircleOutline,
                            onClick = { if (armed) onRemove() },
                        )
                        PopupRow(
                            "Uninstall",
                            subtitle = if (isSystem) "Built into the TV, so Android won't remove it" else null,
                            icon = Icons.Rounded.Delete,
                            onClick = { if (armed) { if (isSystem) onBlockedUninstall() else onUninstall() } },
                        )
                    }
                }
            }
        }
    }
}
