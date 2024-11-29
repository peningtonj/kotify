package com.dzirbel.kotify.repository.artist

import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.model.asFlow
import com.dzirbel.kotify.util.coroutines.combinedStateWhenAllNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlin.math.min


class ArtistRecommendationEngine(
    val artistRepository: ArtistRepository,
    val similarArtistsRepository: SimilarArtistsRepository,
) {

    val DEFAULT_TOP_ARTIST_LIMIT = 20

    fun similarArtistsRecommendation(savedArtists: List<ArtistViewModel>):
            List<Pair<ArtistViewModel, List<ArtistViewModel>>> {
        val similarToSaved = mutableMapOf<ArtistViewModel, MutableList<ArtistViewModel>>()

        savedArtists.map { key ->
            key.similarArtists.map { value ->
                val list = similarToSaved.getOrPut(value) { mutableListOf() }
                list.add(key)
            }
        }

        return similarToSaved.map { (key, values) -> key to values }.filter { !savedArtists.contains(it.first) }
    }

    suspend fun similiarToFavourites(limit : Int = DEFAULT_TOP_ARTIST_LIMIT): List<Pair<ArtistViewModel, String>> {
        val topArtists = Spotify.UsersProfile.topArtists(limit = limit).asFlow().toList().take(limit)
        val artistStateFlow = artistRepository.statesOf(topArtists.map { it.id })
            .combinedStateWhenAllNotNull { it?.cachedValue }

        // Collect the StateFlow until it emits a non-null value
        val artists = artistStateFlow.firstOrNull { it != null } ?: return emptyList()

        val similarArtists = artists.map { it to it.similarArtists.take(min(it.similarArtists.size, 3)) }

        return similarArtists.flatMap { (key, values) -> values.map { it to "Similar to ${key.name}" } }
    }
}

