package com.dzirbel.kotify.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.dzirbel.kotify.cache.SpotifyCache
import com.dzirbel.kotify.network.model.Playlist
import com.dzirbel.kotify.ui.common.PageStack
import com.dzirbel.kotify.ui.common.SimpleTextButton
import com.dzirbel.kotify.ui.common.VerticalScroll
import com.dzirbel.kotify.ui.theme.Colors
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.util.HandleState
import com.dzirbel.kotify.ui.util.mutate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private class LibraryPresenter(scope: CoroutineScope) :
    Presenter<LibraryPresenter.State?, LibraryPresenter.Event>(
        scope = scope,
        eventMergeStrategy = EventMergeStrategy.LATEST,
        startingEvents = listOf(Event.LoadPlaylists()),
        initialState = null
    ) {

    data class State(val refreshing: Boolean, val playlists: List<Playlist>)

    sealed class Event {
        class LoadPlaylists(val invalidate: Boolean = false) : Event()
    }

    override suspend fun reactTo(event: Event) {
        when (event) {
            is Event.LoadPlaylists -> {
                mutateState { it?.copy(refreshing = true) }

                if (event.invalidate) {
                    SpotifyCache.invalidate(SpotifyCache.GlobalObjects.SavedPlaylists.ID)
                }

                val playlists = SpotifyCache.Playlists.getSavedPlaylists()
                    .map { SpotifyCache.Playlists.getPlaylist(it) }

                mutateState {
                    State(
                        refreshing = false,
                        playlists = playlists
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryPanel(pageStack: MutableState<PageStack>) {
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val presenter = remember { LibraryPresenter(scope = scope) }

    VerticalScroll {
        Text(
            text = "Library",
            fontSize = Dimens.fontTitle,
            modifier = Modifier.padding(Dimens.space3)
        )

        Spacer(Modifier.height(Dimens.space3))

        MaxWidthButton(
            text = "Artists",
            selected = pageStack.value.current == ArtistsPage,
            onClick = { pageStack.mutate { to(ArtistsPage) } }
        )

        MaxWidthButton(
            text = "Albums",
            selected = pageStack.value.current == AlbumsPage,
            onClick = { pageStack.mutate { to(AlbumsPage) } }
        )

        MaxWidthButton(
            text = "Songs",
            selected = pageStack.value.current == TracksPage,
            onClick = { pageStack.mutate { to(TracksPage) } }
        )

        Spacer(Modifier.height(Dimens.space3))

        val stateOrError = presenter.state()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f).padding(Dimens.space3),
                fontSize = Dimens.fontTitle,
                text = "Playlists"
            )

            val refreshing = stateOrError.safeState?.refreshing == true
            IconButton(
                enabled = !refreshing,
                onClick = { presenter.emitAsync(LibraryPresenter.Event.LoadPlaylists(invalidate = true)) }
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(Dimens.iconMedium))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(Dimens.iconMedium)
                    )
                }
            }
        }

        HandleState(
            state = { stateOrError },
            onError = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconMedium).align(Alignment.CenterHorizontally),
                    tint = Colors.current.error
                )
            },
            onLoading = {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconMedium).align(Alignment.CenterHorizontally)
                )
            },
            onSuccess = { state ->
                state.playlists.forEach { playlist -> PlaylistItem(playlist, pageStack) }
            }
        )
    }
}

@Composable
private fun PlaylistItem(playlist: Playlist, pageStack: MutableState<PageStack>) {
    val selected = pageStack.value.current == PlaylistPage(playlistId = playlist.id)

    SimpleTextButton(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.space3, vertical = Dimens.space2),
        onClick = { pageStack.mutate { to(PlaylistPage(playlistId = playlist.id)) } }
    ) {
        Text(
            text = playlist.name,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )

        if (Player.playbackContext.value?.uri == playlist.uri) {
            val sizeDp = with(LocalDensity.current) { Dimens.fontBody.toDp() }
            CachedIcon(name = "volume-up", size = sizeDp, contentDescription = "Volume", tint = Colors.current.primary)
        }
    }
}

@Composable
private fun MaxWidthButton(
    text: String,
    selected: Boolean,
    contentPadding: PaddingValues = PaddingValues(all = Dimens.space3),
    onClick: () -> Unit
) {
    SimpleTextButton(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        onClick = onClick
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}