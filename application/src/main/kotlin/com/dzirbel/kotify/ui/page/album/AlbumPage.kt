package com.dzirbel.kotify.ui.page.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.repository.CacheState
import com.dzirbel.kotify.repository.CacheStrategy
import com.dzirbel.kotify.repository.Repository
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.player.Player
import com.dzirbel.kotify.repository.playlist.AlbumPlaylistViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistViewModel
import com.dzirbel.kotify.repository.track.TrackRepository
import com.dzirbel.kotify.repository.track.TrackViewModel
import com.dzirbel.kotify.repository.util.LazyTransactionStateFlow.Companion.requestBatched
import com.dzirbel.kotify.ui.*
import com.dzirbel.kotify.ui.components.*
import com.dzirbel.kotify.ui.components.adapter.ListAdapterState
import com.dzirbel.kotify.ui.components.adapter.rememberListAdapterState
import com.dzirbel.kotify.ui.components.star.AverageAlbumRating
import com.dzirbel.kotify.ui.components.table.Column
import com.dzirbel.kotify.ui.components.table.Table
import com.dzirbel.kotify.ui.page.Page
import com.dzirbel.kotify.ui.page.PageScope
import com.dzirbel.kotify.ui.page.artist.ArtistPage
import com.dzirbel.kotify.ui.properties.*
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.derived
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.ui.util.rememberRatingStates
import com.dzirbel.kotify.util.coroutines.combinedStateWhenAllNotNull
import com.dzirbel.kotify.util.coroutines.flatMapLatestIn
import com.dzirbel.kotify.util.coroutines.mapIn
import com.dzirbel.kotify.util.coroutines.onEachIn
import com.dzirbel.kotify.util.immutable.persistentListOfNotNull
import com.dzirbel.kotify.util.takingIf
import com.dzirbel.kotify.util.time.formatMediumDuration
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class AlbumPage(val albumId: String) : Page {
    @Composable
    override fun PageScope.bind() {
        val album = LocalAlbumRepository.current.stateOf(id = albumId).collectAsState().value?.cachedValue

        val albumTracksRepository = LocalAlbumTracksRepository.current

        val playlistRepository = LocalPlaylistRepository.current
        val savedPlaylistRepository = LocalSavedPlaylistRepository.current
        val playlistTracksRepository = LocalPlaylistTracksRepository.current
        val playlists = rememberListAdapterState(defaultSort = PlaylistLibraryOrderProperty) { scope ->
            savedPlaylistRepository.library.flatMapLatestIn(scope) { cacheState ->
                val ids = cacheState?.cachedValue?.ids
                if (ids == null) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    // TODO handle other cache states: shimmer when loading, show errors, etc
                    playlistRepository.statesOf(
                        ids = ids,
                        cacheStrategy = CacheStrategy.EntityTTL(), // do not require a full playlist model
                    ).combinedStateWhenAllNotNull { it?.cachedValue }
                }
            }
        }
        val localTrackRepository = LocalTrackRepository.current

        val albumPlaylistRepository = LocalAlbumPlaylistRepository.current
        val savedAlbumPlaylistRepository = LocalSavedAlbumPlaylistRepository.current
        val albumPlaylists = rememberListAdapterState { scope ->
            savedAlbumPlaylistRepository.library.flatMapLatestIn(scope) { cacheState ->
                val ids = cacheState?.cachedValue?.ids
                if (ids == null) {
                    MutableStateFlow(if (cacheState is CacheState.Error) emptyList() else null)
                } else {
                    // TODO handle other cache states: shimmer when loading, show errors, etc
                    albumPlaylistRepository.statesOf(
                        ids = ids,
                        cacheStrategy = CacheStrategy.EntityTTL(), // do not require a full playlist model
                    ).combinedStateWhenAllNotNull { it?.cachedValue }
                }
            }
        }


        val tracksAdapterState = rememberListAdapterState(
            key = albumId,
            defaultSort = TrackAlbumIndexProperty,
        ) { scope ->
            albumTracksRepository.stateOf(id = albumId)
                .mapIn(scope) { it?.cachedValue?.tracks }
                .onEachIn(scope) { tracks ->
                    tracks?.requestBatched(transactionName = { "album $albumId $it track artists" }) { it.artists }
                }
        }

        LocalRatingRepository.current.rememberRatingStates(tracksAdapterState.value) { it.id }

        val savedTrackRepository = LocalSavedTrackRepository.current
        val ratingRepository = LocalRatingRepository.current
        val trackProperties: PersistentList<Column<TrackViewModel>> = remember(album) {
            persistentListOf(
                TrackPlayingColumn(
                    trackIdOf = { it.id },
                    playContextFromTrack = { track ->
                        album?.let {
                            Player.PlayContext.albumTrack(album = album, index = track.trackNumber)
                        }
                    },
                ),
                TrackAlbumIndexProperty,
                TrackSavedProperty(savedTrackRepository = savedTrackRepository, trackIdOf = { track -> track.id }),
                TrackNameProperty,
                TrackArtistsProperty,
                TrackRatingProperty(ratingRepository = ratingRepository, trackIdOf = { track -> track.id }),
                TrackDurationProperty,
                TrackPopularityProperty,
                TrackQueueColumn(),
            )
        }

        DisplayVerticalScrollPage(
            title = album?.name,
            header = {
                AlbumHeader(albumId = albumId, album = album, adapter = tracksAdapterState)
            },
        ) {
            if (tracksAdapterState.derived { it.hasElements }.value) {
                Column {
                    ScrollableDropdownMenu(
                        playlists = playlists,
                        onOptionSelected = { option ->
                            addAlbumToPlaylist(
                                tracks = tracksAdapterState,
                                playlist = option,
                                trackRepository = localTrackRepository,
                                albumPlaylists = albumPlaylists
                            )
                        },
                    )

                    Table(
                        columns = trackProperties,
                        items = tracksAdapterState.value,
                        onSetSort = { tracksAdapterState.withSort(persistentListOfNotNull(it)) },
                    )
                }
            } else {
                PageLoadingSpinner()
            }
        }
    }
}

