package com.dzirbel.kotify.network

import assertk.assertThat
import assertk.assertions.hasSameSizeAs
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.dzirbel.kotify.Fixtures
import com.dzirbel.kotify.TAG_NETWORK
import com.dzirbel.kotify.properties.TrackProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Tag(TAG_NETWORK)
internal class SpotifyTracksTest {
    @ParameterizedTest
    @MethodSource("tracks")
    fun getAudioFeatures(trackProperties: TrackProperties) {
        val audioFeatures = runBlocking { Spotify.Tracks.getAudioFeatures(trackProperties.id!!) }

        assertThat(audioFeatures).isNotNull()
    }

    @Test
    fun getAudioFeatures() {
        val audioFeatures = runBlocking { Spotify.Tracks.getAudioFeatures(Fixtures.tracks.map { it.id!! }) }

        assertThat(audioFeatures).hasSameSizeAs(Fixtures.tracks)
    }

    @ParameterizedTest
    @MethodSource("tracks")
    fun getAudioAnalysis(trackProperties: TrackProperties) {
        val audioAnalysis = runBlocking { Spotify.Tracks.getAudioAnalysis(trackProperties.id!!) }

        assertThat(audioAnalysis).isNotNull()
    }

    @ParameterizedTest
    @MethodSource("tracks")
    fun getTrack(trackProperties: TrackProperties) {
        val track = runBlocking { Spotify.Tracks.getTrack(trackProperties.id!!) }

        trackProperties.check(track)
    }

    @Test
    fun getTrackNotFound() {
        val error = runBlocking {
            assertThrows<Spotify.SpotifyError> { Spotify.Tracks.getTrack(Fixtures.notFoundId) }
        }

        assertThat(error.code).isEqualTo(404)
    }

    @Test
    fun getTracks() {
        val tracks = runBlocking { Spotify.Tracks.getTracks(Fixtures.tracks.map { it.id!! }) }

        tracks.zip(Fixtures.tracks).forEach { (track, trackProperties) -> trackProperties.check(track) }
    }

    companion object {
        @JvmStatic
        @Suppress("unused")
        fun tracks() = Fixtures.tracks
    }
}
