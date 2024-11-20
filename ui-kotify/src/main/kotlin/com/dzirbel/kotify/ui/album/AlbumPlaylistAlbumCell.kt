package com.dzirbel.kotify.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.dzirbel.kotify.network.model.FullSpotifyTrack
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.player.Player
import com.dzirbel.kotify.repository.playlist.AlbumPlaylistViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistTrackViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistViewModel
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.LocalPlayer
import com.dzirbel.kotify.ui.LocalSavedAlbumRepository
import com.dzirbel.kotify.ui.components.Interpunct
import com.dzirbel.kotify.ui.components.LoadedImage
import com.dzirbel.kotify.ui.components.PlayButton
import com.dzirbel.kotify.ui.components.ToggleSaveButton
import com.dzirbel.kotify.ui.components.star.AverageAlbumRating
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.KotifyColors
import com.dzirbel.kotify.ui.util.instrumentation.instrument

@Composable
fun AlbumPlaylistAlbumCell(
    album: AlbumViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    firstTrack: PlaylistTrackViewModel? = null,
    albumPlaylist: AlbumPlaylistViewModel? = null,
    showRating: Boolean = true
) {
    val player = LocalPlayer.current
    val currentTrackState = player.currentItem.collectAsState()
    val playing = when (currentTrackState.value) {
        is FullSpotifyTrack ->
            remember(album.id) {
                derivedStateOf { (currentTrackState.value as FullSpotifyTrack).album.id == album.id }
            }
        else ->
            remember(album.id) {
                derivedStateOf { false }
            }
    }
    if ((albumPlaylist == null) or (firstTrack == null)) {
        return
    }
    val size = Dimens.iconSmall
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

            if (playing.value) {
                CachedIcon(
                    name = "volume-up",
                    size = size,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colors.primary,
                )
            } else {
                IconButton(
                    onClick = { player.play(context = Player.PlayContext.playlistTrack(albumPlaylist!!, firstTrack?.indexOnPlaylist!!)) },
                    enabled = Player.PlayContext.playlistTrack(albumPlaylist!!, firstTrack?.indexOnPlaylist!!) != null,
                ) {
                    CachedIcon(
                        name = "play-circle-outline",
                        size = size,
                        contentDescription = "Play",
                    )
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
                    Text(text = releaseDate.year.toString())
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
            AverageAlbumRating(albumId = album.id)
        }
    }
}

@Composable
fun SmallAlbumPlaylistAlbumCell(album: AlbumViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
