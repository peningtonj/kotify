package com.dzirbel.kotify.repository.artist

import com.dzirbel.kotify.db.model.Artist
import com.dzirbel.kotify.db.model.ArtistTable
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.SimilarSpotifyArtist
import com.dzirbel.kotify.repository.ConvertingRepository
import com.dzirbel.kotify.repository.DatabaseRepository
import com.dzirbel.kotify.repository.Repository
import com.dzirbel.kotify.repository.convertToDB
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.exposed.sql.batchInsert
import java.time.Instant

interface SimilarArtistsRepository :
    Repository<List<ArtistViewModel>>,
    ConvertingRepository<List<Artist>, List<SimilarSpotifyArtist>>

/**
 * A local-only repository mapping artist IDs to the set of track IDs for that artist.
 *
 * This is not a full [Repository] because it does not implement fetching from a remote source (since there is no
 * endpoint to get the set of tracks by an artist).
 *
 * TODO make it regular Repository to allow reuse of extensions
 */
class DatabaseSimilarArtistsRepository(
    scope: CoroutineScope,
    private val artistRepository: ArtistRepository
) :
    DatabaseRepository<List<ArtistViewModel>, List<Artist>, List<SimilarSpotifyArtist>>(
        entityName = "similar artists",
        scope = scope
    ),
    SimilarArtistsRepository {

    override fun fetchFromDatabase(id: String): Pair<List<Artist>, Instant> {
        return Artist.similarArtistsList(id) to Instant.now()
    }

    override suspend fun fetchFromRemote(id: String): List<SimilarSpotifyArtist> =
        Spotify.Artists.getArtistRelatedArtists(id = id)

    override fun convertToVM(databaseModel: List<Artist>, fetchTime: Instant) =
        databaseModel.map { ArtistViewModel(it) }

    override fun convertToDB(id: String, networkModel: List<SimilarSpotifyArtist>, fetchTime: Instant): List<Artist> {
        ArtistTable.SimilarArtistsTable.batchInsert(networkModel) { artist ->
            this[ArtistTable.SimilarArtistsTable.artist] = id
            this[ArtistTable.SimilarArtistsTable.similarArtist] = artist.id
        }
        return networkModel.mapNotNull {
            artistRepository.convertToDB(it, fetchTime)
        }
    }

}
