package com.dzirbel.kotify.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.dzirbel.kotify.network.FullSpotifyTrackOrEpisode
import com.dzirbel.kotify.network.model.FullSpotifyTrack
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.player.Player
import com.dzirbel.kotify.repository.playlist.AlbumPlaylistViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistTrackViewModel
import com.dzirbel.kotify.repository.rating.RatingRepository
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.LocalPlayer
import com.dzirbel.kotify.ui.LocalSavedAlbumRepository
import com.dzirbel.kotify.ui.components.Interpunct
import com.dzirbel.kotify.ui.components.LoadedImage
import com.dzirbel.kotify.ui.components.PlayButton
import com.dzirbel.kotify.ui.components.ToggleSaveButton
import com.dzirbel.kotify.ui.components.star.StarRating
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.KotifyColors
import com.dzirbel.kotify.ui.util.instrumentation.instrument

@Composable
fun AlbumCell(
    album: AlbumViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ratingRepository: RatingRepository,
    firstTrack: PlaylistTrackViewModel? = null,
    albumPlaylist: AlbumPlaylistViewModel? = null,
    showRating: Boolean = true,
    fullReleaseDate: Boolean = false,
    showArtist: Boolean = false,
) {

    Column(
        modifier = modifier
            .instrument()
            .clickable(onClick = onClick)
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        LoadedImage(album, size = Dimens.contentImage, modifier = Modifier.align(Alignment.CenterHorizontally))

        Row(
            modifier = Modifier.widthIn(max = Dimens.contentImage),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Text(text = album.name, modifier = Modifier.weight(1f))

            ToggleSaveButton(repository = LocalSavedAlbumRepository.current, id = album.id)

            val player = LocalPlayer.current
            val currentTrackState = player.currentItem.collectAsState()

            if ((albumPlaylist != null) and (currentTrackState.value is FullSpotifyTrack)) {
                albumPlaylistPlayIcon(
                    album = album,
                    currentTrackState = currentTrackState,
                    player = player,
                    firstTrack = firstTrack,
                    albumPlaylist = albumPlaylist
                )
            } else {
                PlayButton(context = Player.PlayContext.album(album), size = Dimens.iconSmall)
            }
        }

        if (showArtist) {
            Row(
                modifier = Modifier.widthIn(max = Dimens.contentImage),
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    var artists = album.artists.collectAsState().value?.sortedBy { it.popularity }?.reversed()
                    val artistsSize = artists?.size ?: 0
                    if (artistsSize > 3) {
                        artists = artists?.take(3)
                    }
                    Text(text = artists?.map{ it.name }?.joinToString(separator = ", ") { it }.toString())
                }
            }
        }
        Row(
            modifier = Modifier.widthIn(max = Dimens.contentImage),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                album.albumType?.let { albumType ->
                    CachedIcon(
                        name = albumType.iconName,
                        size = Dimens.iconSmall,
                        contentDescription = albumType.displayName,
                    )
                }

                album.parsedReleaseDate?.let { releaseDate ->
                    Text(text = if(fullReleaseDate) releaseDate.toString() else releaseDate.year.toString())
                }

                if (album.parsedReleaseDate != null && album.totalTracks != null) {
                    Interpunct()
                }

                album.totalTracks?.let { totalTracks ->
                    Text("$totalTracks tracks")
                }
            }
        }

        if (showRating) {
            StarRating(
                rating = ratingRepository.ratingStateOf(id = album.id).collectAsState().value,
                onRate = { rating -> ratingRepository.rate(id = album.id, rating = rating) },
            )
        }
    }
}

@Composable
fun albumPlaylistPlayIcon(
    album: AlbumViewModel,
    currentTrackState : State<FullSpotifyTrackOrEpisode?>,
    player: Player,
    firstTrack: PlaylistTrackViewModel? = null,
    albumPlaylist: AlbumPlaylistViewModel? = null,
) {
    val playing = remember(album.id) {
        derivedStateOf { (currentTrackState.value as FullSpotifyTrack).album.id == album.id }
    }
    if (playing.value) {
        CachedIcon(
            name = "volume-up",
            size = Dimens.iconSmall,
            contentDescription = "Playing",
            tint = MaterialTheme.colors.primary,
        )
    } else if (firstTrack != null) {
        IconButton(
            onClick = {
                player.play(
                    context = Player.PlayContext.playlistTrack(
                        albumPlaylist!!,
                        firstTrack.indexOnPlaylist
                    )
                )
                player.setShuffle(false)
            },
            enabled = Player.PlayContext.playlistTrack(
                albumPlaylist!!,
                firstTrack.indexOnPlaylist
            ) != null,
        ) {
            CachedIcon(
                name = "play-circle-outline",
                size = Dimens.iconSmall,
                contentDescription = "Play",
            )
        }
    } else {
            CachedIcon(
                name = "error",
                size = Dimens.iconSmall,
                contentDescription = "No First Track",

                )
    }
}

@Composable
fun SmallAlbumCell(album: AlbumViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.cornerSize))
            .clickable(onClick = onClick)
            .padding(Dimens.space2),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Box {
            LoadedImage(album, modifier = Modifier.align(Alignment.Center), size = Dimens.contentImageSmall)

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = MaterialTheme.colors.background.copy(alpha = KotifyColors.current.overlayAlpha),
                        shape = RoundedCornerShape(size = Dimens.cornerSize),
                    )
                    .padding(Dimens.space1),
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                ToggleSaveButton(repository = LocalSavedAlbumRepository.current, id = album.id)

                PlayButton(context = Player.PlayContext.album(album), size = Dimens.iconSmall)
            }
        }

        Text(
            text = album.name,
            style = MaterialTheme.typography.overline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = Dimens.contentImageSmall),
        )
    }
}
