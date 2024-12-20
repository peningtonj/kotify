package com.dzirbel.kotify.ui.page.releaseradar

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import com.dzirbel.kotify.db.model.AlbumType
import com.dzirbel.kotify.repository.CacheState
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow.Companion.requestBatched
import com.dzirbel.kotify.repository.util.ReleaseDate
import com.dzirbel.kotify.ui.*
import com.dzirbel.kotify.ui.page.Page
import com.dzirbel.kotify.ui.page.PageScope
import com.dzirbel.kotify.ui.properties.*
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.*
import com.dzirbel.kotify.util.coroutines.*
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.artist.ArtistRecommendationEngine
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow
import com.dzirbel.kotify.ui.album.AlbumCell
import com.dzirbel.kotify.ui.album.AlbumTypePicker
import com.dzirbel.kotify.ui.components.*
import com.dzirbel.kotify.ui.components.adapter.*
import com.dzirbel.kotify.ui.components.grid.Grid
import com.dzirbel.kotify.ui.components.table.Column
import com.dzirbel.kotify.ui.page.album.AlbumPage
import com.dzirbel.kotify.ui.page.albums.albumCellImageSize
import com.dzirbel.kotify.ui.page.artists.artistCellImageSize
import com.dzirbel.kotify.util.immutable.countBy
import kotlinx.collections.immutable.PersistentSet

data object ReleaseRadar : Page {
    @Composable
    override fun PageScope.bind() {
        val scope = rememberCoroutineScope { Dispatchers.Computation }

        val savedArtistRepository = LocalSavedArtistRepository.current
        val artistRepository = LocalArtistRepository.current
        val artistAlbumsRepository = LocalArtistAlbumsRepository.current
        val albumRepository = LocalAlbumRepository.current
        val similarArtistsRepository = LocalSimilarArtistsRepository.current

        val recommendationEngine = ArtistRecommendationEngine(artistRepository, similarArtistsRepository)

        val displayedAlbumTypes = remember { mutableStateOf(persistentSetOf(AlbumType.ALBUM)) }

        // accumulate saved artist IDs, never removing them from the library so that the artist does not disappear from
        // the grid when removed (to make it easy to add them back if it was an accident)
        val displayedLibraryFlow = remember {
            // TODO do not propagate error/not found states
            savedArtistRepository.library.runningFoldIn(scope) { accumulator, value ->
                value?.map { it.plus(accumulator?.cachedValue?.ids) }
            }
        }

        val artistsAdapter = rememberListAdapterState(defaultSort = ArtistNameProperty, scope = scope) {
            displayedLibraryFlow.flatMapLatestIn(scope) { cacheState ->
                val ids = cacheState?.cachedValue?.ids
                if (ids == null) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    artistRepository.statesOf(ids)
                        .combinedStateWhenAllNotNull { it?.cachedValue }
                }
            }
        }


        val similarArtistsAdapter = rememberListAdapterState(scope = scope) {
            val similarArtistIds = recommendationEngine.similarArtistsRecommendation(artistsAdapter.value.map { it }).filter { it.second.count() > 2 }
            artistRepository.statesOf(similarArtistIds.map { it.first.id })
                .combinedStateWhenAllNotNull { it?.cachedValue }
        }

        val similarArtistsAlbums = rememberListAdapterState(
            source = { scope ->
                artistAlbumsRepository.statesOf(similarArtistsAdapter.value.map { it.id })
                    .combinedStateWhenAllNotNull { it?.cachedValue }
            }
        )


        val imageSize = Dimens.contentImage.toImageSize()
        val artistsAlbums = rememberListAdapterState(
            source = { scope ->
                artistAlbumsRepository.statesOf(artistsAdapter.value.map { it.id })
                    .combinedStateWhenAllNotNull { it?.cachedValue }
            }
        )

        val albumIds = artistsAlbums.value.flatMap { it.artistAlbums }.map { it.album }
            .filter { checkRelease(it.parsedReleaseDate!!) }.distinct()
            .filter { it.albumType != AlbumType.COMPILATION }

        val similarAlbumIds = similarArtistsAlbums.value.flatMap { it.artistAlbums }.map { it.album }
            .filter { checkRelease(it.parsedReleaseDate!!) }.distinct()
            .filter { it.albumType != AlbumType.COMPILATION }

