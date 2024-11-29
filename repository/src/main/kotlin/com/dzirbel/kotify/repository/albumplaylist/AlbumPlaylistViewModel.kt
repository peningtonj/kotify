package com.dzirbel.kotify.repository.playlist

import androidx.compose.runtime.Stable
import com.dzirbel.kotify.db.model.AlbumPlaylist
import com.dzirbel.kotify.db.model.PlaylistTable
import com.dzirbel.kotify.db.model.Track
import com.dzirbel.kotify.repository.EntityImageViewModel
import com.dzirbel.kotify.repository.EntityViewModel
import com.dzirbel.kotify.repository.ImageViewModel
import com.dzirbel.kotify.repository.track.TrackViewModel
import com.dzirbel.kotify.util.CurrentTime
import java.time.Instant

@Stable
data class AlbumPlaylistViewModel(
    override val id: String,
    override val name: String,
    override val uri: String? = null,
    override val updatedTime: Instant = CurrentTime.instant,
    override val fullUpdatedTime: Instant? = null,
    val description: String? = null,
    val followersTotal: Int? = null,
    val totalTracks: Int? = null,
    val ownerId: String,
    val libraryOrder: Int? = null,
    val nextAlbumTrackId: String? = null,
    val nextAlbumTrack: TrackViewModel? = nextAlbumTrackId?.let { Track.findById(it) }?.let(::TrackViewModel),
    val images: ImageViewModel = EntityImageViewModel(id, PlaylistTable.PlaylistImageTable.playlist),
) : EntityViewModel, ImageViewModel by images {

    constructor(playlist: AlbumPlaylist) : this(
        id = playlist.id.value,
        uri = playlist.uri,
        name = playlist.name,
        updatedTime = playlist.updatedTime,
        fullUpdatedTime = playlist.fullUpdatedTime,
        description = playlist.description,
        followersTotal = playlist.followersTotal,
        totalTracks = playlist.totalTracks,
        ownerId = playlist.ownerId.value,
        libraryOrder = playlist.libraryOrder,
        nextAlbumTrackId = playlist.nextAlbumTrackId?.value,
    )

    override fun equals(other: Any?) = other is PlaylistViewModel && id == other.id
    override fun hashCode() = id.hashCode()
}
