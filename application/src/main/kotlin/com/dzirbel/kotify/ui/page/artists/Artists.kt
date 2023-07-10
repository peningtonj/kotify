package com.dzirbel.kotify.ui.page.artists

import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.onClick
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import com.dzirbel.kotify.db.model.Artist
import com.dzirbel.kotify.repository2.player.Player
import com.dzirbel.kotify.repository2.rating.AverageRating
import com.dzirbel.kotify.ui.components.DividerSelector
import com.dzirbel.kotify.ui.components.Flow
import com.dzirbel.kotify.ui.components.Interpunct
import com.dzirbel.kotify.ui.components.InvalidateButton
import com.dzirbel.kotify.ui.components.LoadedImage
import com.dzirbel.kotify.ui.components.PageLoadingSpinner
import com.dzirbel.kotify.ui.components.Pill
import com.dzirbel.kotify.ui.components.PlayButton
import com.dzirbel.kotify.ui.components.SmallAlbumCell
import com.dzirbel.kotify.ui.components.SortSelector
import com.dzirbel.kotify.ui.components.ToggleSaveButton
import com.dzirbel.kotify.ui.components.VerticalSpacer
import com.dzirbel.kotify.ui.components.adapter.dividableProperties
import com.dzirbel.kotify.ui.components.adapter.sortableProperties
import com.dzirbel.kotify.ui.components.grid.Grid
import com.dzirbel.kotify.ui.components.liveRelativeDateText
import com.dzirbel.kotify.ui.components.star.AverageStarRating
import com.dzirbel.kotify.ui.components.star.RatingHistogram
import com.dzirbel.kotify.ui.page.album.AlbumPage
import com.dzirbel.kotify.ui.page.artist.ArtistPage
import com.dzirbel.kotify.ui.pageStack
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.instrumentation.instrument
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.util.immutable.orEmpty
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun ArtistsPageHeader(presenter: ArtistsPresenter, state: ArtistsPresenter.ViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space5, vertical = Dimens.space4),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Artists", style = MaterialTheme.typography.h4)

            if (state.artists.hasElements) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Text(
                            text = "${state.artists.size} saved artists",
                            modifier = Modifier.padding(end = Dimens.space2),
                        )

                        Interpunct()

                        InvalidateButton(
                            refreshing = state.refreshing,
                            updated = state.artistsUpdated,
                            contentPadding = PaddingValues(all = Dimens.space2),
                            onClick = { presenter.emitAsync(ArtistsPresenter.Event.RefreshArtistLibrary) },
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            DividerSelector(
                dividableProperties = state.artistProperties.dividableProperties(),
                currentDivider = state.artists.divider,
                onSelectDivider = { presenter.emitAsync(ArtistsPresenter.Event.SetDivider(divider = it)) },
            )

            SortSelector(
                sortableProperties = state.artistProperties.sortableProperties(),
                sorts = state.artists.sorts.orEmpty(),
                onSetSort = { presenter.emitAsync(ArtistsPresenter.Event.SetSorts(sorts = it)) },
            )
        }
    }
}

@Composable
fun ArtistsPageContent(presenter: ArtistsPresenter, state: ArtistsPresenter.ViewModel) {
    if (state.artists.hasElements) {
        Grid(
            elements = state.artists,
            edgePadding = PaddingValues(
                start = Dimens.space5 - Dimens.space3,
                end = Dimens.space5 - Dimens.space3,
                bottom = Dimens.space3,
            ),
            selectedElementIndex = state.selectedArtistIndex,
            detailInsertContent = { _, artist ->
                ArtistDetailInsert(artist = artist, presenter = presenter, state = state)
            },
        ) { index, artist ->
            ArtistCell(
                artist = artist,
                savedArtists = state.savedArtistIds,
                artistAverageRating = state.artistRatings[artist.id.value]?.collectAsState()?.value,
                presenter = presenter,
                onRightClick = {
                    presenter.emitAsync(
                        ArtistsPresenter.Event.SetSelectedArtistIndex(
                            index = index.takeIf { index != state.selectedArtistIndex },
                        ),
                    )
                },
            )
        }
    } else {
        PageLoadingSpinner()
    }
}

@Composable
private fun ArtistCell(
    artist: Artist,
    savedArtists: ImmutableSet<String>?,
    artistAverageRating: AverageRating?,
    presenter: ArtistsPresenter,
    onRightClick: () -> Unit,
) {
    Column(
        Modifier
            .instrument()
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Primary)) {
                pageStack.mutate { to(ArtistPage(artistId = artist.id.value)) }
            }
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) {
                onRightClick()
            }
            .padding(Dimens.space3),
    ) {
        LoadedImage(
            url = artist.largestImage.cached?.url,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        VerticalSpacer(Dimens.space3)

        Row(
            modifier = Modifier.widthIn(max = Dimens.contentImage),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Text(text = artist.name, modifier = Modifier.weight(1f))

            val isSaved = savedArtists?.contains(artist.id.value)
            ToggleSaveButton(isSaved = isSaved) {
                presenter.emitAsync(ArtistsPresenter.Event.ToggleSave(artistId = artist.id.value, save = it))
            }

            PlayButton(context = Player.PlayContext.artist(artist), size = Dimens.iconSmall)
        }

        AverageStarRating(averageRating = artistAverageRating)
    }
}

private const val DETAILS_COLUMN_WEIGHT = 0.3f
private const val DETAILS_ALBUMS_WEIGHT = 0.7f

@Composable
private fun ArtistDetailInsert(artist: Artist, presenter: ArtistsPresenter, state: ArtistsPresenter.ViewModel) {
    Row(modifier = Modifier.padding(Dimens.space4), horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        val artistDetails = state.artistDetails[artist.id.value]

        LoadedImage(url = artist.largestImage.cached?.url)

        Column(
            modifier = Modifier.weight(weight = DETAILS_COLUMN_WEIGHT),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Text(artist.name, style = MaterialTheme.typography.h5)

            if (artistDetails != null) {
                artistDetails.savedTime?.let { savedTime ->
                    Text(liveRelativeDateText(timestamp = savedTime.toEpochMilli()) { "Saved $it" })
                }

                Flow {
                    artistDetails.genres.forEach { genre ->
                        Pill(text = genre)
                    }
                }
            }

            state.artistRatings[artist.id.value]
                ?.collectAsState()
                ?.value
                ?.let { ratings -> RatingHistogram(ratings = ratings) }
        }

        artistDetails?.albums?.let { albums ->
            Grid(
                modifier = Modifier.weight(DETAILS_ALBUMS_WEIGHT),
                elements = albums,
            ) { _, artistAlbum ->
                SmallAlbumCell(
                    album = artistAlbum.album.cached,
                    isSaved = state.savedAlbumIds?.contains(artistAlbum.albumId.value),
                    onToggleSave = { save ->
                        presenter.emitAsync(
                            ArtistsPresenter.Event.ToggleAlbumSaved(albumId = artistAlbum.albumId.value, save = save),
                        )
                    },
                    onClick = { pageStack.mutate { to(AlbumPage(albumId = artistAlbum.albumId.value)) } },
                )
            }
        }
    }
}