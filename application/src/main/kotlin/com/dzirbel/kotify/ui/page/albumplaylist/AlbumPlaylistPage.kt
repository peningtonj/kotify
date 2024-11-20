package com.dzirbel.kotify.ui.page.albumplaylist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.dzirbel.kotify.repository.Repository
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.albumplaylist.AlbumPlaylistAlbumViewModel
import com.dzirbel.kotify.repository.playlist.AlbumPlaylistViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistTrackViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistTracksRepository
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow.Companion.requestBatched
import com.dzirbel.kotify.ui.*
import com.dzirbel.kotify.ui.album.AlbumCell
import com.dzirbel.kotify.ui.components.*
import com.dzirbel.kotify.ui.components.adapter.*
import com.dzirbel.kotify.ui.page.Page
import com.dzirbel.kotify.ui.page.PageScope
import com.dzirbel.kotify.ui.page.album.AlbumPage
import com.dzirbel.kotify.ui.page.albums.albumCellImageSize
import com.dzirbel.kotify.ui.properties.*
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.derived
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.util.coroutines.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyGridState
import org.burnoutcrew.reorderable.reorderable

data class AlbumPlaylistPage(private val albumPlaylistId: String) : Page {
    @Composable
    override fun PageScope.bind() {

        val albumPlaylistAlbumsRepository = LocalAlbumPlaylistAlbumsRepository.current
        val playlistTracksRepository = LocalPlaylistTracksRepository.current
        val albumPlaylist =
            LocalAlbumPlaylistRepository.current.stateOf(id = albumPlaylistId).collectAsState().value?.cachedValue
        val transitionAlbumId = albumPlaylist?.nextAlbumTrack?.albumId
        val nextAlbumTrackAlbum =
            LocalAlbumRepository.current.stateOf(id = transitionAlbumId!!).collectAsState().value?.cachedValue

        val imageSize = albumCellImageSize.toImageSize()

        val albumPlaylistAlbumsAdapter = rememberListAdapterState(
            key = albumPlaylistId,
            source = { scope ->
                albumPlaylistAlbumsRepository.stateOf(id = albumPlaylistId).mapIn(scope) { cacheState ->
                    cacheState?.cachedValue?.also { albumPlaylistAlbums ->
                        val albums = albumPlaylistAlbums.mapNotNull { it.album }
                        albums.requestBatched(
                            transactionName = { "$it playlist album" },
                            extractor = { it.imageUrlFor(imageSize) },
                        )
                    }
                }
            },
        )

        val playlistTracksAdapter = rememberListAdapterState(
            key = albumPlaylistId,
            defaultSort = PlaylistDirectSortIndexProperty,
            source = { scope ->
                playlistTracksRepository.stateOf(id = albumPlaylistId).mapIn(scope) { cacheState ->
                    cacheState?.cachedValue?.also { playlistTracks ->
                        // request albums and artist for tracks (but not episodes)
                        val tracks = playlistTracks.mapNotNull { it.track }
                        tracks.requestBatched(
                            transactionName = { "$it playlist track albums" },
                            extractor = { it.album },
                        )
                        tracks.requestBatched(
                            transactionName = { "$it playlist track artists" },
                            extractor = { it.artists },
                        )
                    }
                }
            },
        )

        var transitionInPlaylist = playlistTracksAdapter.value.map {track -> track.track?.id}.contains(albumPlaylist.nextAlbumTrackId)
        val playlistSnapshot = playlistTracksAdapter.value.toList()



        DisplayNormalPage(
            title = "Included Albums",
            header = {
                AlbumPlaylistHeader(
                    albumPlaylistId = albumPlaylistId,
                    albumPlaylist = albumPlaylist,
                    nextAlbumTrackAlbum = nextAlbumTrackAlbum
                )
            },
        ) {
            AlbumPlaylistReorderButton(
                reorder = {
                    playlistTracksRepository.reorder(
                        playlistId = albumPlaylistId,
                        tracks = playlistTracksAdapter.value.toList(),
                        comparator = requireNotNull(playlistTracksAdapter.value.sorts).asComparator(),
                    )
                },
                // TODO doesn't seem quite right... just revert to order by index on playlist?
                onReorderFinish = { albumPlaylistAlbumsAdapter.mutate { withSort(persistentListOf()) } },
            )
            AlbumPlaylistButton(
                func = {
                    playlistTracksRepository.syncToRemote(
                        playlistId = albumPlaylistId,
                        tracks = playlistTracksAdapter.value.toList(),
                        remote = playlistSnapshot
                    )
                },
                // TODO doesn't seem quite right... just revert to order by index on playlist?
                onFinish = { albumPlaylistAlbumsAdapter.mutate { withSort(persistentListOf()) } },
                enabledText = "Sync with remote",
            )
            AlbumPlaylistButton(
                func = {
                    playlistTracksRepository.addAtIndexes(
                        playlistId = albumPlaylistId,
                        track = albumPlaylist.nextAlbumTrack!!,
                        indexOnPlaylist = albumPlaylistAlbumsAdapter.value.map { album ->
                            album.album?.totalTracks!!
                        }.runningFold(-1) { acc, num -> acc + num + 1 }.drop(1)
                    )
                },
                // TODO doesn't seem quite right... just revert to order by index on playlist?
                onFinish = {
                    transitionInPlaylist = true
                },
                enabledText = "Add Transition Track",
                enabled = !transitionInPlaylist
            )
            AlbumPlaylistButton(
                func = {
                    playlistTracksRepository.removeTrack(
                        playlistId = albumPlaylistId,
                        track = albumPlaylist.nextAlbumTrack!!,
                    )
                },
                // TODO doesn't seem quite right... just revert to order by index on playlist?
                onFinish = {
                    transitionInPlaylist = false
                },
                enabledText = "Remove Transition Track",
                enabled = transitionInPlaylist

            )
            if (albumPlaylistAlbumsAdapter.derived { it.hasElements }.value) {
                val data =
                    remember { mutableStateOf(List(albumPlaylistAlbumsAdapter.value.size) { id -> albumPlaylistAlbumsAdapter.value[id]!! }) }
                val state = rememberReorderableLazyGridState(
                    onMove = { from, to ->
                        data.value = data.value.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        playlistTracksAdapter.value.map { track ->
                            track.apply {
                                directSortIndex = data.value.map { album -> album.album?.id }
                                    .indexOf(track.track?.album?.value?.id) * 100 + track.track?.trackNumber!!
                            }
                        }
                        playlistTracksAdapter.value.filter { track -> track.track?.id == albumPlaylist.nextAlbumTrack?.id }
                            .mapIndexed { index, track ->
                                track.apply {
                                    directSortIndex = index * 100 - 1
                                }
                            }
                    })
                AlbumPlaylistShuffle(
                    playlistTracksAdapter = playlistTracksAdapter,
                    albums = data,
                    albumPlaylist = albumPlaylist
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = state.gridState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.reorderable(state)
                ) {
                    items(data.value, { it }) { album ->
                        ReorderableItem(state, key = album, defaultDraggingModifier = Modifier) { isDragging ->
                            val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)

                            AlbumCell(
                                album = album.album!!,
                                onClick = { pageStack.mutate { to(AlbumPage(albumId = album.album!!.id)) } },
                                modifier = Modifier.detectReorderAfterLongPress(state)
                                    .shadow(elevation.value)
                            )
                        }
                    }
                }
            } else {
                PageLoadingSpinner()
            }
        }
    }
}

