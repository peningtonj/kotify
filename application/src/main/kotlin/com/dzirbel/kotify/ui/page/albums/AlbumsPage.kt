package com.dzirbel.kotify.ui.page.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.db.model.AlbumType
import com.dzirbel.kotify.repository.CacheState
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.artist.ArtistViewModel
import com.dzirbel.kotify.repository.genre.GenreViewModel
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow.Companion.requestBatched
import com.dzirbel.kotify.ui.*
import com.dzirbel.kotify.ui.album.AlbumCell
import com.dzirbel.kotify.ui.components.*
import com.dzirbel.kotify.ui.components.adapter.*
import com.dzirbel.kotify.ui.components.adapter.filters.FilterByValueList
import com.dzirbel.kotify.ui.components.grid.Grid
import com.dzirbel.kotify.ui.page.Page
import com.dzirbel.kotify.ui.page.PageScope
import com.dzirbel.kotify.ui.page.album.AlbumPage
import com.dzirbel.kotify.ui.properties.*
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.derived
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.ui.util.rememberStates
import com.dzirbel.kotify.util.coroutines.Computation
import com.dzirbel.kotify.util.coroutines.combinedStateWhenAllNotNull
import com.dzirbel.kotify.util.coroutines.flatMapLatestIn
import com.dzirbel.kotify.util.coroutines.onEachIn
import com.dzirbel.kotify.util.coroutines.runningFoldIn
import com.dzirbel.kotify.util.immutable.countBy
import com.dzirbel.kotify.util.immutable.orEmpty
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.reflect.typeOf

val albumCellImageSize = Dimens.contentImage

data object AlbumsPage : Page {
    @Composable
    override fun PageScope.bind() {
        val scope = rememberCoroutineScope { Dispatchers.Computation }

        val savedAlbumRepository = LocalSavedAlbumRepository.current
        val albumRepository = LocalAlbumRepository.current
        val ratingRepository = LocalRatingRepository.current

        val displayedLibraryFlow = remember {
            // TODO do not propagate error/not found states
            savedAlbumRepository.library.runningFoldIn(scope) { accumulator, value ->
                value?.map { it.plus(accumulator?.cachedValue?.ids) }
            }
        }

        val imageSize = albumCellImageSize.toImageSize()
        val albumsAdapter = rememberListAdapterState(defaultSort = AlbumNameProperty, scope = scope) {
            displayedLibraryFlow.flatMapLatestIn(scope) { cacheState ->
                val ids = cacheState?.cachedValue?.ids
                if (ids == null) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    albumRepository.statesOf(ids).combinedStateWhenAllNotNull { it?.cachedValue }
                        .onEachIn(scope) { albums ->
                            albums?.requestBatched(
                                transactionName = { "load $it albums images for $albumCellImageSize" },
                                extractor = { it.imageUrlFor(imageSize) },
                            )
                        }
                }
            }
        }

        val albumIds = displayedLibraryFlow.collectAsState().value?.cachedValue?.ids
        LocalAlbumTracksRepository.current.rememberStates(albumIds)

        val albumProperties = remember(albumIds) {
            persistentListOf(
                AlbumNameProperty,
                AlbumRatingProperty(
                    ratings = albumIds?.associateWith { albumId ->
                        ratingRepository.averageRatingStateOfAlbum(albumId = albumId, scope = scope)
                    },
                ),
                AlbumReleaseDateProperty,
                AlbumTypeDividableProperty,
                AlbumTotalTracksProperty,
            )
        }

        val artistsMap = remember(scope) {
            mutableStateMapOf<String, List<ArtistViewModel>?>()
        }

        val artistRepository = LocalArtistRepository.current
        artistsMap.mapValues { (_, artists) ->
            artists?.forEach { artist ->
                if (artist.fullUpdatedTime == null) {
                    artistRepository.refreshFromRemote(artist.id)
                }
            }
        }

