package com.dzirbel.kotify.ui.framework

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.dzirbel.kotify.ui.components.VerticalScroll
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.LocalColors
import com.dzirbel.kotify.ui.util.HandleState

@Composable
fun <T> BoxScope.ScrollingPage(
    scrollState: ScrollState,
    presenter: Presenter<T?, *>,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    HandleState(
        state = { presenter.state() },
        onError = { throwable ->
            Column(Modifier.align(Alignment.Center)) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    modifier = Modifier.size(Dimens.iconHuge).align(Alignment.CenterHorizontally),
                    tint = LocalColors.current.error
                )

                Text(
                    text = "Encountered an error: ${throwable.message}",
                    color = LocalColors.current.error,
                    style = MaterialTheme.typography.h5,
                )

                Text(
                    text = throwable.stackTraceToString(),
                    color = LocalColors.current.error,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = { presenter.clearError() }
                ) {
                    Text("Clear")
                }
            }
        },
        onLoading = {
            CircularProgressIndicator(Modifier.size(Dimens.iconHuge).align(Alignment.Center))
        },
        onSuccess = {
            VerticalScroll(scrollState = scrollState, content = { content(it) })
        }
    )
}