package com.dzirbel.kotify.ui.page.artist

import com.dzirbel.kotify.db.KotifyDatabase
import com.dzirbel.kotify.db.blockingTransaction
import com.dzirbel.kotify.repository.Artist
import com.dzirbel.kotify.repository.ArtistAlbumList
import com.dzirbel.kotify.repository.album.AlbumTracksRepository
import com.dzirbel.kotify.repository.album.SavedAlbumRepository
import com.dzirbel.kotify.repository.artist.ArtistAlbumsRepository
import com.dzirbel.kotify.repository.artist.ArtistRepository
import com.dzirbel.kotify.repository.mockLibrary
import com.dzirbel.kotify.repository.mockStateCached
import com.dzirbel.kotify.repository.mockStateNull
import com.dzirbel.kotify.ui.framework.render
import com.dzirbel.kotify.ui.screenshotTest
import com.dzirbel.kotify.ui.util.RelativeTimeInfo
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ArtistPageScreenshotTest {
    @Test
    fun empty() {
        val artistId = "artistId"

        ArtistRepository.mockStateNull(id = artistId)
        ArtistAlbumsRepository.mockStateNull(id = artistId)

        screenshotTest(filename = "empty") {
            ArtistPage(artistId = artistId).render()
        }
    }

    @Test
    fun full() {
        val now = Instant.now()

        val artist = Artist(fullUpdateTime = now, albumsFetched = now)
        val artistAlbums = ArtistAlbumList(artistId = artist.id.value, count = 20)

        KotifyDatabase.blockingTransaction {
            for (artistAlbum in artistAlbums) {
                artistAlbum.album.loadToCache()
            }
        }

        ArtistRepository.mockStateCached(id = artist.id.value, value = artist, cacheTime = now)
        ArtistAlbumsRepository.mockStateCached(id = artist.id.value, value = artistAlbums, cacheTime = now)
        SavedAlbumRepository.mockLibrary(ids = null)

        for (album in artistAlbums) {
            AlbumTracksRepository.mockStateNull(album.albumId.value)
        }

        RelativeTimeInfo.withMockedTime(now) {
            screenshotTest(filename = "full", windowWidth = 1500) {
                ArtistPage(artistId = artist.id.value).render()
            }
        }
    }
}
