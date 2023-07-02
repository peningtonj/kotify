package com.dzirbel.kotify.db.model

import com.dzirbel.kotify.db.KotifyDatabase
import com.dzirbel.kotify.db.ReadOnlyCachedProperty
import com.dzirbel.kotify.db.ReadWriteCachedProperty
import com.dzirbel.kotify.db.SavedEntityTable
import com.dzirbel.kotify.db.SpotifyEntity
import com.dzirbel.kotify.db.SpotifyEntityClass
import com.dzirbel.kotify.db.SpotifyEntityTable
import com.dzirbel.kotify.db.cached
import com.dzirbel.kotify.db.cachedAsList
import com.dzirbel.kotify.db.cachedReadOnly
import com.dzirbel.kotify.db.util.largest
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.FullSpotifyPlaylist
import com.dzirbel.kotify.network.model.SimplifiedSpotifyPlaylist
import com.dzirbel.kotify.network.model.SpotifyPlaylist
import com.dzirbel.kotify.network.model.asFlow
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object PlaylistTable : SpotifyEntityTable(name = "playlists") {
    private const val SNAPSHOT_ID_LENGTH = 128

    val collaborative: Column<Boolean> = bool("collaborative")
    val description: Column<String?> = text("description").nullable()
    val owner: Column<EntityID<String>> = reference("owner", UserTable)
    val public: Column<Boolean?> = bool("public").nullable()
    val snapshotId: Column<String> = varchar("snapshotId", length = SNAPSHOT_ID_LENGTH)
    val followersTotal: Column<Int?> = integer("followers_total").nullable()
    val totalTracks: Column<Int?> = integer("total_tracks").nullable()
    val tracksFetched: Column<Instant?> = timestamp("tracks_fetched_time").nullable()

    object PlaylistImageTable : Table() {
        val playlist = reference("playlist", PlaylistTable)
        val image = reference("image", ImageTable)
        override val primaryKey = PrimaryKey(playlist, image)
    }

    object SavedPlaylistsTable : SavedEntityTable(name = "saved_playlists")
}

class Playlist(id: EntityID<String>) : SpotifyEntity(id = id, table = PlaylistTable) {
    var collaborative: Boolean by PlaylistTable.collaborative
    var description: String? by PlaylistTable.description
    var public: Boolean? by PlaylistTable.public
    var snapshotId: String by PlaylistTable.snapshotId
    var followersTotal: Int? by PlaylistTable.followersTotal
    var totalTracks: Int? by PlaylistTable.totalTracks
    var tracksFetched: Instant? by PlaylistTable.tracksFetched

    val owner: ReadWriteCachedProperty<User> by (User referencedOn PlaylistTable.owner).cached()

    val images: ReadWriteCachedProperty<List<Image>> by (Image via PlaylistTable.PlaylistImageTable).cachedAsList()
    val largestImage: ReadOnlyCachedProperty<Image?> by (Image via PlaylistTable.PlaylistImageTable)
        .cachedReadOnly { it.largest() }

    val playlistTracksInOrder: ReadOnlyCachedProperty<List<PlaylistTrack>> = ReadOnlyCachedProperty {
        PlaylistTrack.find { PlaylistTrackTable.playlist eq this@Playlist.id }
            .orderBy(PlaylistTrackTable.indexOnPlaylist to SortOrder.ASC)
            .toList()
    }

    val tracks: ReadOnlyCachedProperty<List<Track>> by (PlaylistTrack referrersOn PlaylistTrackTable.playlist)
        .cachedReadOnly(baseToDerived = { playlistTracks -> playlistTracks.map { it.track.live } })

    val hasAllTracks: Boolean
        get() = tracksFetched != null

    companion object : SpotifyEntityClass<Playlist, SpotifyPlaylist>(PlaylistTable) {
        override fun Playlist.update(networkModel: SpotifyPlaylist) {
            collaborative = networkModel.collaborative
            networkModel.description?.let { description = it }
            networkModel.public?.let { public = it }
            snapshotId = networkModel.snapshotId

            User.from(networkModel.owner)?.let { owner.set(it) }

            images.set(networkModel.images.map { Image.from(it) })

            if (networkModel is SimplifiedSpotifyPlaylist) {
                networkModel.tracks?.let {
                    totalTracks = it.total
                }
            }

            if (networkModel is FullSpotifyPlaylist) {
                fullUpdatedTime = Instant.now()
                followersTotal = networkModel.followers.total

                totalTracks = networkModel.tracks.total
                networkModel.tracks.items.mapIndexedNotNull { index, track ->
                    PlaylistTrack.from(spotifyPlaylistTrack = track, playlistId = id.value, index = index)
                }
            }
        }

        // TODO move to repository?
        suspend fun getAllTracks(playlistId: String, allowCache: Boolean = true): Pair<Playlist?, List<PlaylistTrack>> {
            var playlist: Playlist? = null
            if (allowCache) {
                KotifyDatabase.transaction("load playlist tracks for id $playlistId") {
                    findById(id = playlistId)
                        ?.also { playlist = it }
                        ?.takeIf { it.hasAllTracks }
                        ?.playlistTracksInOrder
                        ?.live
                }
                    ?.let { return Pair(playlist, it) }
            }

            val networkTracks = Spotify.Playlists.getPlaylistTracks(playlistId = playlistId).asFlow().toList()

            val tracks = KotifyDatabase.transaction("save playlist ${playlist?.name ?: "id $playlistId"} tracks") {
                (playlist ?: findById(id = playlistId))?.let { playlist ->
                    playlist.tracksFetched = Instant.now()
                    // TODO PlaylistRepository.updateLiveState(id = playlistId, value = playlist)
                }

                networkTracks.mapIndexedNotNull { index, track ->
                    PlaylistTrack.from(spotifyPlaylistTrack = track, playlistId = playlistId, index = index)
                }
            }

            return Pair(playlist, tracks)
        }
    }
}