@Composable
private fun AlbumPlaylistHeader(
    albumPlaylistId: String,
    albumPlaylist: AlbumPlaylistViewModel?,
    nextAlbumTrackAlbum: AlbumViewModel?
) {
    Row(
        modifier = Modifier.padding(Dimens.space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        LoadedImage(albumPlaylist)
        if (albumPlaylist != null) {

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                Text(albumPlaylist.name, style = MaterialTheme.typography.h3)


                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Interpunct()
                    InvalidateButton(
                        repository = LocalAlbumPlaylistAlbumsRepository.current,
                        id = albumPlaylistId,
                        icon = "queue-music",
                    )
                }
            }
            Spacer(Modifier.width(Dimens.space5))

            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Text("Change Album Sound Effect")
                }
                if (albumPlaylist.nextAlbumTrack != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        albumPlaylist.nextAlbumTrack?.let { Text(it.name) }
                    }
                    Box {
                        LoadedImage(
                            nextAlbumTrackAlbum,
                            modifier = Modifier.align(Alignment.Center),
                            size = Dimens.contentImageMed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPlaylistShuffle(
    playlistTracksAdapter: ListAdapterState<PlaylistTrackViewModel>,
    albums:  MutableState<List<AlbumPlaylistAlbumViewModel>>,
    albumPlaylist: AlbumPlaylistViewModel
) {
    SimpleTextButton(
        onClick = {
            albums.value = albums.value.toMutableList().shuffled()
            val albumOrder = albums.value.map { album -> album.album?.id }

            playlistTracksAdapter.value.map { track ->
                track.directSortIndex = (albumOrder.indexOf(track.track?.album?.value?.id) * 100) + track.track?.trackNumber!!
            }

            playlistTracksAdapter.value.filter { track -> track.track?.id == albumPlaylist.nextAlbumTrack?.id }
                .mapIndexed { index, track ->
                    track.apply {
                        directSortIndex = (index + 1) * 100 - 1
                    }
                }
            println("hey")
        },
    ) {
        Text("Shuffle")
    }

}


@Composable
private fun AlbumPlaylistReorderButton(
    reorder: () -> Flow<PlaylistTracksRepository.PlaylistReorderState>,
    onReorderFinish: () -> Unit,
    enabled: Boolean = true,
) {
    val reorderState = remember { mutableStateOf<PlaylistTracksRepository.PlaylistReorderState?>(null) }

    SimpleTextButton(
        enabled = enabled && reorderState.value == null,
        onClick = {
            // TODO prevent sort changes while reordering?
            Repository.applicationScope.launch {
                reorder()
                    .onCompletion {
                        reorderState.value = null
                        onReorderFinish()
                    }
                    .collect { state -> reorderState.value = state }
            }
        },
    ) {
        val text = when (val state = reorderState.value) {
            PlaylistTracksRepository.PlaylistReorderState.Calculating -> "Calculating"

            is PlaylistTracksRepository.PlaylistReorderState.Reordering ->
                "Reordering ${state.completedOps} / ${state.totalOps}"

            PlaylistTracksRepository.PlaylistReorderState.Verifying -> "Verifying"

            null -> "Set current order as playlist order"
        }

        Text(text)
    }
}


@Composable
private fun AlbumPlaylistButton(
    func: () -> Flow<PlaylistTracksRepository.PlaylistSyncState>,
    onFinish: () -> Unit,
    enabledText: String,
    enabled: Boolean = true,
) {
    val syncState = remember { mutableStateOf<PlaylistTracksRepository.PlaylistSyncState?>(null) }

    SimpleTextButton(
        enabled = enabled && syncState.value == null,
        onClick = {
            // TODO prevent sort changes while reordering?
            Repository.applicationScope.launch {
                func()
                    .onCompletion {
                        syncState.value = null
                        onFinish()
                    }
                    .collect { state -> syncState.value = state }
            }
        },
    ) {
        val text = when (val state = syncState.value) {
            PlaylistTracksRepository.PlaylistSyncState.Calculating -> "Calculating"

            is PlaylistTracksRepository.PlaylistSyncState.Reordering ->
                "Reordering ${state.completedOps} / ${state.totalOps}"

            PlaylistTracksRepository.PlaylistSyncState.Verifying -> "Verifying"

            null -> enabledText
        }
        Text(text)
    }
}