        val newReleases = rememberListAdapterState(
            defaultSort = AlbumNameProperty,
            defaultFilter = filterFor(displayedAlbumTypes.value),
            scope = scope
        ) {
            displayedLibraryFlow.flatMapLatestIn(scope) { cacheState ->
                if (albumIds.isEmpty()) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    albumRepository.statesOf(albumIds.map { it.id })
                        .combinedStateWhenAllNotNull { it?.cachedValue }
                        .onEachIn(scope) { albums ->
                            albums?.requestBatched(
                                transactionName = { "load $it albums images for $albumCellImageSize" },
                                extractor = { it.imageUrlFor(imageSize) },
                            )
                        }
                }
            }
        }

        val similarNewReleases = rememberListAdapterState(
            defaultSort = AlbumNameProperty,
            defaultFilter = filterFor(displayedAlbumTypes.value),
            scope = scope
        ) {
            displayedLibraryFlow.flatMapLatestIn(scope) { cacheState ->
                if (similarAlbumIds.isEmpty()) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    albumRepository.statesOf(similarAlbumIds.map { it.id })
                        .combinedStateWhenAllNotNull { it?.cachedValue }
                        .onEachIn(scope) { albums ->
                            albums?.requestBatched(
                                transactionName = { "load $it albums images for $albumCellImageSize" },
                                extractor = { it.imageUrlFor(imageSize) },
                            )
                            albums?.requestBatched(
                                transactionName = { "$it album artists" },
                                extractor = { LazyTransactionStateFlow("$it album artists") { it.artists } },
                            )
                        }
                }
            }
        }
        LocalSavedAlbumRepository.current.rememberSavedStates(newReleases.value) { it.id }

        DisplayVerticalScrollPage(
            title = "Release Radar",
            header = {
                ReleaseRadarHeader(
                    albums = newReleases,
                    displayedAlbumTypes = displayedAlbumTypes.value,
                    setDisplayedAlbumTypes = { types ->
                        displayedAlbumTypes.value = types
                        newReleases.withFilter(filterFor(types))
                        similarNewReleases.withFilter(filterFor(types))
                    },
                )
            },
        ) {
            if (newReleases.derived { it.hasElements }.value) {
                Text("Followed Artists", style = MaterialTheme.typography.h5)
                Grid(
                    elements = newReleases.value,
                    edgePadding = PaddingValues(
                        start = Dimens.space5 - Dimens.space3,
                        end = Dimens.space5 - Dimens.space3,
                        bottom = Dimens.space3,
                    ),
                ) { _, album ->
                    AlbumCell(
                        album = album,
                        onClick = { pageStack.mutate { to(AlbumPage(albumId = album.id)) } },
                        ratingRepository = LocalRatingRepository.current,
                        fullReleaseDate = true,
                        showArtist = true,
                    )
                }
            }
            if (similarNewReleases.derived { it.hasElements }.value) {
                Text("Recommended Artists", style = MaterialTheme.typography.h5)
                Grid(
                    elements = similarNewReleases.value,
                    edgePadding = PaddingValues(
                        start = Dimens.space5 - Dimens.space3,
                        end = Dimens.space5 - Dimens.space3,
                        bottom = Dimens.space3,
                    ),
                ) { _, artistAlbum ->
                    AlbumCell(
                        album = artistAlbum,
                        onClick = { pageStack.mutate { to(AlbumPage(albumId = artistAlbum.id)) } },
                        ratingRepository = LocalRatingRepository.current,
                        fullReleaseDate = true,
                        showArtist = true,
                    )
                }
            }
            if (!similarNewReleases.derived { it.hasElements }.value and !newReleases.derived { it.hasElements }.value) {
                PageLoadingSpinner()
            }
        }
    }
}

fun checkRelease(date: ReleaseDate): Boolean {
    val year = date.year
    val month = when {
        date.month is Int -> date.month!!
        else -> 1
    }
    val day = when {
        date.day is Int -> date.day!!
        else -> 1
    }

    return LocalDate.of(year, month, day).isAfter(LocalDate.now().minusWeeks(4))
}

private fun filterFor(albumTypes: Set<AlbumType>): ((AlbumViewModel) -> Boolean)? {
    return if (albumTypes.isNotEmpty()) {
        { album -> albumTypes.contains(album.albumType) }
    } else {
        null
    }
}

@Composable
private fun ReleaseRadarHeader(
    albums: ListAdapterState<AlbumViewModel>,
    displayedAlbumTypes: PersistentSet<AlbumType>,
    setDisplayedAlbumTypes: (PersistentSet<AlbumType>) -> Unit,
) {

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space5, vertical = Dimens.space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Column {
            Text("Release Radar", style = MaterialTheme.typography.h4)

            Row(
                modifier = Modifier.padding(Dimens.space4),
                horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
            ) {
                val albumTypeCounts = albums.derived { it.countBy { artistAlbum -> artistAlbum.albumType } }.value
                AlbumTypePicker(
                    albumTypeCounts = albumTypeCounts,
                    albumTypes = displayedAlbumTypes,
                    onSelectAlbumTypes = { setDisplayedAlbumTypes(it) },
                )
            }
        }
    }
}