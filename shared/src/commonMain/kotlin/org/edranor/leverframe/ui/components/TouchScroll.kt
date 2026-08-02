package org.edranor.leverframe.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Adds touch-to-scroll behaviour for [LazyListState] by translating vertical
 * pointer drags into scroll-offset deltas via [draggable].
 */
@Composable
fun Modifier.touchScroll(state: LazyListState): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return this.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            coroutineScope.launch {
                state.dispatchRawDelta(-delta)
            }
        }
    )
}

/**
 * Adds touch-to-scroll behaviour for [ScrollState] by translating vertical
 * pointer drags into scroll-offset deltas via [draggable].
 */
@Composable
fun Modifier.touchScroll(state: ScrollState): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return this.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            coroutineScope.launch {
                state.dispatchRawDelta(-delta)
            }
        }
    )
}

/**
 * Adds touch-to-scroll behaviour for [LazyListState] by translating horizontal
 * pointer drags into scroll-offset deltas via [draggable].
 */
@Composable
fun Modifier.horizontalTouchScroll(state: LazyListState): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return this.draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            coroutineScope.launch {
                state.dispatchRawDelta(-delta)
            }
        }
    )
}

/**
 * Adds touch-to-scroll behaviour for [ScrollState] by translating horizontal
 * pointer drags into scroll-offset deltas via [draggable].
 */
@Composable
fun Modifier.horizontalTouchScroll(state: ScrollState): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return this.draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            coroutineScope.launch {
                state.dispatchRawDelta(-delta)
            }
        }
    )
}
