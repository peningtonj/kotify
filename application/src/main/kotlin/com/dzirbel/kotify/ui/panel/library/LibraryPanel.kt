package com.dzirbel.kotify.ui.panel.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dzirbel.kotify.repository.CacheStrategy
import com.dzirbel.kotify.repository.playlist.PlaylistViewModel
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.LocalPlayer
import com.dzirbel.kotify.ui.LocalPlaylistRepository
import com.dzirbel.kotify.ui.LocalSavedPlaylistRepository
import com.dzirbel.kotify.ui.components.HorizontalDivider
import com.dzirbel.kotify.ui.components.LibraryInvalidateButton
import com.dzirbel.kotify.ui.components.SimpleTextButton
import com.dzirbel.kotify.ui.components.VerticalScroll
import com.dzirbel.kotify.ui.components.VerticalSpacer
import com.dzirbel.kotify.ui.page.albums.AlbumsPage
import com.dzirbel.kotify.ui.page.artists.ArtistsPage
import com.dzirbel.kotify.ui.page.playlist.PlaylistPage
import com.dzirbel.kotify.ui.pageStack
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.mutate
import com.dzirbel.kotify.util.collections.zipEach

@Composable
fun LibraryPanel() {
    Surface(elevation = Dimens.panelElevationSmall) {
        VerticalScroll(Modifier.fillMaxHeight()) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(start = Dimens.space3, end = Dimens.space3, top = Dimens.space3),
            )

            HorizontalDivider(Modifier.padding(bottom = Dimens.space3))

            MaxWidthButton(
                text = "Artists",
                selected = pageStack.value.current == ArtistsPage,
                onClick = { pageStack.mutate { to(ArtistsPage) } },
            )

            MaxWidthButton(
                text = "Albums",
                selected = pageStack.value.current == AlbumsPage,
                onClick = { pageStack.mutate { to(AlbumsPage) } },
            )

            VerticalSpacer(Dimens.space3)

            Text(
                modifier = Modifier.padding(start = Dimens.space3, end = Dimens.space3, top = Dimens.space3),
                style = MaterialTheme.typography.h5,
                text = "Playlists",
            )

            LibraryInvalidateButton(LocalSavedPlaylistRepository.current)

            HorizontalDivider(Modifier.padding(bottom = Dimens.space3))

            val savedPlaylistIds = LocalSavedPlaylistRepository.current.library.collectAsState().value?.ids
            if (savedPlaylistIds != null) {
                val playlistRepository = LocalPlaylistRepository.current
                val playlistStates = remember(savedPlaylistIds) {
                    // do not require a full playlist model
                    playlistRepository.statesOf(ids = savedPlaylistIds, cacheStrategy = CacheStrategy.EntityTTL())
                }

                savedPlaylistIds.zipEach(playlistStates) { playlistId, playlistState ->
                    key(playlistId) {
                        val playlist = playlistState.collectAsState().value?.cachedValue

                        // TODO handle other cache states: shimmer when loading, show errors, etc
                        if (playlist != null) {
                            PlaylistItem(playlist = playlist)
                        }
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconMedium).align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun PlaylistItem(playlist: PlaylistViewModel) {
    val selected = pageStack.value.current == PlaylistPage(playlistId = playlist.id)

    SimpleTextButton(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        contentPadding = PaddingValues(horizontal = Dimens.space3, vertical = Dimens.space2),
        onClick = { pageStack.mutate { to(PlaylistPage(playlistId = playlist.id)) } },
    ) {
        Text(
            text = playlist.name,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )

        val playbackUriState = LocalPlayer.current.playbackContextUri.collectAsState()
        val playingPlaylist = remember(playlist.uri) {
            derivedStateOf { playlist.uri != null && playbackUriState.value == playlist.uri }
        }

        if (playingPlaylist.value) {
            CachedIcon(
                name = "volume-up",
                size = Dimens.fontBodyDp,
                tint = MaterialTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun MaxWidthButton(text: String, selected: Boolean, onClick: () -> Unit) {
    SimpleTextButton(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        contentPadding = PaddingValues(horizontal = Dimens.space3, vertical = Dimens.space2),
        onClick = onClick,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
