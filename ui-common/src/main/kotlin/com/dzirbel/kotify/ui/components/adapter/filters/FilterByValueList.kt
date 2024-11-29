package com.dzirbel.kotify.ui.components.adapter.filters

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.ui.components.adapter.FilterableProperty
import com.dzirbel.kotify.ui.theme.Dimens

abstract class FilterByValueList<E, F>(
    override val title: String,
    val values: List<F>,
    val pGetter: (E) -> F
) :
    FilterableProperty<E> {

    val allValues = values.toMutableSet()
    val allowedValues: MutableSet<F> = values.toMutableSet()


    private fun addAllowableValue(option: F) {
        allowedValues.add(option)
    }

    private fun removeAllowableValue(option: F) {
        allowedValues.remove(option)
    }

    fun toggleAllowableValue(option: F) {
        if (allowedValues.contains(option)) {
            removeAllowableValue(option)
        } else {
            addAllowableValue(option)
        }
    }

    override fun filter(element: E): Boolean {
        return allowedValues.contains(pGetter(element))
    }

    open fun filterFunction(): (E) -> Boolean {
        return { element -> allowedValues.contains(pGetter(element)) }
    }

}

@Composable
fun <E, F> DropdownFromValueListFilter(
    filterByValueList: FilterByValueList<E, F>,
    onFiltersChanged: () -> Unit = {},
) {
    val dropdownExpanded = remember { mutableStateOf(false) }

    // Toggle dropdown visibility
    Text(
        text = filterByValueList.title,
        modifier = Modifier
            .padding(Dimens.space2)
            .toggleable(value = dropdownExpanded.value, onValueChange = { dropdownExpanded.value = it })
    )

    DropdownMenu(
        expanded = dropdownExpanded.value,
        onDismissRequest = { dropdownExpanded.value = false }
    ) {
        filterByValueList.allValues.forEach { item ->
            val isChecked = filterByValueList.allowedValues.contains(item)
            DropdownMenuItem(onClick = {
                filterByValueList.toggleAllowableValue(item)
                onFiltersChanged()
            }) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = {
                        filterByValueList.toggleAllowableValue(item)
                    }
                )
                Text(
                    text = item.toString(), // You can customize how `value` is displayed here
                    modifier = Modifier.padding(start = Dimens.space2)
                )
            }
        }
    }
}

