package com.unwind.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A search box that uses Android's own on-screen keyboard (Gboard or whatever the TV ships with).
 *
 * It has two states so the remote never gets trapped:
 *  - Resting: looks like a field and takes focus like any other button. Press OK to start typing.
 *  - Typing: the real text field is focused and the system keyboard is up. Press the keyboard's
 *    Search/Done key, or Back, and you land on the resting field again.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SystemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Rounded.Search,
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: () -> Unit = {},
) {
    val ui = LocalUi.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var innerHadFocus by remember { mutableStateOf(false) }
    val outer = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    val shape = ui.settings.shape.toBarShape()

    fun finish() {
        restore = true
        editing = false
        keyboard?.hide()
    }

    // Back first closes the typing state (the keyboard itself swallows the first Back while it is up)
    BackHandler(enabled = editing) { finish() }

    LaunchedEffect(editing) {
        if (editing) {
            delay(80)
            runCatching { inner.requestFocus() }
            delay(80)
            keyboard?.show()
        } else {
            innerHadFocus = false
            if (restore) {
                restore = false
                delay(60)
                runCatching { outer.requestFocus() }
            }
        }
    }

    Row(
        modifier
            .height(44.dp)
            .glass(shape, focused || editing)
            .focusRequester(outer)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!editing) editing = true }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (editing) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(ui.wp.accent),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onSearch = { finish(); onSubmit() },
                        onDone = { finish(); onSubmit() },
                        onGo = { finish(); onSubmit() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inner)
                        .onFocusChanged { s ->
                            if (s.isFocused) {
                                innerHadFocus = true
                            } else if (innerHadFocus) {
                                // Focus moved away with the D-pad (not through Done/Back): just stop typing
                                innerHadFocus = false
                                editing = false
                            }
                        },
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(hint, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, maxLines = 1)
                            }
                            field()
                        }
                    },
                )
            } else {
                Text(
                    if (value.isEmpty()) hint else value,
                    color = Color.White.copy(alpha = if (value.isEmpty()) 0.65f else 1f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
