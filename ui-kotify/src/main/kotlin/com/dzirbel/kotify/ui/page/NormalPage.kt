package com.dzirbel.kotify.ui.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import com.dzirbel.kotify.ui.util.instrumentation.instrument

/**
 * The base layout for a standard vertical scrolling [Page].
 */
@Composable
fun NormalPage(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    onHeaderVisibilityChange: ((Boolean) -> Unit)? = null,
    header: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.instrument()) {
        Column {
            if (header != null) {
                Box(
                    modifier = if (onHeaderVisibilityChange != null) {
                        Modifier.composed {
                            val headerVisibleState = remember { mutableStateOf<Boolean?>(null) }
                            onGloballyPositioned { coordinates ->
                                val headerVisible = coordinates.boundsInParent().bottom - scrollState.value > 0
                                if (headerVisible != headerVisibleState.value) {
                                    headerVisibleState.value = headerVisible
                                    onHeaderVisibilityChange(headerVisible)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                    content = { header() },
                )
            }

            content()
        }
    }
}
