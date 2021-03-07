package com.dominiczirbel.ui

import androidx.compose.foundation.ScrollbarStyleAmbient
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dominiczirbel.ui.theme.Colors
import com.dominiczirbel.ui.theme.Dimens
import com.dominiczirbel.ui.theme.disabled
import com.dominiczirbel.ui.util.RemoteState

private val ICON_SIZE = 200.dp

/**
 * Convenience wrapper for the common case where a [RemoteState] should be contained in a vertical scrolling box, and
 * handles loading/error states.
 */
@Composable
fun <T : Any> BoxScope.ScrollingPage(state: RemoteState<T>, content: @Composable (T) -> Unit) {
    when (state) {
        is RemoteState.Loading ->
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Loading",
                modifier = Modifier.size(ICON_SIZE).align(Alignment.Center),
                tint = Colors.current.text.disabled()
            )

        is RemoteState.Error ->
            Column(Modifier.align(Alignment.Center)) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    modifier = Modifier.size(ICON_SIZE).align(Alignment.CenterHorizontally),
                    tint = Color.Red
                )

                Text(
                    text = "Encountered an error: ${state.throwable.message}",
                    color = Color.Red,
                    fontSize = Dimens.fontTitle
                )

                Text(
                    text = state.throwable.stackTraceToString(),
                    color = Color.Red,
                    fontSize = Dimens.fontBody
                )
            }

        is RemoteState.Success -> {
            val scrollState = rememberScrollState(0)
            Row {
                Box(Modifier.verticalScroll(scrollState).weight(1f).padding(Dimens.space4)) {
                    content(state.data)
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = ScrollbarStyleAmbient.current.copy(
                        thickness = Dimens.scrollbarWidth,
                        hoverColor = Colors.current.scrollBarHover,
                        unhoverColor = Colors.current.scrollBarUnhover
                    )
                )
            }
        }
    }
}