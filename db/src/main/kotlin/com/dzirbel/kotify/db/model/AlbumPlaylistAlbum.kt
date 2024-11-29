package com.dzirbel.kotify.db.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

object AlbumPlaylistAlbumTable : IntIdTable() {
    val indexOnPlaylist: Column<Int> = integer("index_on_playlist")
    val albumPlaylist: Column<EntityID<String>> = reference("albumPlaylist", AlbumPlaylistTable)
    val album: Column<EntityID<String>?> = reference("album", AlbumTable).nullable()

    init {
        uniqueIndex(albumPlaylist, album)
    }
}

class AlbumPlaylistAlbum(id: EntityID<Int>) : IntEntity(id) {
    var albumPlaylistId: EntityID<String> by AlbumPlaylistAlbumTable.albumPlaylist
    var albumId: EntityID<String>? by AlbumPlaylistAlbumTable.album

    var indexOnPlaylist: Int by AlbumPlaylistAlbumTable.indexOnPlaylist

    var albumPlaylist: AlbumPlaylist by AlbumPlaylist referencedOn AlbumPlaylistAlbumTable.albumPlaylist
    var album: Album? by Album optionalReferencedOn AlbumPlaylistAlbumTable.album

    companion object : IntEntityClass<AlbumPlaylistAlbum>(AlbumPlaylistAlbumTable) {
        fun findOrCreateFromAlbum(albumId: String, albumPlaylistId: String): AlbumPlaylistAlbum {
            return find {
                (AlbumPlaylistAlbumTable.album eq albumId) and
                    (AlbumPlaylistAlbumTable.albumPlaylist eq albumPlaylistId)
            }
                .firstOrNull()
                ?: new {
                    this.albumId = EntityID(id = albumId, table = AlbumTable)
                    this.albumPlaylistId = EntityID(id = albumPlaylistId, table = AlbumPlaylistTable)
                }
        }

        /**
         * Returns the tracks of the playlist with the given [playlistId] in the order they appear on the playlist.
         */
        fun albumsInOrder(albumPlaylistId: String): List<AlbumPlaylistAlbum> {
            return find { AlbumPlaylistAlbumTable.albumPlaylist eq albumPlaylistId }
                .orderBy(AlbumPlaylistAlbumTable.indexOnPlaylist to SortOrder.ASC)
                .toList()
        }
    }
}
