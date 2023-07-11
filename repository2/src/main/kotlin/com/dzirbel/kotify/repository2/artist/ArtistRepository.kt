package com.dzirbel.kotify.repository2.artist

import com.dzirbel.kotify.db.model.Artist
import com.dzirbel.kotify.db.model.Genre
import com.dzirbel.kotify.db.model.Image
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.FullSpotifyArtist
import com.dzirbel.kotify.network.model.SpotifyArtist
import com.dzirbel.kotify.repository2.DatabaseEntityRepository
import com.dzirbel.kotify.util.flatMapParallel
import java.time.Instant

object ArtistRepository : DatabaseEntityRepository<Artist, SpotifyArtist>(Artist) {
    override suspend fun fetchFromRemote(id: String) = Spotify.Artists.getArtist(id = id)
    override suspend fun fetchFromRemote(ids: List<String>): List<SpotifyArtist?> {
        return ids.chunked(size = Spotify.MAX_LIMIT)
            .flatMapParallel { idsChunk -> Spotify.Artists.getArtists(ids = idsChunk) }
    }

    override fun convert(id: String, networkModel: SpotifyArtist): Artist {
        return Artist.updateOrInsert(id = id, networkModel = networkModel) {
            if (networkModel is FullSpotifyArtist) {
                fullUpdatedTime = Instant.now()

                popularity = networkModel.popularity
                followersTotal = networkModel.followers.total
                images.set(networkModel.images.map { Image.from(it) })
                genres.set(networkModel.genres.map { Genre.from(it) })
            }
        }
    }
}
