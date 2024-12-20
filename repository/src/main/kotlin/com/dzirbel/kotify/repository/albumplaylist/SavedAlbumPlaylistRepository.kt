package com.dzirbel.kotify.repository.albumplaylist

import com.dzirbel.kotify.db.model.AlbumPlaylistTable
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.SpotifyPlaylist
import com.dzirbel.kotify.repository.DatabaseSavedRepository
import com.dzirbel.kotify.repository.SavedRepository
import com.dzirbel.kotify.repository.convertToDB
import com.dzirbel.kotify.repository.user.UserRepository
import com.dzirbel.kotify.util.coroutines.mapParallel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import java.time.Instant

interface SavedAlbumPlaylistRepository : SavedRepository

class DatabaseSavedAlbumPlaylistRepository(
    scope: CoroutineScope,
    userRepository: UserRepository,
    private val albumPlaylistRepository: AlbumPlaylistRepository,
) :
    DatabaseSavedRepository<IndexedValue<SpotifyPlaylist>>(
        savedEntityTable = AlbumPlaylistTable.SavedAlbumPlaylistsTable,
        scope = scope,
        userRepository = userRepository,
    ),
    SavedAlbumPlaylistRepository {

    override suspend fun fetchIsSaved(ids: List<String>): List<Boolean> {
        val userId = userRepository.requireCurrentUserId

        return ids.mapParallel { id ->
            Spotify.Follow.isFollowingPlaylist(playlistId = id, userIds = listOf(userId))
                .first()
        }
    }

    override suspend fun pushSaved(ids: List<String>, saved: Boolean) {
        if (saved) {
            ids.mapParallel { id -> Spotify.Follow.followPlaylist(playlistId = id) }
        } else {
            ids.mapParallel { id -> Spotify.Follow.unfollowPlaylist(playlistId = id) }
        }
    }

    override suspend fun fetchLibrary(): Iterable<IndexedValue<SpotifyPlaylist>> {
        return Spotify.Playlists.getAlbumPlaylists(limit = Spotify.MAX_LIMIT)
            .asFlow()
            .withIndex()
            .toList()
            .filter { playlist -> playlist.value.description == "albums" }
    }

    override fun convertToDB(
        savedNetworkType: IndexedValue<SpotifyPlaylist>,
        fetchTime: Instant,
    ): Pair<String, Instant?> {
        val albumPlaylistId = savedNetworkType.value.id
        albumPlaylistRepository.convertToDB(
            networkModel = savedNetworkType.value,
            fetchTime = fetchTime
        )?.let { albumPlaylist ->
            albumPlaylist.libraryOrder = savedNetworkType.index
            albumPlaylistRepository.update(id = albumPlaylistId, model = albumPlaylist, fetchTime = fetchTime)
        }

        return savedNetworkType.value.id to null
    }
}
