package com.dzirbel.kotify.db.model

import com.dzirbel.kotify.db.ReadWriteCachedProperty
import com.dzirbel.kotify.db.SavedEntityTable
import com.dzirbel.kotify.db.SpotifyEntity
import com.dzirbel.kotify.db.SpotifyEntityClass
import com.dzirbel.kotify.db.SpotifyEntityTable
import com.dzirbel.kotify.db.cached
import com.dzirbel.kotify.db.cachedAsList
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

object TrackTable : SpotifyEntityTable(name = "tracks") {
    val discNumber: Column<Int> = integer("disc_number")
    val durationMs: Column<Long> = long("duration_ms")
    val explicit: Column<Boolean> = bool("explicit")
    val local: Column<Boolean> = bool("local")
    val playable: Column<Boolean?> = bool("playable").nullable()
    val trackNumber: Column<Int> = integer("track_number")
    val popularity: Column<Int?> = integer("popularity").nullable()

    val album: Column<EntityID<String>?> = reference("album", AlbumTable).nullable()

    object TrackArtistTable : Table() {
        val track = reference("track", TrackTable)
        val artist = reference("artist", ArtistTable)
        override val primaryKey = PrimaryKey(track, artist)

        fun artistIdsForTrack(trackId: String): Set<String> {
            return slice(artist)
                .select { track eq trackId }
                .mapTo(mutableSetOf()) { it[artist].value }
        }

        fun trackIdsForArtist(artistId: String): Set<String> {
            return slice(track)
                .select { artist eq artistId }
                .mapTo(mutableSetOf()) { it[track].value }
        }

        fun setTrackArtists(trackId: String, artistIds: Iterable<String>) {
            val currentArtistIds = artistIdsForTrack(trackId)
            val newArtistIdsSet = artistIds.toSet()

            val artistsToRemove = currentArtistIds.minus(newArtistIdsSet)
            if (artistsToRemove.isNotEmpty()) {
                deleteWhere { (track eq trackId) and (artist inList artistsToRemove) }
            }

            val artistsToAdd = newArtistIdsSet.minus(currentArtistIds)
            if (artistsToAdd.isNotEmpty()) {
                batchInsert(artistsToAdd, shouldReturnGeneratedValues = false) { artistId ->
                    this[track] = trackId
                    this[artist] = artistId
                }
            }
        }
    }

    object SavedTracksTable : SavedEntityTable(name = "saved_tracks")
}

class Track(id: EntityID<String>) : SpotifyEntity(id = id, table = TrackTable) {
    var discNumber: Int by TrackTable.discNumber
    var durationMs: Long by TrackTable.durationMs
    var explicit: Boolean by TrackTable.explicit
    var local: Boolean by TrackTable.local
    var playable: Boolean? by TrackTable.playable
    var trackNumber: Int by TrackTable.trackNumber
    var popularity: Int? by TrackTable.popularity
    var albumId: EntityID<String>? by TrackTable.album

    val album: ReadWriteCachedProperty<Album?> by (Album optionalReferencedOn TrackTable.album).cached()

    val artists: ReadWriteCachedProperty<List<Artist>> by (Artist via TrackTable.TrackArtistTable).cachedAsList()

    companion object : SpotifyEntityClass<Track>(TrackTable)
}
