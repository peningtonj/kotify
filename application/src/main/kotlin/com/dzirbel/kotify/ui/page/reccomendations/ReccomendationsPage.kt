package com.dzirbel.kotify.ui.page.reccomendations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Text
import androidx.compose.runtime.*
import com.dzirbel.kotify.repository.CacheState
import com.dzirbel.kotify.repository.artist.ArtistRecommendationEngine
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow.Companion.requestBatched
import com.dzirbel.kotify.ui.*
import com.dzirbel.kotify.ui.artist.ArtistCell
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

data object ReccommendationsPage : Page {
    @Composable
    override fun PageScope.bind() {
        val savedArtistRepository = LocalSavedArtistRepository.current
        val artistRepository = LocalArtistRepository.current

        val recommendationEngine = ArtistRecommendationEngine()

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

        val similarArtistIds = recommendationEngine.similarArtistsRecommendation(savedArtistsAdapter.value.orEmpty()).slice(0..10)
        val similarArtistsAdapter = rememberListAdapterState(scope = scope) {
                artistRepository.statesOf(similarArtistIds.map { it.first.id })
                    .combinedStateWhenAllNotNull { it?.cachedValue }
            }



        var selectedArtistIndex: Int? by remember { mutableStateOf(null) }

        DisplayVerticalScrollPage(
            title = "Recommendations",
            header = {
            },
        ) {
            Grid(
                elements = similarArtistsAdapter.value,
                edgePadding = PaddingValues(
                    start = Dimens.space5 - Dimens.space3,
                    end = Dimens.space5 - Dimens.space3,
                    bottom = Dimens.space3,
                ),
                selectedElementIndex = selectedArtistIndex,
                detailInsertContent = { _, artist ->
                    ArtistDetailInsert(artist = artist)
                },
                cellContent = { index, artist ->
                    Column() {
                        ArtistCell(
                            artist = artist,
                            imageSize = com.dzirbel.kotify.ui.page.artists.artistCellImageSize,
                            onClick = {
                                pageStack.mutate { to(ArtistPage(artistId = artist.id)) }
                            },
                            onMiddleClick = {
                                selectedArtistIndex = index.takeIf { it != selectedArtistIndex }
                            },
                        )
                        val similarToCount = similarArtistIds.find { it.first.id == artist.id }?.second.toString()
                        Text("Similar to $similarToCount artists")
                    }
                },
            )
        }
    }
}
