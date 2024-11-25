package com.dzirbel.kotify.repository.artist

import com.dzirbel.kotify.util.immutable.countBy

class ArtistRecommendationEngine
 {

    fun similarArtistsRecommendation(savedArtists: List<ArtistViewModel>): List<Pair<ArtistViewModel, Int>> {
        return savedArtists.map { it.similarArtists }.flatten().filter { !savedArtists.contains(it) }.countBy { it }
                .toList().sortedBy { (_, value) -> -value }
    }
}