        val genreMap = remember(scope) {
            mutableStateMapOf<String, List<GenreViewModel>?>()
        }

// Launch a coroutine to observe artist details for each album
        LaunchedEffect(albumsAdapter.value) {
            albumsAdapter.value.forEach { album ->
                launch {
                    album.artists.collect { artistList ->
                        artistsMap[album.id] = artistList
                        artistList?.map { artist ->
                            artist.genres.collect { genreList ->
                                genreMap[artist.id] = genreList
                            }
                        }

                    }
                }
            }
        }

// Use artistsMap to access resolved artist details
        val albumGenreFilterProperty = remember {
            AlbumGenreFilterProperty(
                title = "Album Genres",
                genres = mutableListOf() // Start with an empty list
            )
        }

        LaunchedEffect(Unit) {
            snapshotFlow { genreMap.values.flatMap { it.orEmpty() } }
                .collect { flattenedGenres ->
                    val newGenres = flattenedGenres
                        .groupBy { it.name }
                        .filterValues { it.size > 5 }
                        .keys
                        .toList()

                    albumGenreFilterProperty.addValues(newGenres.toSet())
                }
        }


        // Create the AlbumGenreFilterProperty upfront

        val filterableAlbumProperties: PersistentList<FilterableProperty<AlbumViewModel>> =
            remember(albumIds) {
                persistentListOf(
                    AlbumTypeFilterProperty(
                        title = "Album Types",
                        types = listOf(AlbumType.ALBUM, AlbumType.EP, AlbumType.SINGLE)
                    ), AlbumRatingFilterProperty(
                        title = "Album Ratings",
                        ratings = (0 .. 5).toList(),
                        ratingRepository = ratingRepository),
                    albumGenreFilterProperty
                )
            }

        DisplayVerticalScrollPage(
            title = "Saved Albums",
            header = {
                AlbumsPageHeader(albumsAdapter = albumsAdapter, albumProperties = albumProperties)
            },
        ) {
            if (albumsAdapter.derived { it.hasElements }.value) {
                AlbumFilterSection(albumsAdapter, filterableAlbumProperties)
                Grid(
                    elements = albumsAdapter.value,
                    edgePadding = PaddingValues(
                        start = Dimens.space5 - Dimens.space3,
                        end = Dimens.space5 - Dimens.space3,
                        bottom = Dimens.space3,
                    ),
                    cellContent = { _, album ->
                        AlbumCell(
                            album = album,
                            onClick = { pageStack.mutate { to(AlbumPage(albumId = album.id)) } },
                            ratingRepository = LocalRatingRepository.current,
                        )
                    },
                )
            } else {
                PageLoadingSpinner()
            }
        }
    }
}

@Composable
private fun AlbumFilterSection(
    albumsAdapter: ListAdapterState<AlbumViewModel>,
    filterableAlbumProperties: PersistentList<FilterableProperty<AlbumViewModel>>,
) {


    FilterOptions(
        filterableOptions = filterableAlbumProperties,
        onSetFilter = albumsAdapter::withFilters,
    )
}

@Composable
private fun AlbumsPageHeader(
    albumsAdapter: ListAdapterState<AlbumViewModel>,
    albumProperties: PersistentList<AdapterProperty<AlbumViewModel>>,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space5, vertical = Dimens.space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Column {
            Text("Albums", style = MaterialTheme.typography.h4, maxLines = 1)

            if (albumsAdapter.derived { it.hasElements }.value) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        val size = albumsAdapter.derived { it.size }.value
                        Text("$size saved albums", maxLines = 1)

                        Interpunct()
                    }

                    LibraryInvalidateButton(LocalSavedAlbumRepository.current)
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            DividerSelector(
                dividableProperties = albumProperties.dividableProperties(),
                currentDivider = albumsAdapter.derived { it.divider }.value,
                onSelectDivider = albumsAdapter::withDivider,
            )

            SortSelector(
                sortableProperties = albumProperties.sortableProperties(),
                sorts = albumsAdapter.derived { it.sorts.orEmpty() }.value,
                onSetSort = albumsAdapter::withSort,
            )
        }
    }
}
