package com.dzirbel.kotify.ui.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.db.model.PlaylistTrack
import com.dzirbel.kotify.db.model.SavedTrackRepository
import com.dzirbel.kotify.db.model.Track
import com.dzirbel.kotify.db.model.TrackRatingRepository
import com.dzirbel.kotify.repository.Rating
import com.dzirbel.kotify.ui.components.ToggleSaveButton
import com.dzirbel.kotify.ui.components.adapter.DividableProperty
import com.dzirbel.kotify.ui.components.adapter.SortOrder
import com.dzirbel.kotify.ui.components.adapter.SortableProperty
import com.dzirbel.kotify.ui.components.adapter.compareNullable
import com.dzirbel.kotify.ui.components.star.StarRating
import com.dzirbel.kotify.ui.components.table.Column
import com.dzirbel.kotify.ui.components.table.ColumnByLinkedText
import com.dzirbel.kotify.ui.components.table.ColumnWidth
import com.dzirbel.kotify.ui.page.album.AlbumPage
import com.dzirbel.kotify.ui.page.artist.ArtistPage
import com.dzirbel.kotify.ui.pageStack
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.LocalColors
import com.dzirbel.kotify.ui.theme.surfaceBackground
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class TrackNameProperty<T>(private val toTrack: (T) -> Track) : PropertyByString<T>(title = "Name") {
    override val width = ColumnWidth.Weighted(weight = 1f)

    override fun toString(item: T) = toTrack(item).name

    companion object : TrackNameProperty<Track>(toTrack = { it })
    object ForPlaylistTrack : TrackNameProperty<PlaylistTrack>(toTrack = { it.track.cached })
}

open class TrackAlbumIndexProperty<T>(private val toTrack: (T) -> Track) : PropertyByNumber<T>(title = "#") {
    override fun toNumber(item: T) = toTrack(item).trackNumber.toInt()

    companion object : TrackAlbumIndexProperty<Track>(toTrack = { it })
    object ForPlaylistTrack : TrackAlbumIndexProperty<PlaylistTrack>(toTrack = { it.track.cached })
}

open class TrackDurationProperty<T>(private val toTrack: (T) -> Track) : PropertyByNumber<T>(title = "Duration") {
    override val cellAlignment = Alignment.TopEnd

    override fun toNumber(item: T) = toTrack(item).durationMs.toLong()

    override fun toString(item: T): String = formatDuration(toNumber(item))

    companion object : TrackDurationProperty<Track>(toTrack = { it })
    object ForPlaylistTrack : TrackDurationProperty<PlaylistTrack>(toTrack = { it.track.cached })
}

open class TrackArtistsProperty<T>(private val toTrack: (T) -> Track) : PropertyByLinkedText<T>(title = "Artist") {
    override val width = ColumnWidth.Weighted(weight = 1f)

    override fun links(item: T): List<ColumnByLinkedText.Link> {
        return toTrack(item).artists.cached.map { artist ->
            ColumnByLinkedText.Link(text = artist.name, link = artist.id.value)
        }
    }

    override fun onClickLink(link: String) {
        pageStack.mutate { to(ArtistPage(artistId = link)) }
    }

    companion object : TrackArtistsProperty<Track>(toTrack = { it })
    object ForPlaylistTrack : TrackArtistsProperty<PlaylistTrack>(toTrack = { it.track.cached })
}

open class TrackAlbumProperty<T>(private val toTrack: (T) -> Track) : PropertyByLinkedText<T>(title = "Album") {
    override val width = ColumnWidth.Weighted(weight = 1f)

    override fun links(item: T): List<ColumnByLinkedText.Link> {
        val track = toTrack(item)
        return listOf(ColumnByLinkedText.Link(text = track.name, link = track.id.value))
    }

    override fun onClickLink(link: String) {
        pageStack.mutate { to(AlbumPage(albumId = link)) }
    }

