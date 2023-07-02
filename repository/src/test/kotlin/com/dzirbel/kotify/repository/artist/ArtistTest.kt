package com.dzirbel.kotify.repository.artist

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSameSizeAs
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.dzirbel.kotify.db.model.Artist
import com.dzirbel.kotify.network.model.FullSpotifyArtist
import com.dzirbel.kotify.network.model.SimplifiedSpotifyAlbum
import com.dzirbel.kotify.network.model.SimplifiedSpotifyArtist
import com.dzirbel.kotify.network.model.SpotifyAlbum
import com.dzirbel.kotify.network.model.SpotifyExternalUrl
import com.dzirbel.kotify.network.model.SpotifyFollowers
import com.dzirbel.kotify.network.model.SpotifyImage
import com.dzirbel.kotify.util.containsExactlyElementsOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

// TODO tests both Artist db models directly and repository
internal class ArtistTest {
    @Test
    fun testFromSimplified() {
        val artist = transaction { Artist.from(Fixtures.simplifiedArtist) }
        requireNotNull(artist)

        transaction {
            assertThat(artist.id.value).isEqualTo(Fixtures.simplifiedArtist.id)
            assertThat(artist.name).isEqualTo(Fixtures.simplifiedArtist.name)

            assertThat(artist.popularity).isNull()
            assertThat(artist.followersTotal).isNull()

            assertThat(artist.images.live).isEmpty()
            assertThat(artist.genres.live).isEmpty()

            assertThat(artist.trackIds.live).isEmpty()
            assertThat(artist.artistAlbums.live).isEmpty()
            assertThat(artist.hasAllAlbums).isFalse()

            assertThat(artist.largestImage.live).isNull()
        }
    }

    @Test
    fun testFromFull() {
        val artist = transaction { Artist.from(Fixtures.fullArtist) }
        requireNotNull(artist)

        transaction {
            assertThat(artist.id.value).isEqualTo(Fixtures.fullArtist.id)
            assertThat(artist.name).isEqualTo(Fixtures.fullArtist.name)

            assertThat(artist.popularity).isEqualTo(Fixtures.fullArtist.popularity)
            assertThat(artist.followersTotal).isEqualTo(Fixtures.fullArtist.followers.total)

            assertThat(artist.images.live.map { it.url }).containsExactlyInAnyOrder("url 1", "url 2")
            assertThat(artist.largestImage.live?.url).isEqualTo("url 2")
            assertThat(artist.genres.live.map { it.name }).containsExactlyInAnyOrder("genre 1", "genre 2")

            assertThat(artist.trackIds.live).isEmpty()
            assertThat(artist.artistAlbums.live).isEmpty()
            assertThat(artist.hasAllAlbums).isFalse()
        }
    }

    @Test
    fun testGetAllAlbumsWithArtistEntity() {
        val artist = transaction { Artist.from(Fixtures.simplifiedArtist) }
        requireNotNull(artist)

        val albums = runBlocking {
            ArtistRepository.getAllAlbums(
                artistId = artist.id.value,
                allowCache = true,
                fetchAlbums = { Fixtures.albums },
            )
        }

        assertThat(albums).hasSameSizeAs(Fixtures.albums)
        assertThat(albums.map { it.albumId.value }).containsExactlyElementsOf(Fixtures.albums.map { it.id })
        assertThat(albums.map { it.album.cached.name }).containsExactlyElementsOf(Fixtures.albums.map { it.name })
        assertThat(albums.map { it.albumGroup }).containsExactlyElementsOf(Fixtures.albums.map { it.albumGroup })

        val cachedAlbums = runBlocking {
            ArtistRepository.getAllAlbums(artistId = "id1", allowCache = true, fetchAlbums = { emptyList() })
        }

        // cached albums use previously fetched values
        assertThat(cachedAlbums).hasSameSizeAs(Fixtures.albums)
        assertThat(cachedAlbums.map { it.albumId.value }).containsExactlyElementsOf(Fixtures.albums.map { it.id })
        assertThat(cachedAlbums.map { it.album.cached.name }).containsExactlyElementsOf(Fixtures.albums.map { it.name })
        assertThat(cachedAlbums.map { it.albumGroup }).containsExactlyElementsOf(Fixtures.albums.map { it.albumGroup })
    }

    @Test
    fun testGetAllAlbumsNoArtistEntity() {
        val albums = runBlocking {
            ArtistRepository.getAllAlbums(
                artistId = "id1",
                allowCache = true,
                fetchAlbums = { Fixtures.albums },
            )
        }

        assertThat(albums).hasSameSizeAs(Fixtures.albums)
        assertThat(albums.map { it.albumId.value }).containsExactlyElementsOf(Fixtures.albums.map { it.id })
        assertThat(albums.map { it.album.cached.name }).containsExactlyElementsOf(Fixtures.albums.map { it.name })
        assertThat(albums.map { it.albumGroup }).containsExactlyElementsOf(Fixtures.albums.map { it.albumGroup })

        val cachedAlbums = runBlocking {
            ArtistRepository.getAllAlbums(artistId = "id1", allowCache = true, fetchAlbums = { emptyList() })
        }

        // cached albums use new fetched values (empty) because artist entity does not exist
        assertThat(cachedAlbums).isEmpty()
    }

    private object Fixtures {
        val simplifiedArtist = SimplifiedSpotifyArtist(
            externalUrls = SpotifyExternalUrl(),
            id = "id1",
            name = "test artist",
            type = "artist",
        )

        val fullArtist = FullSpotifyArtist(
            externalUrls = SpotifyExternalUrl(),
            id = "id1",
            href = "",
            uri = "",
            name = "test artist",
            type = "artist",
            followers = SpotifyFollowers(total = 3),
            genres = listOf("genre 1", "genre 2"),
            images = listOf(
                SpotifyImage(
                    url = "url 1",
                    width = 10,
                    height = 20,
                ),
                SpotifyImage(
                    url = "url 2",
                    width = 15,
                    height = 25,
                ),
            ),
            popularity = 4,
        )

        val albums = listOf(
            SimplifiedSpotifyAlbum(
                id = "album1",
                artists = emptyList(),
                externalUrls = emptyMap(),
                images = emptyList(),
                name = "album 1",
                type = "album",
            ),
            SimplifiedSpotifyAlbum(
                id = "album2",
                artists = emptyList(),
                externalUrls = emptyMap(),
                images = emptyList(),
                name = "album 2",
                type = "album",
                albumGroup = SpotifyAlbum.Type.ALBUM,
            ),
        )
    }
}