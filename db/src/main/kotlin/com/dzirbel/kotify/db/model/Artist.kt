package com.dzirbel.kotify.db.model

import com.dzirbel.kotify.db.SavedEntityTable
import com.dzirbel.kotify.db.SpotifyEntity
import com.dzirbel.kotify.db.SpotifyEntityClass
import com.dzirbel.kotify.db.SpotifyEntityTable
import com.dzirbel.kotify.db.util.sized
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object ArtistTable : SpotifyEntityTable(entityName = "artist") {
    val popularity: Column<Int?> = integer("popularity").nullable()
    val followersTotal: Column<Int?> = integer("followers_total").nullable()
    val albumsFetched: Column<Instant?> = timestamp("albums_fetched_time").nullable()

    object ArtistImageTable : Table() {
        val artist = reference("artist", ArtistTable)
        val image = reference("image", ImageTable)
        override val primaryKey = PrimaryKey(artist, image)
    }

    object ArtistGenreTable : Table() {
        val artist = reference("artist", ArtistTable)
        val genre = reference("genre", GenreTable)
        override val primaryKey = PrimaryKey(artist, genre)
    }

    object SimilarArtistsTable : Table() {
        val artist = reference("artist", ArtistTable)
        val similarArtist = reference("similar_artist", ArtistTable, onDelete = ReferenceOption.CASCADE)
        override val primaryKey = PrimaryKey(artist, similarArtist)
    }

    object SavedArtistsTable : SavedEntityTable(name = "saved_artists")
}

class Artist(id: EntityID<String>) : SpotifyEntity(id = id, table = ArtistTable) {
    var popularity: Int? by ArtistTable.popularity
    var followersTotal: Int? by ArtistTable.followersTotal

    var images: SizedIterable<Image> by Image via ArtistTable.ArtistImageTable
    var genres: SizedIterable<Genre> by Genre via ArtistTable.ArtistGenreTable

    val similarArtists: SizedIterable<Artist>
        get() = ArtistTable.SimilarArtistsTable
            .selectAll()
            .where { ArtistTable.SimilarArtistsTable.artist eq id }
            .mapNotNull {
                Artist.findById(it[ArtistTable.SimilarArtistsTable.similarArtist])
            }
            .sized()

    companion object : SpotifyEntityClass<Artist>(ArtistTable) {
        fun similarArtistsList(artistId: String): List<Artist> {
            return Artist.find { ArtistTable.id eq artistId }
                .map { it.similarArtists }
                .flatten()
        }
    }
}
