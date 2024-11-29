package com.dzirbel.kotify.ui.page.reccomendations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.*
import com.dzirbel.kotify.repository.CacheState
import com.dzirbel.kotify.repository.artist.ArtistRecommendationEngine
import com.dzirbel.kotify.repository.artist.ArtistViewModel
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow.Companion.requestBatched
import com.dzirbel.kotify.ui.*
import com.dzirbel.kotify.ui.artist.ArtistCell
import com.dzirbel.kotify.ui.components.adapter.ListAdapter
import com.dzirbel.kotify.ui.components.adapter.rememberListAdapterState
import com.dzirbel.kotify.ui.components.grid.Grid
import com.dzirbel.kotify.ui.components.toImageSize
import com.dzirbel.kotify.ui.page.Page
import com.dzirbel.kotify.ui.page.PageScope
import com.dzirbel.kotify.ui.page.artist.ArtistPage
import com.dzirbel.kotify.ui.page.artists.ArtistDetailInsert
import com.dzirbel.kotify.ui.page.artists.artistCellImageSize
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.util.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data object ReccommendationsPage : Page {
    @Composable
    override fun PageScope.bind() {
        val savedArtistRepository = LocalSavedArtistRepository.current
        val artistRepository = LocalArtistRepository.current
        val similarArtistsRepository = LocalSimilarArtistsRepository.current

        val recommendationEngine = ArtistRecommendationEngine(artistRepository, similarArtistsRepository)

        val scope = rememberCoroutineScope { Dispatchers.Computation }
        val savedArtists = remember {
            // TODO do not propagate error/not found states
            savedArtistRepository.library.runningFoldIn(scope) { accumulator, value ->
                value?.map { it.plus(accumulator?.cachedValue?.ids) }
            }
        }

        val imageSize = artistCellImageSize.toImageSize()

        val savedArtistsAdapter =
            savedArtists.flatMapLatestIn(scope) { cacheState ->
                val ids = cacheState?.cachedValue?.ids
                if (ids == null) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    artistRepository.statesOf(ids)
                        .combinedStateWhenAllNotNull { it?.cachedValue }
                        .onEachIn(scope) { artists ->
                            artists?.requestBatched(
                                transactionName = { "load $it artist images for $artistCellImageSize" },
                                extractor = { it.imageUrlFor(imageSize) },
                            )
                        }
                }
            }

        val similarArtistIds =
                ListAdapter.of(
                recommendationEngine.similarArtistsRecommendation(savedArtistsAdapter.value.orEmpty())
                    .sortedBy { it.second.count() * -1 }
                    .slice(0..10)
                    .map { it.first to "Similar to ${it.second.count()} artists" }
                )

        val similarToFavourite = remember {
            mutableStateOf<ListAdapter<Pair<ArtistViewModel, String>>>(
                ListAdapter.of(
                    emptyList()
                )
            )
        }

        LaunchedEffect(Unit) {
            similarToFavourite.value = ListAdapter.of(recommendationEngine.similiarToFavourites())
        }

        var selectedArtistIndex: Int? by remember { mutableStateOf(null) }


        DisplayVerticalScrollPage(
            title = "Recommendations",
            header = {
            },
        ) {
            Grid(
                elements = similarArtistIds.plusElements(similarToFavourite.value.toList()),
                edgePadding = PaddingValues(
                    start = Dimens.space5 - Dimens.space3,
                    end = Dimens.space5 - Dimens.space3,
                    bottom = Dimens.space3,
                ),
                selectedElementIndex = selectedArtistIndex,
                detailInsertContent = { _, artist ->
                    ArtistDetailInsert(artist = artist.first)
                },
                cellContent = { index, artist ->
                    Column {
                        ArtistCell(
                            artist = artist.first,
                            imageSize = artistCellImageSize,
                            onClick = {
                                pageStack.mutate { to(ArtistPage(artistId = artist.first.id)) }
                            },
                            onMiddleClick = {
                                selectedArtistIndex = index.takeIf { it != selectedArtistIndex }
                            },
                        )
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Text(artist.second)
                        }
                    }
                },
            )
        }
    }
}
