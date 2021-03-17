package com.dominiczirbel.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.dominiczirbel.ui.theme.Dimens

@Composable
fun InvalidateButton(
    modifier: Modifier = Modifier,
    refreshing: MutableState<Boolean>,
    updated: Long?,
    updatedFormat: (String) -> String = { "Last updated $it" },
    updatedFallback: String = "Never updated",
    onClick: () -> Unit,
) {
    SimpleTextButton(
        modifier = modifier,
        enabled = !refreshing.value,
        onClick = {
            onClick()
            refreshing.value = true
        }
    ) {
        Text(
            text = updated?.let {
                liveRelativeDateText(timestamp = updated, format = updatedFormat)
            } ?: updatedFallback
        )

        Spacer(Modifier.width(Dimens.space2))

        if (refreshing.value) {
            CircularProgressIndicator(Modifier.size(Dimens.iconMedium))
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(Dimens.iconMedium)
            )
        }
    }
}