package com.dzirbel.kotify.network

import com.dzirbel.kotify.Fixtures
import com.dzirbel.kotify.properties.ShowProperties
import com.dzirbel.kotify.TAG_NETWORK
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Tag(TAG_NETWORK)
class SpotifyShowsTest {
    @ParameterizedTest
    @MethodSource("shows")
    fun getShow(showProperties: ShowProperties) {
        val show = runBlocking { Spotify.Shows.getShow(id = showProperties.id) }

        showProperties.check(show)
    }

    @Test
    fun getShowNotFound() {
        val error = runBlocking {
            assertThrows<Spotify.SpotifyError> { Spotify.Shows.getShow(Fixtures.notFoundId) }
        }

        assertThat(error.code).isEqualTo(404)
    }

    @Test
    fun getShows() {
        val shows = runBlocking { Spotify.Shows.getShows(ids = Fixtures.shows.map { it.id }) }

        shows.zip(Fixtures.shows).forEach { (show, showProperties) -> showProperties.check(show) }
    }

    @ParameterizedTest
    @MethodSource("shows")
    fun getShowEpisodes(showProperties: ShowProperties) {
        val episodes = runBlocking { Spotify.Shows.getShowEpisodes(id = showProperties.id) }

        assertThat(episodes.items).isNotEmpty()
    }

    companion object {
        @JvmStatic
        @Suppress("unused")
        fun shows() = Fixtures.shows
    }
}