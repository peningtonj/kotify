package com.dominiczirbel.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SpotifySearchTest {
    @Test
    fun search() {
        val results = runBlocking {
            Spotify.Search.search(
                q = "blackbriar",
                type = listOf("artist,track")
            )
        }

        assertThat(results.artists).isNotNull()
        assertThat(results.tracks).isNotNull()

        assertThat(results.albums).isNull()
        assertThat(results.playlists).isNull()
        assertThat(results.shows).isNull()
        assertThat(results.episodes).isNull()

        assertThat(results.artists?.items?.any { it.id == "6PXQUX3BYTSVj7LcvviOmI" }).isTrue()
        assertThat(results.tracks?.items?.any { track -> track.artists.any { it.id == "6PXQUX3BYTSVj7LcvviOmI" } })
            .isTrue()
    }
}