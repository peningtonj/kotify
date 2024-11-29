package com.dzirbel.kotify.ui.properties

import com.dzirbel.kotify.db.model.AlbumType
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.repository.rating.RatingRepository
import com.dzirbel.kotify.ui.components.adapter.filters.FilterByValueList

class AlbumTypeFilterProperty(types: List<AlbumType?>, override val title: String) :
    FilterByValueList<AlbumViewModel, AlbumType?>(
        title = title,
        values = types,
        pGetter = {album -> album.albumType}
    )

class AlbumRatingFilterProperty(ratings : List<Int?>, override val title: String, ratingRepository: RatingRepository) :
    FilterByValueList<AlbumViewModel, Int?>(
        title = title,
        values = ratings,
        pGetter = {album -> ratingRepository.ratingStateOf(album.id).value?.rating ?: 0}
    )


class AlbumGenreFilterProperty(genres : List<String>, override val title: String) :
    FilterByValueList<AlbumViewModel, String>(
        title = title,
        values = genres,
        pGetter = {album ->
            album.artists.value?.map { artist ->
                artist.genres.value?.map { genre ->
                    genre.name
                }
            }.toString()
        }
    ) {
        override fun filterFunction(): (AlbumViewModel) -> Boolean {
            return {element -> allowedValues.any {pGetter(element).contains(it) }}
        }

        fun addValues(values: Set<String>) {
            allValues.addAll(values)
        }

}
