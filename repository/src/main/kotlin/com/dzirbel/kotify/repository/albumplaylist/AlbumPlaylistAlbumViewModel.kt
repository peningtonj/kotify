package com.dzirbel.kotify.repository.albumplaylist

import androidx.compose.runtime.Stable
import com.dzirbel.kotify.db.model.AlbumPlaylistAlbum
import com.dzirbel.kotify.repository.album.AlbumViewModel
import java.time.Instant

@Stable
data class AlbumPlaylistAlbumViewModel(
    val album: AlbumViewModel? = null,
    val addedAt: String? = null,
    val isLocal: Boolean = false,
    val indexOnPlaylist: Int,
    var draggableViewIndex: Int,
    ) {
        val addedAtInstant: Instant? by lazy {
            addedAt?.let { Instant.parse(it) }
        }

        constructor(
            albumPlaylistAlbum: AlbumPlaylistAlbum,
            album: AlbumViewModel? = albumPlaylistAlbum.album?.let(::AlbumViewModel),
        ) : this(
            album = album,
            indexOnPlaylist = albumPlaylistAlbum.indexOnPlaylist,
            draggableViewIndex = albumPlaylistAlbum.indexOnPlaylist,
        )

        override fun equals(other: Any?): Boolean {
            return other is AlbumPlaylistAlbumViewModel && album?.id == other.album?.id
        }

        override fun hashCode() = album?.id.hashCode()
    }