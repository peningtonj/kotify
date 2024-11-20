package com.dzirbel.kotify.repository.albumplaylist

import com.dzirbel.kotify.db.model.*
import com.dzirbel.kotify.db.util.sized
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.*
import com.dzirbel.kotify.repository.ConvertingRepository
import com.dzirbel.kotify.repository.DatabaseRepository
import com.dzirbel.kotify.repository.Repository
import com.dzirbel.kotify.repository.album.AlbumRepository
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.album.toAlbumType
import com.dzirbel.kotify.repository.convertToDB
import com.dzirbel.kotify.repository.episode.EpisodeRepository
import com.dzirbel.kotify.repository.episode.convertToDB
import com.dzirbel.kotify.repository.playlist.PlaylistRepository
import com.dzirbel.kotify.repository.playlist.PlaylistTrackViewModel
import com.dzirbel.kotify.repository.playlist.PlaylistTracksRepository
import com.dzirbel.kotify.repository.playlist.PlaylistTracksRepository.PlaylistReorderState
import com.dzirbel.kotify.repository.track.TrackRepository
import com.dzirbel.kotify.repository.user.UserRepository
import com.dzirbel.kotify.repository.user.convertToDB
import com.dzirbel.kotify.repository.util.ReorderCalculator
import com.dzirbel.kotify.repository.util.updateOrInsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.sql.update
import java.time.Instant

interface AlbumPlaylistAlbumsRepository :
    Repository<List<AlbumPlaylistAlbumViewModel>>,
    ConvertingRepository<List<AlbumPlaylistAlbum>, List<SpotifyAlbum>> {
    /**
     * Converts the given network model [spotifyAlbum] to a database model [AlbumPlaylistAlbum] for the playlist with
     * the given [albumPlaylistId] and at the given [index] on the playlist.
     */
    fun convertAlbum(
        spotifyAlbum: SpotifyAlbum,
//        sampleTrack: SpotifyPlaylistTrack,
        albumPlaylistId: String,
        index: Int,
        fetchTime: Instant,
    ): AlbumPlaylistAlbum?

    sealed interface AlbumPlaylistReorderState {
        /**
         * The first phase in which the set of operations to apply is being calculated.
         */
        data object Calculating : AlbumPlaylistReorderState

        /**
         * The second phase in which the reorder operations are being applied to the remote source.
         */
        data class Reordering(val completedOps: Int, val totalOps: Int) : AlbumPlaylistReorderState

        /**
         * The third phase in which the track list is being re-fetched to verify the order is correct.
         */
        data object Verifying : AlbumPlaylistReorderState
    }

}


    // TODO add CacheStrategy
class DatabaseAlbumPlaylistAlbumsRepository(
    scope: CoroutineScope,
    private val albumRepository: AlbumRepository,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val userRepository: UserRepository,
) :
        DatabaseRepository<List<AlbumPlaylistAlbumViewModel>, List<AlbumPlaylistAlbum>, List<SpotifyAlbum>>(
            entityName = "album playlist albums",
            entityNamePlural = "album playlists albums",
            scope = scope,
        ),
        AlbumPlaylistAlbumsRepository {

        fun getAlbumFromPlaylistTrack(track: SpotifyPlaylistTrack): SpotifyAlbum? {
            if (track.track is SimplifiedSpotifyTrack) {
                return (track.track as SimplifiedSpotifyTrack).album
            }
            return null
        }


        override suspend fun fetchFromRemote(id: String): List<SpotifyAlbum> {
            trackRepository.refreshFromRemote("6xXAl2w0mqyxsRB8ak2S7N").join()
            albumRepository.refreshFromRemote("5zFOVxtYkKdqwYdG9bASRR").join()
            playlistRepository.refreshFromRemote(id)

            val tracks = Spotify.Playlists.getPlaylistTracks(playlistId = id).asFlow().toList()
            val albums: List<SpotifyAlbum> =
                tracks.map { track: SpotifyPlaylistTrack -> getAlbumFromPlaylistTrack(track)!! }
            return albums.distinct().filter { album -> album.name != "Turntables & Phonographs Sound Effects" }
        }

        override fun convertToVM(
            databaseModel: List<AlbumPlaylistAlbum>,
            fetchTime: Instant
        ): List<AlbumPlaylistAlbumViewModel> {
            return databaseModel.map(::AlbumPlaylistAlbumViewModel)
        }

        override fun fetchFromDatabase(id: String): Pair<List<AlbumPlaylistAlbum>, Instant>? {
            return AlbumPlaylistTable.albumsFetchTime(playlistId = id)?.let { fetchTime ->
                val albums = AlbumPlaylistAlbum.albumsInOrder(albumPlaylistId = id)
                albums to fetchTime
            }
        }

        override fun convertToDB(
            id: String,
            networkModel: List<SpotifyAlbum>,
//            networkModel: List<Pair<SpotifyAlbum, SpotifyPlaylistTrack>>,
            fetchTime: Instant,
        ): List<AlbumPlaylistAlbum> {
            AlbumPlaylistTable.update(where = { AlbumPlaylistTable.id eq id }) {
                it[albumsFetched] = fetchTime
            }

            return networkModel.mapIndexedNotNull { index, spotifyAlbum ->
                convertAlbum(
//                    spotifyAlbum = spotifyAlbum.first,
//                    sampleTrack = spotifyAlbum.second,
                    spotifyAlbum = spotifyAlbum,
                    albumPlaylistId = id,
                    index = index,
                    fetchTime = fetchTime,
                )

            }

        }

        override fun convertAlbum(
            spotifyAlbum: SpotifyAlbum,
//            sampleTrack: SpotifyPlaylistTrack,
            albumPlaylistId: String,
            index: Int,
            fetchTime: Instant,
        ): AlbumPlaylistAlbum? {
            val albumPlaylistAlbum = albumRepository.convertToDB(spotifyAlbum, fetchTime)?.id?.value?.let { albumId ->
                AlbumPlaylistAlbum.findOrCreateFromAlbum(albumId = albumId, albumPlaylistId = albumPlaylistId)
            }

            return albumPlaylistAlbum?.apply {
//                sampleTrack.addedBy?.let { addedBy = userRepository.convertToDB(it, fetchTime) }
                indexOnPlaylist = index
//                sampleTrack.addedAt?.let { addedAt = it }
            }
        }

    }
