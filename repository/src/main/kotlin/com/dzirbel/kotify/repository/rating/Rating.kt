package com.dzirbel.kotify.repository.rating

import androidx.compose.runtime.Stable
import com.dzirbel.kotify.util.CurrentTime
import java.time.Instant

/**
 * Wrapper around a singe user-provided rating. To allow changing the rating format in the future, both the current
 * [rating] and the [maxRating] are included, as well as the [rateTime] when the rating was given.
 */
@Stable
data class Rating(
    val rating: Int,
    val maxRating: Int = DEFAULT_MAX_RATING,
    val rateTime: Instant = CurrentTime.instant,
) {
    val ratingPercent: Double
        get() = rating.toDouble() / maxRating

    /**
     * Calculates the relative rating scaled to the given [maxRating], e.g. if this [Rating] is 7/10 and the given
     * [maxRating] is 5, the returned value will be 3.5.
     */
    fun ratingRelativeToMax(maxRating: Int): Double {
        if (maxRating == this.maxRating) return rating.toDouble()
        @Suppress("UnnecessaryParentheses")
        return (maxRating.toDouble() / this.maxRating) * rating
    }

    companion object {
        /**
         * Default max value (number of stars) for individually rated items.
         */
        const val DEFAULT_MAX_RATING = 5

        /**
         * Default max value (number of stars) for ratings shown as averages.
         */
        const val DEFAULT_MAX_AVERAGE_RATING = 5
    }
}