    companion object : TrackAlbumProperty<Track>(toTrack = { it })
    object ForPlaylistTrack : TrackAlbumProperty<PlaylistTrack>(toTrack = { it.track.cached })
}

open class TrackPopularityProperty<T>(private val toTrack: (T) -> Track) : PropertyByNumber<T>(title = "Popularity") {
    override val defaultSortOrder = SortOrder.DESCENDING
    override val defaultDivisionSortOrder = SortOrder.DESCENDING

    override val width: ColumnWidth = ColumnWidth.MatchHeader
    override val cellAlignment = Alignment.TopEnd

    override fun toNumber(item: T) = toTrack(item).popularity?.toInt()

    @Composable
    override fun item(item: T) {
        val popularity = toNumber(item) ?: 0
        val color = LocalColors.current.text.copy(alpha = ContentAlpha.disabled)

        LocalColors.current.withSurface {
            Box(
                Modifier
                    .padding(Dimens.space3)
                    .surfaceBackground()
                    .height(Dimens.fontBodyDp)
                    .fillMaxWidth()
                    .border(width = Dimens.divider, color = color)
            ) {
                Box(
                    @Suppress("MagicNumber")
                    Modifier
                        .background(color)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = popularity / 100f)
                )
            }
        }
    }

    companion object : TrackPopularityProperty<Track>(toTrack = { it })
    object ForPlaylistTrack : TrackPopularityProperty<PlaylistTrack>(toTrack = { it.track.cached })
}

open class TrackSavedProperty<T>(
    private val trackIdOf: (T) -> String,
    private val savedTrackIds: Set<String>?,
) : SortableProperty<T>, DividableProperty<T>, Column<T> {
    override val title = "Saved"
    override val width = ColumnWidth.Fill()

    override fun compare(sortOrder: SortOrder, first: T, second: T): Int {
        val firstSaved = savedTrackIds?.contains(trackIdOf(first))
        val secondSaved = savedTrackIds?.contains(trackIdOf(second))

        return sortOrder.compareNullable(firstSaved, secondSaved)
    }

    override fun divisionFor(element: T): Boolean? {
        return savedTrackIds?.contains(trackIdOf(element))
    }

    override fun compareDivisions(sortOrder: SortOrder, first: Any?, second: Any?): Int {
        return sortOrder.compareNullable(first as? Boolean, second as? Boolean)
    }

    override fun divisionTitle(division: Any?): String {
        return when (division as? Boolean) {
            true -> "Saved"
            false -> "Unsaved"
            null -> "Unknown"
        }
    }

    @Composable
    override fun item(item: T) {
        val trackId = trackIdOf(item)

        val scope = rememberCoroutineScope { Dispatchers.IO }
        ToggleSaveButton(
            modifier = Modifier.padding(Dimens.space2),
            isSaved = savedTrackIds?.contains(trackId),
        ) { saved ->
            scope.launch { SavedTrackRepository.setSaved(id = trackId, saved = saved) }
        }
    }
}

class TrackRatingProperty<T>(
    private val trackIdOf: (T) -> String,
    private val trackRatings: Map<String, State<Rating?>>?,
) : PropertyByNumber<T>(title = "Rating") {
    override val defaultSortOrder = SortOrder.DESCENDING
    override val defaultDivisionSortOrder = SortOrder.DESCENDING

    override val width: ColumnWidth = ColumnWidth.Fill()
    override val cellAlignment = Alignment.Center

    override fun toNumber(item: T): Number? {
        return trackRatings?.get(trackIdOf(item))?.value?.ratingPercent
    }

    @Composable
    override fun item(item: T) {
        val trackId = trackIdOf(item)

        val scope = rememberCoroutineScope { Dispatchers.IO }
        StarRating(
            rating = trackRatings?.get(trackId)?.value,
            onRate = { rating ->
                scope.launch { TrackRatingRepository.rate(id = trackId, rating = rating) }
            },
        )
    }
}