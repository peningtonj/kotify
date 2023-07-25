package com.dzirbel.kotify.repository.track

import com.dzirbel.kotify.db.model.Track
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.FullSpotifyTrack
import com.dzirbel.kotify.network.model.SimplifiedSpotifyTrack
import com.dzirbel.kotify.network.model.SpotifyTrack
import com.dzirbel.kotify.repository.DatabaseEntityRepository
import com.dzirbel.kotify.repository.Repository
import com.dzirbel.kotify.repository.album.AlbumRepository
import com.dzirbel.kotify.repository.artist.ArtistRepository
import com.dzirbel.kotify.util.flatMapParallel
import kotlinx.coroutines.CoroutineScope
import java.time.Instant

open class TrackRepository internal constructor(scope: CoroutineScope) :
    DatabaseEntityRepository<Track, SpotifyTrack>(entityClass = Track, scope = scope) {

    override suspend fun fetchFromRemote(id: String) = Spotify.Tracks.getTrack(id = id)
    override suspend fun fetchFromRemote(ids: List<String>): List<FullSpotifyTrack> {
        return ids.chunked(size = Spotify.MAX_LIMIT)
            .flatMapParallel { idsChunk -> Spotify.Tracks.getTracks(ids = idsChunk) }
    }

    override fun convert(id: String, networkModel: SpotifyTrack): Track {
        return Track.updateOrInsert(id = id, networkModel = networkModel) {
            discNumber = networkModel.discNumber
            durationMs = networkModel.durationMs
            explicit = networkModel.explicit
            local = networkModel.isLocal
            playable = networkModel.isPlayable
            trackNumber = networkModel.trackNumber
            networkModel.album
                ?.let { AlbumRepository.convert(it) }
                ?.let { album.set(it) }

            artists.set(networkModel.artists.mapNotNull { ArtistRepository.convert(it) })

            if (networkModel is SimplifiedSpotifyTrack) {
                networkModel.popularity?.let {
                    popularity = it
                }
            }

            if (networkModel is FullSpotifyTrack) {
                fullUpdatedTime = Instant.now()
                popularity = networkModel.popularity
            }
        }
    }

    companion object : TrackRepository(scope = Repository.applicationScope)
}
