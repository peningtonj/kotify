package com.dzirbel.kotify.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.components.adapter.Filter
import com.dzirbel.kotify.ui.components.adapter.FilterableProperty
import com.dzirbel.kotify.ui.components.adapter.filters.DropdownFromValueListFilter
import com.dzirbel.kotify.ui.components.adapter.filters.FilterByValueList
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.instrumentation.instrument
import kotlinx.collections.immutable.PersistentList

@Composable
fun <T> FilterOptions(
    filterableOptions: PersistentList<FilterableProperty<T>>,
    onSetFilter: (List<Filter<T>>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Dimens.space3),
) {
    // Store which filters are active
    val activeFilters = remember { mutableStateListOf<Filter<T>>() }

    // Track the visibility state of individual filters
    val filterVisibilityStates = remember {
        filterableOptions.associateWith { mutableStateOf(false) }
    }

    // Function to recalculate active filters
    val updateFilters = {
        activeFilters.clear()
        filterableOptions.forEach { option ->
                val isShown = filterVisibilityStates[option]?.value == true
            if (isShown) {
                (option as? FilterByValueList<T, *>)?.filterFunction()?.let { filter ->
                    activeFilters.add(Filter(option.title, filter))
                }
            }
            onSetFilter(activeFilters) // Notify parent of filter changes
        }
    }

    Column {
        // Dropdown for toggling filters
        Surface(
            modifier = modifier.instrument(),
            elevation = Dimens.componentElevation,
            shape = RoundedCornerShape(size = Dimens.cornerSize),
        ) {
            val dropdownExpanded = remember { mutableStateOf(false) }

            SimpleTextButton(
                onClick = { dropdownExpanded.value = true },
                contentPadding = contentPadding,
                enforceMinWidth = false,
                enforceMinHeight = true,
            ) {
                CachedIcon(
                    name = "horizontal-split",
                    size = Dimens.iconSmall,
                    modifier = Modifier.padding(end = Dimens.space2),
                )
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(text = "Toggle Filter...", fontStyle = FontStyle.Italic, maxLines = 1)
                }

                DropdownMenu(
                    expanded = dropdownExpanded.value,
                    onDismissRequest = { dropdownExpanded.value = false },
                ) {
                    filterableOptions.forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                dropdownExpanded.value = false
                                filterVisibilityStates[option]?.value = !(filterVisibilityStates[option]?.value ?: false)
                                updateFilters() // Recalculate filters whenever an option is toggled
                            },
                        ) {
                            Text(option.title, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Display active filters
        Surface(
            modifier = modifier.instrument(),
            elevation = Dimens.componentElevation,
            shape = RoundedCornerShape(size = Dimens.cornerSize),
        ) {
            Column {
                filterableOptions.forEach { option ->
                    if (filterVisibilityStates[option]?.value == true) {
                        filterFromProperty(option, updateFilters)
                    }
                }
            }
        }
    }
}

@Composable
fun <T> filterFromProperty(
    filterableOption: FilterableProperty<T>,
    onFiltersChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.instrument(),
        elevation = Dimens.componentElevation,
        shape = RoundedCornerShape(size = Dimens.cornerSize),
    ) {
        FlowRow(
            verticalArrangement = Arrangement.Center,
        ) {
            when (filterableOption) {
                is FilterByValueList<*, *> -> DropdownFromValueListFilter(
                    filterByValueList = filterableOption,
                    onFiltersChanged = onFiltersChanged
                )
            }
        }
    }
}
