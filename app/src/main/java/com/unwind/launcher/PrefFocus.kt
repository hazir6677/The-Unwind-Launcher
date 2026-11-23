package com.unwind.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * D-pad navigation for Preferences.
 *
 * Before, every card relied on Compose's automatic "find the nearest thing below" search. Inside a
 * scrolling page full of cards of different widths that search is unreliable on a TV, and nothing
 * scrolled the page to follow the highlight, so Down seemed to do nothing and OK kept activating
 * the tab you were still on.
 *
 * Now each focusable card on a Preferences page says where it sits: row N, position M in that row.
 *   Up / Down    -> the row above / below (same position, or the last card if that row is shorter)
 *   Left / Right -> the neighbour in the row; Left at the start goes back to the side rail
 *   Down at the end stays put, Up from the first row goes to the tab bar
 * and the page scrolls so the highlighted card (and a little around it) is always on screen.
 * Nothing here depends on layout, so it behaves the same on every Preferences tab.
 */
class PrefRows {
    private val items = HashMap<Long, FocusRequester>()
    /** The card that last had focus, so coming back from a sub-screen puts you where you were. */
    var lastRow = -1
    var lastCol = 0

    private fun key(row: Int, col: Int) = row.toLong() * 10_000L + col

    fun add(row: Int, col: Int, r: FocusRequester) { items[key(row, col)] = r }
    fun remove(row: Int, col: Int, r: FocusRequester) { if (items[key(row, col)] === r) items.remove(key(row, col)) }
    fun clear() { items.clear(); lastRow = -1; lastCol = 0 }

    private fun rowsSorted(): List<Int> = items.keys.map { (it / 10_000L).toInt() }.distinct().sorted()
    private fun colsOf(row: Int): List<Int> =
        items.keys.filter { (it / 10_000L).toInt() == row }.map { (it % 10_000L).toInt() }.sorted()

    fun first(): FocusRequester? {
        val r = rowsSorted().firstOrNull() ?: return null
        return items[key(r, colsOf(r).first())]
    }

    fun at(row: Int, col: Int): FocusRequester? {
        if (row < 0) return null
        val cols = colsOf(row)
        if (cols.isEmpty()) return null
        val c = cols.lastOrNull { it <= col } ?: cols.first()
        return items[key(row, c)]
    }

    fun rowAbove(row: Int): Int? = rowsSorted().lastOrNull { it < row }
    fun rowBelow(row: Int): Int? = rowsSorted().firstOrNull { it > row }
    fun colBefore(row: Int, col: Int): Int? = colsOf(row).lastOrNull { it < col }
    fun colAfter(row: Int, col: Int): Int? = colsOf(row).firstOrNull { it > col }
}

/**
 * Marks a card as row [row], position [col] of the current Preferences page. Put it on the card's
 * modifier, before the click handling. [nav] supplies the side rail and the tab bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.prefItem(rows: PrefRows, nav: Nav, row: Int, col: Int = 0): Modifier {
    val req = remember(rows, row, col) { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { 56.dp.toPx() }
    val sizeHolder = remember { arrayOf(IntSize.Zero) }
    DisposableEffect(rows, row, col, req) {
        rows.add(row, col, req)
        onDispose { rows.remove(row, col, req) }
    }
    return this
        .focusRequester(req)
        .bringIntoViewRequester(bring)
        .onSizeChanged { sizeHolder[0] = it }
        .onFocusChanged {
            if (it.isFocused) {
                rows.lastRow = row
                rows.lastCol = col
                // scroll so the card and a little of what is around it (its section title) stay visible
                val s = sizeHolder[0]
                scope.launch {
                    runCatching {
                        bring.bringIntoView(Rect(0f, -margin, s.width.toFloat(), s.height.toFloat() + margin))
                    }
                }
            }
        }
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            fun go(target: FocusRequester?): Boolean = target != null && runCatching { target.requestFocus() }.isSuccess
            when (e.key) {
                Key.DirectionDown -> {
                    val r = rows.rowBelow(row)
                    if (r == null) true else go(rows.at(r, col)) || true
                }
                Key.DirectionUp -> {
                    val r = rows.rowAbove(row)
                    if (r == null) go(nav.content) else go(rows.at(r, col))
                }
                Key.DirectionLeft -> {
                    val c = rows.colBefore(row, col)
                    if (c == null) go(nav.rail) else go(rows.at(row, c))
                }
                Key.DirectionRight -> {
                    val c = rows.colAfter(row, col)
                    // nothing to the right: stay on this card instead of jumping somewhere unexpected
                    if (c == null) true else go(rows.at(row, c)) || true
                }
                else -> false
            }
        }
}
