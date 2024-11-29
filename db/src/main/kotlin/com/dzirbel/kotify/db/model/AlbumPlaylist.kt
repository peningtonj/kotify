package com.dzirbel.kotify.db.model

import com.dzirbel.kotify.db.SavedEntityTable
import com.dzirbel.kotify.db.SpotifyEntity
import com.dzirbel.kotify.db.SpotifyEntityClass
import com.dzirbel.kotify.db.SpotifyEntityTable
import com.dzirbel.kotify.db.util.single
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object AlbumPlaylistTable : SpotifyEntityTable(entityName = "albumPlaylist") {
    private const val SNAPSHOT_ID_LENGTH = 128

    val collaborative: Column<Boolean> = bool("collaborative")
    val description: Column<String?> = text("description").nullable()
    val owner: Column<EntityID<String>> = reference("owner", UserTable)
    val public: Column<Boolean?> = bool("public").nullable()
    val snapshotId: Column<String> = varchar("snapshotId", length = SNAPSHOT_ID_LENGTH)
    val followersTotal: Column<Int?> = integer("followers_total").nullable()
    val totalTracks: Column<Int?> = integer("total_tracks").nullable()
    val albumsFetched: Column<Instant?> = timestamp("album_fetched_time").nullable()
    val libraryOrder: Column<Int?> = integer("library_order").nullable()
    val nextAlbumTrackId: Column<EntityID<String>?> = reference("track", TrackTable).nullable()

    fun albumsFetchTime(playlistId: String): Instant? {
        // TODO also compare totalTracks to number of tracks in DB?
        return single(albumsFetched) { id eq playlistId }
    }

//    object PlaylistImageTable : Table() {
//        val playlist = reference("playlist", PlaylistTable)
//        val image = reference("image", ImageTable)
//        override val primaryKey = PrimaryKey(playlist, image)
//    }

    object SavedAlbumPlaylistsTable : SavedEntityTable(name = "saved_album_playlists")
}

class AlbumPlaylist(id: EntityID<String>) : SpotifyEntity(id = id, table = AlbumPlaylistTable) {
    var collaborative: Boolean by AlbumPlaylistTable.collaborative
    var description: String? by AlbumPlaylistTable.description
    var ownerId: EntityID<String> by AlbumPlaylistTable.owner
    var public: Boolean? by AlbumPlaylistTable.public
    var snapshotId: String by AlbumPlaylistTable.snapshotId
    var followersTotal: Int? by AlbumPlaylistTable.followersTotal
    var totalTracks: Int? by AlbumPlaylistTable.totalTracks
    var albumsFetched: Instant? by AlbumPlaylistTable.albumsFetched
    var libraryOrder: Int? by AlbumPlaylistTable.libraryOrder
    var nextAlbumTrackId: EntityID<String>? by AlbumPlaylistTable.nextAlbumTrackId

    var owner: User by User referencedOn AlbumPlaylistTable.owner

//    var images: SizedIterable<Image> by Image via AlbumPlaylistTable.PlaylistImageTable

    companion object : SpotifyEntityClass<AlbumPlaylist>(AlbumPlaylistTable)
}