@Composable
private fun AlbumHeader(albumId: String, album: AlbumViewModel?, adapter: ListAdapterState<TrackViewModel>) {
    Row(
        modifier = Modifier.padding(Dimens.space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        LoadedImage(album)

        if (album != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                Text(album.name, style = MaterialTheme.typography.h3)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    ToggleSaveButton(
                        repository = LocalSavedAlbumRepository.current,
                        id = albumId,
                        size = Dimens.iconMedium,
                    )

                    PlayButton(context = Player.PlayContext.album(album), size = Dimens.iconMedium)

                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    val artists = album.artists.collectAsState().value
                    LinkedText(
                        onClickLink = { artistId -> pageStack.mutate { to(ArtistPage(artistId = artistId)) } },
                        key = artists,
                    ) {
                        text("By ")
                        if (artists != null) {
                            list(artists) { artist -> link(text = artist.name, link = artist.id) }
                        } else {
                            text("...")
                        }
                    }

                    album.releaseDate?.let { releaseDate ->
                        Interpunct()
                        Text(releaseDate)
                    }

                    Interpunct()
                    InvalidateButton(repository = LocalAlbumRepository.current, id = albumId, icon = "album")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Text("${album.totalTracks} songs")

                    val totalDuration = adapter.derived { adapter ->
                        takingIf(adapter.hasElements) {
                            adapter.sumOf { it.durationMs }.milliseconds.formatMediumDuration()
                        }
                    }

                    Interpunct()
                    Text(totalDuration.value ?: "...")

                    Interpunct()
                    InvalidateButton(
                        repository = LocalAlbumTracksRepository.current,
                        id = albumId,
                        icon = "queue-music",
                    )
                }

                AverageAlbumRating(albumId = albumId)
            }
        }
    }
}

fun addAlbumToPlaylist(
    tracks: ListAdapterState<TrackViewModel>,
    playlist: PlaylistViewModel,
    trackRepository: TrackRepository,
    albumPlaylists: ListAdapterState<AlbumPlaylistViewModel>,
) {
    val albumPlaylist = albumPlaylists.value.find { it.id == playlist.id }
    Repository.applicationScope.launch {
        Spotify.Playlists.addItemsToPlaylist(
            playlistId = playlist.id,
            uris = tracks.value.map { it.uri!! }
        )

        if ((albumPlaylist != null) and (albumPlaylist!!.nextAlbumTrack != null)) {
            val transitionTrack = trackRepository.stateOf(albumPlaylist.nextAlbumTrackId!!)
                .first { it?.cachedValue != null }
                ?.cachedValue
            Spotify.Playlists.addItemsToPlaylist(
                playlistId = playlist.id,
                uris = listOf(transitionTrack!!.uri!!)
            )
        }
    }
}

