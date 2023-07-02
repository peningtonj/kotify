package com.dzirbel.kotify.repository.playlist

import com.dzirbel.kotify.db.model.Playlist
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.SpotifyPlaylist
import com.dzirbel.kotify.repository.DatabaseRepository

object PlaylistRepository : DatabaseRepository<Playlist, SpotifyPlaylist>(Playlist) {
    override suspend fun fetch(id: String) = Spotify.Playlists.getPlaylist(playlistId = id)
}