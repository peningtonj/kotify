package com.dzirbel.kotify.ui.components.adapter

import androidx.compose.runtime.Immutable

/**
 * Represents a property by which objects of type [E] can be filtered.
 *
 * Note that [FilterableProperty] extends [Comparator] whose functions can be used to compare elements of type [E].
 */
@Immutable
interface FilterableProperty<E> : AdapterProperty<E> {
    /**
     * A user-readable name of this property, specific to its use in filtering. By default uses [title] but may be
     * overridden to use a specific name for filtering.
     */
    val filterTitle: String
        get() = title


    /**
     * Evaluates whether the given [element] matches the filter condition defined by this property.
     *
     * @return True if the element matches the filter; false otherwise.
     */
    fun filter(element: E): Boolean
}

