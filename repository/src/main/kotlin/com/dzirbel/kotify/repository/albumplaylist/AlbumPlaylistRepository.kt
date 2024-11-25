package com.dzirbel.kotify.repository.albumplaylist

import com.dzirbel.kotify.db.model.AlbumPlaylist
import com.dzirbel.kotify.db.model.PlaylistTrack
import com.dzirbel.kotify.db.model.TrackTable
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.Spotify.Playlists.getPlaylist
import com.dzirbel.kotify.network.model.*
import com.dzirbel.kotify.repository.ConvertingRepository
import com.dzirbel.kotify.repository.DatabaseEntityRepository
import com.dzirbel.kotify.repository.Repository
import com.dzirbel.kotify.repository.playlist.AlbumPlaylistViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistTracksRepository
import com.dzirbel.kotify.repository.track.TrackRepository
import com.dzirbel.kotify.repository.user.UserRepository
import com.dzirbel.kotify.repository.user.convertToDB
import com.dzirbel.kotify.repository.util.updateOrInsert
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.exposed.dao.id.EntityID
import java.time.Instant

interface AlbumPlaylistRepository : Repository<AlbumPlaylistViewModel>, ConvertingRepository<AlbumPlaylist, SpotifyPlaylist>

class DatabaseAlbumPlaylistRepository(
    scope: CoroutineScope,
    private val albumPlaylistAlbumsRepository: AlbumPlaylistAlbumsRepository,
    private val userRepository: UserRepository,
) : DatabaseEntityRepository<AlbumPlaylistViewModel, AlbumPlaylist, SpotifyPlaylist>(entityClass = AlbumPlaylist, scope = scope),
    AlbumPlaylistRepository {

    override suspend fun fetchFromRemote(id: String) = getPlaylist(playlistId = id)

    fun getAlbum(track: SpotifyPlaylistTrack): SimplifiedSpotifyAlbum? {
        val spotifyTrack = track.track as SimplifiedSpotifyTrack
        return spotifyTrack.album
    }

    override fun convertToDB(id: String, networkModel: SpotifyPlaylist, fetchTime: Instant): AlbumPlaylist {
        return AlbumPlaylist.updateOrInsert(id = id, networkModel = networkModel, fetchTime = fetchTime) {
            collaborative = networkModel.collaborative
            networkModel.description?.let { description = it }
            networkModel.public?.let { public = it }
            snapshotId = networkModel.snapshotId
            albumsFetched = fetchTime

            nextAlbumTrackId = EntityID("6xXAl2w0mqyxsRB8ak2S7N", TrackTable)
            owner = userRepository.convertToDB(networkModel.owner, fetchTime)

            if (networkModel is FullSpotifyPlaylist) {
                networkModel.tracks.items.mapNotNull { track ->
                    getAlbum(track)
                }.distinct().mapIndexedNotNull {index, album ->
                            albumPlaylistAlbumsRepository.convertAlbum(
                                spotifyAlbum = album,
                                albumPlaylistId = if(album.name.contains("Sound Effects")) "" else networkModel.id,
                                index = index,
                                fetchTime = fetchTime,
                            )
                    }
                }

            }
        }

    override fun convertToVM(databaseModel: AlbumPlaylist, fetchTime: Instant) = AlbumPlaylistViewModel(databaseModel)
}