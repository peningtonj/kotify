package com.dzirbel.kotify.ui.properties

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.dzirbel.kotify.repository.Rating
import com.dzirbel.kotify.ui.components.adapter.DividableProperty
import com.dzirbel.kotify.ui.components.adapter.SortOrder
import com.dzirbel.kotify.ui.components.adapter.SortableProperty
import com.dzirbel.kotify.ui.components.adapter.compareNullable
import com.dzirbel.kotify.ui.components.star.AverageStarRating
import com.dzirbel.kotify.ui.components.table.Column
import com.dzirbel.kotify.util.averageOrNull
import java.util.Locale
import kotlin.math.floor

abstract class PropertyByAverageRating<E>(
    private val ratings: Map<String, List<State<Rating?>>?>,
    private val maxRating: Int = Rating.DEFAULT_MAX_AVERAGE_RATING,
    override val title: String = "Rating",
) : SortableProperty<E>, DividableProperty<E>, Column<E> {
    override val defaultDivisionSortOrder = SortOrder.DESCENDING
    override val defaultSortOrder = SortOrder.DESCENDING
    override val terminalSort = true

    abstract fun idOf(element: E): String

    override fun compare(sortOrder: SortOrder, first: E, second: E): Int {
        return sortOrder.compareNullable(averageRatingOf(first), averageRatingOf(second))
    }

    override fun compareDivisions(sortOrder: SortOrder, first: Any?, second: Any?): Int {
        return sortOrder.compareNullable(first as? Double, second as? Double)
    }

    override fun divisionFor(element: E): Double? {
        return averageRatingOf(element)?.let { average ->
            floor(average * maxRating * DIVIDING_FRACTION) / DIVIDING_FRACTION
        }
    }

    override fun divisionTitle(division: Any?): String {
        return (division as? Double)?.let { String.format(Locale.getDefault(), "%.1f", it) }
            ?: "Unrated"
    }

    @Composable
    override fun item(item: E) {
        AverageStarRating(
            ratings = ratings[idOf(item)]?.map { it.value },
            maxRating = maxRating,
        )
    }

    private fun averageRatingOf(element: E): Double? {
        return ratings[idOf(element)]?.averageOrNull { it.value?.ratingPercent }
    }

    companion object {
        /**
         * Determines what ranges ratings are grouped into, inverted. E.g. with a fraction of 4 ranges will be
         * 0.0 - 0.25, 0.25 - 0.5, 0.5 - 0.75, 0.75 - 1.0, etc. Note that the rounding of the header strings may make
         * some groupings unclear.
         */
        private const val DIVIDING_FRACTION = 2
    }
}