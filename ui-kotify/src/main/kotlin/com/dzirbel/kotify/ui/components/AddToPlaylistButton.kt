package com.dzirbel.kotify.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import com.dzirbel.kotify.repository.playlist.PlaylistViewModel
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.components.adapter.ListAdapterState
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.instrumentation.instrument

@Composable
fun ScrollableDropdownMenu(
    playlists: ListAdapterState<PlaylistViewModel>,
    onOptionSelected: (PlaylistViewModel) -> Unit, // Callback to handle option selection
    modifier: Modifier = Modifier,
    size: Dp = Dimens.iconSmall,
) {
    var expanded by remember { mutableStateOf(false) } // To control the dropdown's visibility
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) } // To handle text input

    Column(modifier = modifier) {
        // Text field to trigger dropdown
        IconButton(
            modifier = modifier.instrument().size(size),
            onClick = {
                expanded = !expanded // Toggle dropdown visibility
            }
        ) {
            CachedIcon(
                name = "data-table",
                size = size,
                contentDescription = "Add to Test",
            )
        }

        // Dropdown menu when expanded
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }, // Close dropdown on outside click
        ) {
            // Scrollable dropdown list
            playlists.value.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option) // Handle option selection
                    textFieldValue = TextFieldValue(option.name) // Update text field with the selected option
                    expanded = false // Close the dropdown
                }) {
                    Text(text = option.name)
                }
            }
        }
    }
}
