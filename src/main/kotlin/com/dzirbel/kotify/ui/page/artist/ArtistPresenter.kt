package com.dzirbel.kotify.ui.page.artist

import androidx.compose.runtime.State
import com.dzirbel.kotify.cache.SpotifyImageCache
import com.dzirbel.kotify.db.KotifyDatabase
import com.dzirbel.kotify.db.model.Artist
import com.dzirbel.kotify.db.model.ArtistAlbum
import com.dzirbel.kotify.db.model.ArtistRepository
import com.dzirbel.kotify.db.model.SavedAlbumRepository
import com.dzirbel.kotify.db.model.TrackRatingRepository
import com.dzirbel.kotify.network.model.SpotifyAlbum
import com.dzirbel.kotify.repository.Rating
import com.dzirbel.kotify.ui.components.adapter.AdapterProperty
import com.dzirbel.kotify.ui.components.adapter.Divider
import com.dzirbel.kotify.ui.components.adapter.ListAdapter
import com.dzirbel.kotify.ui.components.adapter.Sort
import com.dzirbel.kotify.ui.framework.Presenter
import com.dzirbel.kotify.ui.properties.AlbumNameProperty
import com.dzirbel.kotify.ui.properties.AlbumRatingProperty
import com.dzirbel.kotify.ui.properties.AlbumReleaseDateProperty
import com.dzirbel.kotify.ui.properties.AlbumTypeDividableProperty
import com.dzirbel.kotify.ui.util.requireValue
import com.dzirbel.kotify.util.zipToMap
import kotlinx.coroutines.CoroutineScope

class ArtistPresenter(
    private val artistId: String,
    scope: CoroutineScope,
) : Presenter<ArtistPresenter.ViewModel, ArtistPresenter.Event>(
    scope = scope,
    key = artistId,
    startingEvents = listOf(
        Event.LoadArtist(invalidate = false),
        Event.LoadArtistAlbums(invalidate = false),
    ),
    initialState = ViewModel(),
) {

    data class ViewModel(
        val artist: State<Artist>? = null,
        val refreshingArtist: Boolean = false,
        val displayedAlbumTypes: Set<SpotifyAlbum.Type> = setOf(SpotifyAlbum.Type.ALBUM),
        val artistAlbums: ListAdapter<ArtistAlbum> = ListAdapter.empty(
            defaultSort = AlbumReleaseDateProperty.ForArtistAlbum,
            defaultFilter = filterFor(displayedAlbumTypes),
        ),
        val albumRatings: Map<String, List<State<Rating?>>?> = emptyMap(),
        val savedAlbumsStates: Map<String, State<Boolean?>>? = null,
        val refreshingArtistAlbums: Boolean = false,
    ) {
        val artistAlbumProperties: List<AdapterProperty<ArtistAlbum>> = listOf(
            AlbumNameProperty.ForArtistAlbum,
            AlbumReleaseDateProperty.ForArtistAlbum,
            AlbumTypeDividableProperty.ForArtistAlbum,
            AlbumRatingProperty.ForArtistAlbum(ratings = albumRatings),
        )
    }

    sealed class Event {
        data class LoadArtist(val invalidate: Boolean) : Event()
        data class LoadArtistAlbums(val invalidate: Boolean) : Event()

        class ToggleSave(val albumId: String, val save: Boolean) : Event()
        class SetSorts(val sorts: List<Sort<ArtistAlbum>>) : Event()
        class SetDivider(val divider: Divider<ArtistAlbum>?) : Event()
        class SetDisplayedAlbumTypes(val albumTypes: Set<SpotifyAlbum.Type>) : Event()
    }

    override suspend fun reactTo(event: Event) {
        when (event) {
            is Event.LoadArtist -> {
                mutateState { it.copy(refreshingArtist = true) }

                val artistState = ArtistRepository.stateOf(id = artistId)
                    .requireValue { throw NotFound("Artist $artistId not found") }

                mutateState {
                    it.copy(artist = artistState, refreshingArtist = false)
                }
            }

            is Event.LoadArtistAlbums -> {
                mutateState { it.copy(refreshingArtistAlbums = true) }

                val (artist, artistAlbums) = Artist.getAllAlbums(artistId = artistId, allowCache = !event.invalidate)

                val albumUrls = KotifyDatabase.transaction("load artist ${artist?.name} albums tracks and image") {
                    artistAlbums.mapNotNull { artistAlbum ->
                        artistAlbum.album.cached.trackIds.loadToCache()
                        artistAlbum.album.cached.largestImage.live?.url
                    }
                }
                SpotifyImageCache.loadFromFileCache(urls = albumUrls, scope = scope)

                val albumIds = artistAlbums.map { it.albumId.value }
                val savedAlbumsStates = albumIds.zipToMap(SavedAlbumRepository.stateOf(ids = albumIds))

                val albumRatings = artistAlbums.associate { artistAlbum ->
                    val album = artistAlbum.album.cached
                    album.id.value to album.trackIds.cached.let { TrackRatingRepository.ratingStates(ids = it) }
                }

                mutateState {
                    // TODO refresh artist album updated time in state
                    it.copy(
                        artistAlbums = it.artistAlbums.withElements(artistAlbums),
                        albumRatings = albumRatings,
                        savedAlbumsStates = savedAlbumsStates,
                        refreshingArtistAlbums = false,
                    )
                }
            }

            is Event.ToggleSave -> SavedAlbumRepository.setSaved(id = event.albumId, saved = event.save)

            is Event.SetSorts -> mutateState {
                it.copy(artistAlbums = it.artistAlbums.withSort(event.sorts))
            }

            is Event.SetDivider -> mutateState {
                it.copy(artistAlbums = it.artistAlbums.withDivider(divider = event.divider))
            }

            is Event.SetDisplayedAlbumTypes -> mutateState {
                it.copy(
                    displayedAlbumTypes = event.albumTypes,
                    artistAlbums = it.artistAlbums.withFilter(filter = filterFor(event.albumTypes)),
                )
            }
        }
    }

    companion object {
        private fun filterFor(albumTypes: Set<SpotifyAlbum.Type>): ((ArtistAlbum) -> Boolean)? {
            return if (albumTypes.isNotEmpty()) {
                { album -> albumTypes.contains(album.albumGroup) }
            } else {
                null
            }
        }
    }
}
