package com.dzirbel.kotify.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.instrumentation.instrument

/**
 * A generic button to toggle to the saved status of a artist/album/track/etc.
 */
@Composable
fun ToggleSaveButton(
    isSaved: Boolean?,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.iconSmall,
    onSave: (Boolean) -> Unit,
) {
    val expectedState = remember(isSaved) { mutableStateOf(isSaved) }
    IconButton(
        modifier = modifier.instrument().size(size),
        enabled = isSaved != null && expectedState.value == isSaved,
        onClick = {
            if (isSaved != null) {
                expectedState.value = !isSaved
                onSave(!isSaved)
            }
        },
    ) {
        CachedIcon(
            name = if (isSaved == true) "favorite" else "favorite-border",
            size = size,
            contentDescription = "Save",
        )
    }
}