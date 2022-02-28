package com.dzirbel.kotify.ui.components.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.components.SimpleTextButton
import com.dzirbel.kotify.ui.components.adapter.flipped
import com.dzirbel.kotify.ui.components.adapter.icon
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.LocalColors

@Composable
fun <E> DivisionSelector(
    dividers: List<GridDivider<E>>,
    currentDivider: GridDivider<E>?,
    onSelectDivider: (GridDivider<E>?) -> Unit,
) {
    Row(
        modifier = Modifier.background(
            color = LocalColors.current.surface2,
            shape = RoundedCornerShape(size = Dimens.cornerSize),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedIcon(
            name = "horizontal-split",
            size = Dimens.iconSmall,
            modifier = Modifier.padding(horizontal = Dimens.space2),
        )

        val dropdownExpanded = remember { mutableStateOf(false) }
        SimpleTextButton(
            onClick = {
                dropdownExpanded.value = true
            },
            contentPadding = PaddingValues(all = Dimens.space2),
            enforceMinWidth = false,
            enforceMinHeight = true,
        ) {
            if (currentDivider == null) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = "Group by...",
                        fontStyle = FontStyle.Italic,
                    )
                }
            } else {
                Text(currentDivider.dividerTitle)
            }

            DropdownMenu(expanded = dropdownExpanded.value, onDismissRequest = { dropdownExpanded.value = false }) {
                dividers.forEach { divider ->
                    if (divider.dividerTitle != currentDivider?.dividerTitle) {
                        DropdownMenuItem(
                            onClick = {
                                dropdownExpanded.value = false

                                onSelectDivider(divider)
                            }
                        ) {
                            Text(divider.dividerTitle)
                        }
                    }
                }
            }
        }

        if (currentDivider != null) {
            SimpleTextButton(
                onClick = {
                    onSelectDivider(currentDivider.withSortOrder(sortOrder = currentDivider.sortOrder.flipped))
                },
                contentPadding = PaddingValues(all = Dimens.space2),
                enforceMinWidth = false,
                enforceMinHeight = true,
            ) {
                Icon(
                    imageVector = currentDivider.sortOrder.icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSmall),
                    tint = LocalColors.current.primary,
                )
            }

            SimpleTextButton(
                onClick = { onSelectDivider(null) },
                contentPadding = PaddingValues(all = Dimens.space2),
                enforceMinWidth = false,
                enforceMinHeight = true,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        }
    }
}
