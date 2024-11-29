package com.dzirbel.kotify.ui.components.adapter

/**
 * Encapsulates a [filterableProperty] and a particular [filter], which together provide a means to impose an ordering
 * on element of type [E]; namely as [comparator].
 */
data class Filter<E>(
    val title: String,
    val filter: ((E) -> Boolean),
)