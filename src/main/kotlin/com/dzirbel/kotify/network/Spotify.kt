package com.dzirbel.kotify.network

import com.dzirbel.kotify.network.model.Album
import com.dzirbel.kotify.network.model.AudioAnalysis
import com.dzirbel.kotify.network.model.AudioFeatures
import com.dzirbel.kotify.network.model.Category
import com.dzirbel.kotify.network.model.CursorPaging
import com.dzirbel.kotify.network.model.FullAlbum
import com.dzirbel.kotify.network.model.FullArtist
import com.dzirbel.kotify.network.model.FullEpisode
import com.dzirbel.kotify.network.model.FullPlaylist
import com.dzirbel.kotify.network.model.FullShow
import com.dzirbel.kotify.network.model.FullTrack
import com.dzirbel.kotify.network.model.Image
import com.dzirbel.kotify.network.model.Paging
import com.dzirbel.kotify.network.model.PlayHistoryObject
import com.dzirbel.kotify.network.model.Playback
import com.dzirbel.kotify.network.model.PlaybackDevice
import com.dzirbel.kotify.network.model.PlaylistTrack
import com.dzirbel.kotify.network.model.PrivateUser
import com.dzirbel.kotify.network.model.PublicUser
import com.dzirbel.kotify.network.model.Recommendations
import com.dzirbel.kotify.network.model.SavedAlbum
import com.dzirbel.kotify.network.model.SavedShow
import com.dzirbel.kotify.network.model.SavedTrack
import com.dzirbel.kotify.network.model.SimplifiedAlbum
import com.dzirbel.kotify.network.model.SimplifiedEpisode
import com.dzirbel.kotify.network.model.SimplifiedPlaylist
import com.dzirbel.kotify.network.model.SimplifiedShow
import com.dzirbel.kotify.network.model.SimplifiedTrack
import com.dzirbel.kotify.network.model.TrackPlayback
import com.dzirbel.kotify.network.oauth.AccessToken
import com.dzirbel.kotify.ui.util.assertNotOnUIThread
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.Locale

/**
 * https://developer.spotify.com/documentation/web-api/reference/
 */
object Spotify {
    data class Configuration(
        val okHttpClient: OkHttpClient = OkHttpClient(),
        val oauthOkHttpClient: OkHttpClient = OkHttpClient()
    )

    var configuration: Configuration = Configuration()

    const val FROM_TOKEN = "from_token"
    const val API_URL = "https://api.spotify.com/v1/"

    /**
     * The maximum number of sent/returned items for most endpoints.
     */
    const val MAX_LIMIT = 50

    class SpotifyError(val code: Int, message: String) : Throwable(message = "HTTP $code : $message")

    @Serializable
    data class ErrorObject(val error: ErrorDetails)

    @Serializable
    data class ErrorDetails(val status: Int, val message: String)

    @Serializable
    private data class AlbumsModel(val albums: List<FullAlbum>)

    @Serializable
    private data class AlbumsPagingModel(val albums: Paging<SimplifiedAlbum>)

    @Serializable
    private data class ArtistsModel(val artists: List<FullArtist>)

    @Serializable
    data class ArtistsCursorPagingModel(val artists: CursorPaging<FullArtist>)

    @Serializable
    private data class AudioFeaturesModel(@SerialName("audio_features") val audioFeatures: List<AudioFeatures>)

    @Serializable
    private data class CategoriesModel(val categories: Paging<Category>)

    @Serializable
    private data class EpisodesModel(val episodes: List<FullEpisode>)

    @Serializable
    private data class PlaylistPagingModel(val playlists: Paging<SimplifiedPlaylist>, val message: String? = null)

    @Serializable
    private data class RecommendationGenresModel(val genres: List<String>)

    @Serializable
    private data class ShowsModel(val shows: List<SimplifiedShow>)

    @Serializable
    private data class SnaphshotId(@SerialName("snapshot_id") val snapshotId: String)

    @Serializable
    private data class TracksModel(val tracks: List<FullTrack>)

    suspend inline fun <reified T : Any?> get(path: String, queryParams: Map<String, String?>? = null): T {
        return request(method = "GET", path = path, queryParams = queryParams, body = null)
    }

    suspend inline fun <reified In : Any?, reified Out> post(
        path: String,
        jsonBody: In,
        queryParams: Map<String, String?>? = null
    ): Out {
        return request(
            method = "POST",
            path = path,
            queryParams = queryParams,
            body = Json.encodeToString(jsonBody).toRequestBody()
        )
    }

    suspend inline fun <reified In : Any?, reified Out> put(
        path: String,
        jsonBody: In,
        queryParams: Map<String, String?>? = null
    ): Out {
        return request(
            method = "PUT",
            path = path,
            queryParams = queryParams,
            body = Json.encodeToString(jsonBody).toRequestBody()
        )
    }

    suspend inline fun <reified In : Any?, reified Out> delete(
        path: String,
        jsonBody: In,
        queryParams: Map<String, String?>? = null
    ): Out {
        return request(
            method = "DELETE",
            path = path,
            queryParams = queryParams,
            body = Json.encodeToString(jsonBody).toRequestBody()
        )
    }

    suspend inline fun <reified T : Any> request(
        method: String,
        path: String,
        queryParams: Map<String, String?>? = null,
        body: RequestBody? = null
    ): T {
        assertNotOnUIThread()

        val token = AccessToken.Cache.getOrThrow()

        val url = (if (path.startsWith(API_URL)) path else API_URL + path).toHttpUrl()
            .newBuilder()
            .apply {
                queryParams?.forEach { (key, value) ->
                    value?.let { addQueryParameter(key, it) }
                }
            }
            .build()

        val request = Request.Builder()
            .method(method, body)
            .url(url)
            .header("Authorization", "${token.tokenType} ${token.accessToken}")
            .build()

        return configuration.okHttpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                val message = runCatching { response.bodyFromJson<ErrorObject>() }
                    .getOrNull()
                    ?.error
                    ?.message
                    ?: response.message
                throw SpotifyError(code = response.code, message = message)
            }

            response.bodyFromJson()
        }
    }

    /**
     * Endpoints for retrieving information about one or more albums from the Spotify catalog.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-albums
     */
    object Albums {
        /**
         * Get Spotify catalog information for a single album.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-album
         *
         * @param id The Spotify ID for the album.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking.
         */
        suspend fun getAlbum(id: String, market: String? = null): FullAlbum {
            return get("albums/$id", mapOf("market" to market))
        }

        /**
         * Get Spotify catalog information about an album’s tracks. Optional parameters can be used to limit the number
         * of tracks returned.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-albums-tracks
         *
         * @param id The Spotify ID for the album.
         * @param limit Optional. The maximum number of tracks to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first track to return. Default: 0 (the first object). Use with limit
         *  to get the next set of tracks.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking.
         */
        suspend fun getAlbumTracks(
            id: String,
            limit: Int? = null,
            offset: Int? = null,
            market: String? = null
        ): Paging<SimplifiedTrack> {
            return get(
                "albums/$id/tracks",
                mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "market" to market)
            )
        }

        /**
         * Get Spotify catalog information for multiple albums identified by their Spotify IDs.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-multiple-albums
         *
         * @param ids Required. A comma-separated list of the Spotify IDs for the albums. Maximum: 20 IDs.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking.
         */
        suspend fun getAlbums(ids: List<String>, market: String? = null): List<FullAlbum> {
            return get<AlbumsModel>(
                "albums",
                mapOf("ids" to ids.joinToString(separator = ","), "market" to market)
            ).albums
        }
    }

    /**
     * Endpoints for retrieving information about one or more artists from the Spotify catalog.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-artists
     */
    object Artists {
        /**
         * Get Spotify catalog information for a single artist identified by their unique Spotify ID.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-artist
         *
         * @param id The Spotify ID for the artist.
         */
        suspend fun getArtist(id: String): FullArtist = get("artists/$id")

        /**
         * Get Spotify catalog information for several artists based on their Spotify IDs.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-multiple-artists
         *
         * @param ids A comma-separated list of the Spotify IDs for the artists. Maximum: 50 IDs.
         */
        suspend fun getArtists(ids: List<String>): List<FullArtist> {
            return get<ArtistsModel>("artists", mapOf("ids" to ids.joinToString(separator = ","))).artists
        }

        /**
         * Get Spotify catalog information about an artist’s albums.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-artists-albums
         *
         * @param id The Spotify ID for the artist.
         * @param includeGroups Optional. A comma-separated list of keywords that will be used to filter the response.
         *  If not supplied, all album types will be returned. Valid values are:
         *  - album
         *  - single
         *  - appears_on
         *  - compilation
         *  For example: include_groups=album,single.
         * @param country Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Supply this parameter
         *  to limit the response to one particular geographical market. For example, for albums available in Sweden:
         *  country=SE. If not given, results will be returned for all countries and you are likely to get duplicate
         *  results per album, one for each country in which the album is available!
         * @param limit The number of album objects to return. Default: 20. Minimum: 1. Maximum: 50. For example:
         *  limit=2
         * @Param offset The index of the first album to return. Default: 0 (i.e., the first album). Use with limit to
         *  get the next set of albums.
         */
        suspend fun getArtistAlbums(
            id: String,
            includeGroups: List<Album.Type>? = null,
            country: String? = null,
            limit: Int? = null,
            offset: Int? = null
        ): Paging<SimplifiedAlbum> {
            return get(
                "artists/$id/albums",
                mapOf(
                    "include_groups" to includeGroups?.joinToString(separator = ",") { it.name.toLowerCase(Locale.US) },
                    "country" to country,
                    "limit" to limit?.toString(),
                    "offset" to offset?.toString()
                )
            )
        }

        /**
         * Get Spotify catalog information about an artist’s top tracks by country.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-artists-top-tracks
         *
         * @param id The Spotify ID for the artist
         * @param country Required. An ISO 3166-1 alpha-2 country code or the string from_token.
         */
        suspend fun getArtistTopTracks(id: String, country: String): List<FullTrack> {
            return get<TracksModel>("artists/$id/top-tracks", mapOf("country" to country)).tracks
        }

        /**
         * Get Spotify catalog information about artists similar to a given artist. Similarity is based on analysis of
         * the Spotify community’s listening history.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-artists-related-artists
         *
         * @param id The Spotify ID for the artist
         */
        suspend fun getArtistRelatedArtists(id: String): List<FullArtist> {
            return get<ArtistsModel>("artists/$id/related-artists").artists
        }
    }

    /**
     * Endpoints for getting playlists and new album releases featured on Spotify’s Browse tab.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-browse
     */
    object Browse {
        /**
         * Get a single category used to tag items in Spotify (on, for example, the Spotify player’s "Browse" tab).
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-a-category
         *
         * @param categoryId The Spotify category ID for the category.
         * @param country Optional. A country: an ISO 3166-1 alpha-2 country code. Provide this parameter to ensure that
         *  the category exists for a particular country.
         * @param locale Optional. The desired language, consisting of an ISO 639-1 language code and an ISO 3166-1
         *  alpha-2 country code, joined by an underscore. For example: es_MX, meaning "Spanish (Mexico)". Provide this
         *  parameter if you want the category strings returned in a particular language. Note that, if locale is not
         *  supplied, or if the specified language is not available, the category strings returned will be in the
         *  Spotify default language (American English).
         */
        suspend fun getCategory(categoryId: String, country: String? = null, locale: String? = null): Category {
            return get("browse/categories/$categoryId", mapOf("country" to country, "locale" to locale))
        }

        /**
         * Get a list of Spotify playlists tagged with a particular category.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-a-categories-playlists
         *
         * @param categoryId The Spotify category ID for the category.
         * @param country Optional. A country: an ISO 3166-1 alpha-2 country code.
         * @param limit Optional. The maximum number of items to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first item to return. Default: 0 (the first object). Use with limit
         *  to get the next set of items.
         */
        suspend fun getCategoryPlaylists(
            categoryId: String,
            country: String? = null,
            limit: Int? = null,
            offset: Int? = null
        ): Paging<SimplifiedPlaylist> {
            return get<PlaylistPagingModel>(
                "browse/categories/$categoryId/playlists",
                mapOf("country" to country, "limit" to limit?.toString(), "offset" to offset?.toString())
            ).playlists
        }

        /**
         * Get a list of categories used to tag items in Spotify (on, for example, the Spotify player’s “Browse” tab).
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-categories
         *
         * @param country Optional. A country: an ISO 3166-1 alpha-2 country code. Provide this parameter if you want to
         *  narrow the list of returned categories to those relevant to a particular country. If omitted, the returned
         *  items will be globally relevant.
         * @param locale Optional. The desired language, consisting of an ISO 639-1 language code and an ISO 3166-1
         *  alpha-2 country code, joined by an underscore. For example: es_MX, meaning “Spanish (Mexico)”. Provide this
         *  parameter if you want the category metadata returned in a particular language. Note that, if locale is not
         *  supplied, or if the specified language is not available, all strings will be returned in the Spotify default
         *  language (American English). The locale parameter, combined with the country parameter, may give odd results
         *  if not carefully matched. For example country=SE&locale=de_DE will return a list of categories relevant to
         *  Sweden but as German language strings.
         * @param limit Optional. The maximum number of categories to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first item to return. Default: 0 (the first object). Use with limit
         *  to get the next set of categories.
         */
        suspend fun getCategories(
            country: String? = null,
            locale: String? = null,
            limit: Int? = null,
            offset: Int? = null
        ): Paging<Category> {
            return get<CategoriesModel>(
                "browse/categories",
                mapOf(
                    "country" to country,
                    "locale" to locale,
                    "limit" to limit?.toString(),
                    "offset" to offset?.toString()
                )
            ).categories
        }

        /**
         * Get a list of Spotify featured playlists (shown, for example, on a Spotify player's 'Browse' tab).
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-featured-playlists
         *
         * @param locale Optional. The desired language, consisting of a lowercase ISO 639-1 language code and an
         *  uppercase ISO 3166-1 alpha-2 country code, joined by an underscore. For example: es_MX, meaning "Spanish
         *  (Mexico)". Provide this parameter if you want the results returned in a particular language (where
         *  available). Note that, if locale is not supplied, or if the specified language is not available, all strings
         *  will be returned in the Spotify default language (American English). The locale parameter, combined with the
         *  country parameter, may give odd results if not carefully matched. For example country=SE&locale=de_DE will
         *  return a list of categories relevant to Sweden but as German language strings.
         * @param country Optional. A country: an ISO 3166-1 alpha-2 country code. Provide this parameter if you want
         *  the list of returned items to be relevant to a particular country. If omitted, the returned items will be
         *  relevant to all countries.
         * @param timestamp Optional. A timestamp in ISO 8601 format: yyyy-MM-ddTHH:mm:ss. Use this parameter to specify
         *  the user’s local time to get results tailored for that specific date and time in the day. If not provided,
         *  the response defaults to the current UTC time. Example: "2014-10-23T09:00:00" for a user whose local time is
         *  9AM. If there were no featured playlists (or there is no data) at the specified time, the response will
         *  revert to the current UTC time.
         * @param limit Optional. The maximum number of items to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first item to return. Default: 0 (the first object). Use with limit
         *  to get the next set of items.
         */
        suspend fun getFeaturedPlaylists(
            locale: String? = null,
            country: String? = null,
            timestamp: String? = null,
            limit: Int? = null,
            offset: Int? = null
        ): Paging<SimplifiedPlaylist> {
            return get<PlaylistPagingModel>(
                "browse/featured-playlists",
                mapOf(
                    "locale" to locale,
                    "country" to country,
                    "timestamp" to timestamp,
                    "limit" to limit?.toString(),
                    "offset" to offset?.toString()
                )
            ).playlists
        }

        /**
         * Get a list of new album releases featured in Spotify (shown, for example, on a Spotify player's "Browse"
         * tab).
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-new-releases
         *
         * @param country Optional. A country: an ISO 3166-1 alpha-2 country code. Provide this parameter if you want
         *  the list of returned items to be relevant to a particular country. If omitted, the returned items will be
         *  relevant to all countries.
         * @param limit Optional. The maximum number of items to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first item to return. Default: 0 (the first object). Use with limit
         *  to get the next set of items.
         */
        suspend fun getNewReleases(
            country: String? = null,
            limit: Int? = null,
            offset: Int? = null
        ): Paging<SimplifiedAlbum> {
            return get<AlbumsPagingModel>(
                "browse/new-releases",
                mapOf("country" to country, "limit" to limit?.toString(), "offset" to offset?.toString())
            ).albums
        }

        /**
         * Create a playlist-style listening experience based on seed artists, tracks and genres.
         *
         * Recommendations are generated based on the available information for a given seed entity and matched against
         * similar artists and tracks. If there is sufficient information about the provided seeds, a list of tracks
         * will be returned together with pool size details.
         *
         * For artists and tracks that are very new or obscure there might not be enough data to generate a list of
         * tracks.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-recommendations
         *
         * @param limit Optional. The target size of the list of recommended tracks. For seeds with unusually small
         *  pools or when highly restrictive filtering is applied, it may be impossible to generate the requested number
         *  of recommended tracks. Debugging information for such cases is available in the response. Default: 20.
         *  Minimum: 1. Maximum: 100.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking. Because min_*, max_* and target_* are applied to pools before
         *  relinking, the generated results may not precisely match the filters applied. Original, non-relinked tracks
         *  are available via the linked_from attribute of the relinked track response.
         * @param seedArtists A comma separated list of Spotify IDs for seed artists. Up to 5 seed values may be
         *  provided in any combination of seed_artists, seed_tracks and seed_genres.
         * @param seedGenres A comma separated list of any genres in the set of available genre seeds. Up to 5 seed
         *  values may be provided in any combination of seed_artists, seed_tracks and seed_genres.
         * @param seedTracks A comma separated list of Spotify IDs for a seed track. Up to 5 seed values may be provided
         *  in any combination of seed_artists, seed_tracks and seed_genres.
         * @param tunableTrackAttributes A set maximums, minimums, and targets for tunable track attributes. See the
         *  Spotify documentation for details.
         */
        suspend fun getRecommendations(
            limit: Int? = null,
            market: String? = null,
            seedArtists: List<String>,
            seedGenres: List<String>,
            seedTracks: List<String>,
            tunableTrackAttributes: Map<String, String> = emptyMap()
        ): Recommendations {
            return get(
                "recommendations",
                mapOf(
                    "limit" to limit?.toString(),
                    "market" to market,
                    "seed_artists" to seedArtists.joinToString(separator = ","),
                    "seed_genres" to seedGenres.joinToString(separator = ","),
                    "seed_tracks" to seedTracks.joinToString(separator = ",")
                ).plus(tunableTrackAttributes)
            )
        }

        /**
         * Retrieve a list of available genres seed parameter values for recommendations.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-recommendation-genres
         */
        suspend fun getRecommendationGenres(): List<String> {
            return get<RecommendationGenresModel>("recommendations/available-genre-seeds").genres
        }
    }

    /**
     * Endpoints for retrieving information about one or more episodes from the Spotify catalog.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-episodes
     */
    object Episodes {
        /**
         * Get Spotify catalog information for a single episode identified by its unique Spotify ID.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-an-episode
         *
         * @param id The Spotify ID for the episode.
         * @param market Optional. An ISO 3166-1 alpha-2 country code. If a country code is specified, only shows and
         *  episodes that are available in that market will be returned. If a valid user access token is specified in
         *  the request header, the country associated with the user account will take priority over this parameter.
         *  Note: If neither market or user country are provided, the content is considered unavailable for the client.
         *  Users can view the country that is associated with their account in the account settings.
         */
        suspend fun getEpisode(id: String, market: String? = null): FullEpisode {
            return get("episodes/$id", mapOf("market" to market))
        }

        /**
         * Get Spotify catalog information for multiple episodes based on their Spotify IDs.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-multiple-episodes
         *
         * @param ids Required. A comma-separated list of the Spotify IDs for the episodes. Maximum: 50 IDs.
         * @param market Optional. An ISO 3166-1 alpha-2 country code. If a country code is specified, only shows and
         *  episodes that are available in that market will be returned. If a valid user access token is specified in
         *  the request header, the country associated with the user account will take priority over this parameter.
         *  Note: If neither market or user country are provided, the content is considered unavailable for the client.
         *  Users can view the country that is associated with their account in the account settings.
         */
        suspend fun getEpisodes(ids: List<String>, market: String? = null): List<FullEpisode> {
            return get<EpisodesModel>(
                "episodes",
                mapOf("ids" to ids.joinToString(separator = ","), "market" to market)
            ).episodes
        }
    }

    /**
     * Endpoints for managing the artists, users, and playlists that a Spotify user follows.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-follow
     */
    object Follow {
        /**
         * Check to see if the current user is following one or more artists or other Spotify users.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-check-current-user-follows
         *
         * @param type Required. The ID type: either artist or user.
         * @param ids Required. A comma-separated list of the artist or the user Spotify IDs to check. For example:
         *  ids=74ASZWbe4lXaubB36ztrGX,08td7MxkoHQkXnWAYD8d6Q. A maximum of 50 IDs can be sent in one request.
         */
        suspend fun isFollowing(type: String, ids: List<String>): List<Boolean> {
            return get("me/following/contains", mapOf("type" to type, "ids" to ids.joinToString(separator = ",")))
        }

        /**
         * Check to see if one or more Spotify users are following a specified playlist.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-check-if-user-follows-playlist
         *
         * @param playlistId The Spotify ID of the playlist.
         * @param userIds Required. A comma-separated list of Spotify User IDs ; the ids of the users that you want to
         *  check to see if they follow the playlist. Maximum: 5 ids.
         */
        suspend fun isFollowingPlaylist(playlistId: String, userIds: List<String>): List<Boolean> {
            return get(
                "playlists/$playlistId/followers/contains",
                mapOf("ids" to userIds.joinToString(separator = ","))
            )
        }

        /**
         * Get the current user’s followed artists.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-followed
         *
         * @param limit Optional. The maximum number of items to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param after Optional. The last artist ID retrieved from the previous request.
         */
        suspend fun getFollowedArtists(limit: Int? = null, after: String? = null): CursorPaging<FullArtist> {
            return get<ArtistsCursorPagingModel>(
                "me/following",
                mapOf("type" to "artist", "limit" to limit?.toString(), "after" to after)
            ).artists
        }

        /**
         * Add the current user as a follower of one or more artists or other Spotify users.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-follow-artists-users
         *
         * @param type Required. The ID type: either artist or user.
         * @param ids Optional. A comma-separated list of the artist or the user Spotify IDs. For example:
         *  ids=74ASZWbe4lXaubB36ztrGX,08td7MxkoHQkXnWAYD8d6Q. A maximum of 50 IDs can be sent in one request.
         */
        suspend fun follow(type: String, ids: List<String>) {
            return put(
                "me/following",
                jsonBody = mapOf<String, String>(),
                queryParams = mapOf("type" to type, "ids" to ids.joinToString(separator = ",")),
            )
        }

        /**
         * Add the current user as a follower of a playlist.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-follow-playlist
         *
         * @param playlistId The Spotify ID of the playlist. Any playlist can be followed, regardless of its
         *  public/private status, as long as you know its playlist ID.
         * @param public Optional. Defaults to true. If true the playlist will be included in user’s public playlists,
         *  if false it will remain private. To be able to follow playlists privately, the user must have granted the
         *  playlist-modify-private scope.
         */
        suspend fun followPlaylist(playlistId: String, public: Boolean = true) {
            return put("playlists/$playlistId/followers", jsonBody = mapOf("public" to public))
        }

        /**
         * Remove the current user as a follower of one or more artists or other Spotify users.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-unfollow-artists-users
         *
         * @param type Required. The ID type: either artist or user.
         * @param ids Optional. A comma-separated list of the artist or the user Spotify IDs. For example:
         *  ids=74ASZWbe4lXaubB36ztrGX,08td7MxkoHQkXnWAYD8d6Q. A maximum of 50 IDs can be sent in one request.
         */
        suspend fun unfollow(type: String, ids: List<String>) {
            return delete(
                "me/following",
                jsonBody = mapOf<String, String>(),
                queryParams = mapOf("type" to type, "ids" to ids.joinToString(separator = ","))
            )
        }

        /**
         * Remove the current user as a follower of a playlist.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-unfollow-playlist
         *
         * @param playlistId The Spotify ID of the playlist that is to be no longer followed.
         */
        suspend fun unfollowPlaylist(playlistId: String) {
            @Suppress("CastToNullableType")
            return delete(
                "playlists/$playlistId/followers",
                jsonBody = null as Map<String, String>?,
                queryParams = null
            )
        }
    }

    /**
     * Endpoints for retrieving information about, and managing, tracks that the current user has saved in their "Your
     * Music" library.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-library
     */
    object Library {
        /**
         * Get a list of the albums saved in the current Spotify user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-users-saved-albums
         *
         * @param limit The maximum number of objects to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset The index of the first object to return. Default: 0 (i.e., the first object). Use with limit to
         *  get the next set of objects.
         * @param market An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter if you want
         *  to apply Track Relinking.
         */
        suspend fun getSavedAlbums(
            limit: Int? = null,
            offset: Int? = null,
            market: String? = null
        ): Paging<SavedAlbum> {
            return get(
                "me/albums",
                mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "market" to market)
            )
        }

        /**
         * Save one or more albums to the current user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-save-albums-user
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun saveAlbums(ids: List<String>) {
            @Suppress("CastToNullableType")
            return put(
                "me/albums",
                queryParams = mapOf("ids" to ids.joinToString(separator = ",")),
                jsonBody = null as Unit?
            )
        }

        /**
         * Remove one or more albums from the current user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-remove-albums-user
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun removeAlbums(ids: List<String>) {
            @Suppress("CastToNullableType")
            return delete(
                "me/albums",
                queryParams = mapOf("ids" to ids.joinToString(separator = ",")),
                jsonBody = null as Unit?
            )
        }

        /**
         * Check if one or more albums is already saved in the current Spotify user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-check-users-saved-albums
         *
         * @param ids A comma-separated list of the Spotify IDs for the albums. Maximum: 50 IDs.
         */
        suspend fun checkAlbums(ids: List<String>): List<Boolean> {
            return get("me/albums/contains", mapOf("ids" to ids.joinToString(separator = ",")))
        }

        /**
         * Get a list of the songs saved in the current Spotify user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-users-saved-tracks
         *
         * @param limit The maximum number of objects to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset The index of the first object to return. Default: 0 (i.e., the first object). Use with limit to
         *  get the next set of objects.
         * @param market An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter if you want
         *  to apply Track Relinking.
         */
        suspend fun getSavedTracks(
            limit: Int? = null,
            offset: Int? = null,
            market: String? = null
        ): Paging<SavedTrack> {
            return get(
                "me/tracks",
                mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "market" to market)
            )
        }

        /**
         * Save one or more tracks to the current user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-save-tracks-user
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun saveTracks(ids: List<String>) {
            @Suppress("CastToNullableType")
            return put(
                "me/tracks",
                queryParams = mapOf("ids" to ids.joinToString(separator = ",")),
                jsonBody = null as Unit?
            )
        }

        /**
         * Remove one or more tracks from the current user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-remove-tracks-user
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun removeTracks(ids: List<String>) {
            @Suppress("CastToNullableType")
            return delete(
                "me/tracks",
                queryParams = mapOf("ids" to ids.joinToString(separator = ",")),
                jsonBody = null as Unit?
            )
        }

        /**
         * Check if one or more tracks is already saved in the current Spotify user’s ‘Your Music’ library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-check-users-saved-tracks
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun checkTracks(ids: List<String>): List<Boolean> {
            return get("me/tracks/contains", mapOf("ids" to ids.joinToString(separator = ",")))
        }

        /**
         * Get a list of shows saved in the current Spotify user’s library. Optional parameters can be used to limit the
         * number of shows returned.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-users-saved-shows
         *
         * @param limit The maximum number of shows to return. Default: 20. Minimum: 1. Maximum: 50
         * @param offset The index of the first show to return. Default: 0 (the first object). Use with limit to get the
         *  next set of shows.
         */
        suspend fun getSavedShows(limit: Int? = null, offset: Int? = null): Paging<SavedShow> {
            return get("me/shows", mapOf("limit" to limit?.toString(), "offset" to offset?.toString()))
        }

        /**
         * Save one or more shows to current Spotify user’s library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-save-shows-user
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun saveShows(ids: List<String>) {
            @Suppress("CastToNullableType")
            return put(
                "me/shows",
                queryParams = mapOf("ids" to ids.joinToString(separator = ",")),
                jsonBody = null as Unit?
            )
        }

        /**
         * Delete one or more shows from current Spotify user’s library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-remove-shows-user
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun removeShows(ids: List<String>) {
            @Suppress("CastToNullableType")
            return delete(
                "me/shows",
                queryParams = mapOf("ids" to ids.joinToString(separator = ",")),
                jsonBody = null as Unit?
            )
        }

        /**
         * Check if one or more shows is already saved in the current Spotify user’s library.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-check-users-saved-shows
         *
         * @param ids A comma-separated list of the Spotify IDs. Maximum: 50 IDs.
         */
        suspend fun checkShows(ids: List<String>): List<Boolean> {
            return get("me/shows/contains", mapOf("ids" to ids.joinToString(separator = ",")))
        }
    }

    /**
     * Endpoints for retrieving information about the user's listening habits.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-personalization
     */
    object Personalization {
        enum class TimeRange(val value: String) {
            SHORT_TERM("short_term"),
            MEDIUM_TERM("medium_term"),
            LONG_TERM("long_term")
        }

        /**
         * Get the current user’s top artists or tracks based on calculated affinity.
         *
         * Affinity is a measure of the expected preference a user has for a particular track or artist. It is based on
         * user behavior, including play history, but does not include actions made while in incognito mode. Light or
         * infrequent users of Spotify may not have sufficient play history to generate a full affinity data set. As a
         * user's behavior is likely to shift over time, this preference data is available over three time spans. See
         * time_range in the query parameter table for more information. For each time range, the top 50 tracks and
         * artists are available for each user. In the future, it is likely that this restriction will be relaxed.
         * This data is typically updated once each day for each user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-users-top-artists-and-tracks
         *
         * @Param limit Optional. The number of entities to return. Default: 20. Minimum: 1. Maximum: 50. For example:
         *  limit=2
         * @param offset Optional. The index of the first entity to return. Default: 0 (i.e., the first track). Use with
         *  limit to get the next set of entities.
         * @param timeRange Optional. Over what time frame the affinities are computed. Valid values: long_term
         *  (calculated from several years of data and including all new data as it becomes available), medium_term
         *  (approximately last 6 months), short_term (approximately last 4 weeks). Default: medium_term.
         */
        suspend fun topArtists(
            limit: Int? = null,
            offset: Int? = null,
            timeRange: TimeRange? = null
        ): Paging<FullArtist> {
            return get(
                "me/top/artists",
                mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "time_range" to timeRange?.value)
            )
        }

        /**
         * Get the current user’s top artists or tracks based on calculated affinity.
         *
         * Affinity is a measure of the expected preference a user has for a particular track or artist. It is based on
         * user behavior, including play history, but does not include actions made while in incognito mode. Light or
         * infrequent users of Spotify may not have sufficient play history to generate a full affinity data set. As a
         * user's behavior is likely to shift over time, this preference data is available over three time spans. See
         * time_range in the query parameter table for more information. For each time range, the top 50 tracks and
         * artists are available for each user. In the future, it is likely that this restriction will be relaxed.
         * This data is typically updated once each day for each user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-users-top-artists-and-tracks
         *
         * @Param limit Optional. The number of entities to return. Default: 20. Minimum: 1. Maximum: 50. For example:
         *  limit=2
         * @param offset Optional. The index of the first entity to return. Default: 0 (i.e., the first track). Use with
         *  limit to get the next set of entities.
         * @param timeRange Optional. Over what time frame the affinities are computed. Valid values: long_term
         *  (calculated from several years of data and including all new data as it becomes available), medium_term
         *  (approximately last 6 months), short_term (approximately last 4 weeks). Default: medium_term.
         */
        suspend fun topTracks(
            limit: Int? = null,
            offset: Int? = null,
            timeRange: TimeRange? = null
        ): Paging<FullTrack> {
            return get(
                "me/top/tracks",
                mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "time_range" to timeRange?.value)
            )
        }
    }

    /**
     * These endpoints are in beta. While we encourage you to build with them, a situation may arise where we need to
     * disable some or all of the functionality and/or change how they work without prior notice. Please report any
     * issues via our developer community forum.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-player
     */
    object Player {
        /**
         * Get information about the user's current playback state, including track or episode, progress, and active
         * device.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-information-about-the-users-
         * current-playback
         *
         * @param market An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter if you want
         *  to apply Track Relinking.
         * @param additionalTypes A comma-separated list of item types that your client supports besides the default
         *  track type. Valid types are: track and episode. An unsupported type in the response is expected to be
         *  represented as null value in the item field. Note: This parameter was introduced to allow existing clients
         *  to maintain their current behaviour and might be deprecated in the future. In addition to providing this
         *  parameter, make sure that your client properly handles cases of new
         */
        suspend fun getCurrentPlayback(market: String? = null, additionalTypes: List<String>? = null): Playback? {
            return get(
                "me/player",
                mapOf("market" to market, "additional_types" to additionalTypes?.joinToString(separator = ","))
            )
        }

        /**
         * Transfer playback to a new device and determine if it should start playing.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-transfer-a-users-playback
         *
         * @param deviceIds A JSON array containing the ID of the device on which playback should be
         *  started/transferred. For example:{device_ids:["74ASZWbe4lXaubB36ztrGX"]}
         *  Note: Although an array is accepted, only a single device_id is currently supported. Supplying more than one
         *  will return 400 Bad Request
         * @param play true: ensure playback happens on new device. false or not provided: keep the current playback
         *  state.
         */
        suspend fun transferPlayback(deviceIds: List<String>, play: String? = null) {
            @Serializable
            data class Body(
                @SerialName("device_ids") val deviceIds: List<String>,
                val play: String? = null
            )

            return put("me/player", jsonBody = Body(deviceIds = deviceIds, play = play))
        }

        /**
         * Get information about a user’s available devices.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-a-users-available-devices
         */
        suspend fun getAvailableDevices(): List<PlaybackDevice> {
            @Serializable
            data class Response(val devices: List<PlaybackDevice>)

            return get<Response>("me/player/devices").devices
        }

        /**
         * Get the object currently being played on the user’s Spotify account.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-the-users-currently-playing-track
         *
         * @param market An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter if you want
         *  to apply Track Relinking.
         * @param additionalTypes A comma-separated list of item types that your client supports besides the default
         *  track type. Valid types are: track and episode. An unsupported type in the response is expected to be
         *  represented as null value in the item field. Note: This parameter was introduced to allow existing clients
         *  to maintain their current behaviour and might be deprecated in the future. In addition to providing this
         *  parameter, make sure that your client properly handles cases of new types in the future by checking against
         *  the currently_playing_type field.
         */
        suspend fun getCurrentlyPlayingTrack(
            market: String? = null,
            additionalTypes: List<String>? = null
        ): TrackPlayback? {
            return get(
                "me/player/currently-playing",
                mapOf("market" to market, "additional_types" to additionalTypes?.joinToString(separator = ","))
            )
        }

        /**
         * Start a new context or resume current playback on the user’s active device.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-start-a-users-playback
         *
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         * @param contextUri string
         * @param uris Array of URIs
         * @param offset object
         * @param positionMs integer
         */
        suspend fun startPlayback(
            deviceId: String? = null,
            contextUri: String? = null,
            uris: List<String>? = null,
            offset: Any? = null,
            positionMs: Int? = null
        ) {
            @Serializable
            data class Body(
                @SerialName("context_uri") val contextUri: String? = null,
                @SerialName("uris") val uris: List<String>? = null,
                @Contextual
                @SerialName("offset")
                val offset: Any? = null,
                @SerialName("position_ms") val positionMs: Int? = null
            )

            return put(
                "me/player/play",
                jsonBody = Body(contextUri = contextUri, uris = uris, offset = offset, positionMs = positionMs),
                queryParams = mapOf("device_id" to deviceId)
            )
        }

        /**
         * Pause playback on the user’s account.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-pause-a-users-playback
         *
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun pausePlayback(deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return put(
                "me/player/pause",
                jsonBody = null as Unit?,
                queryParams = mapOf("device_id" to deviceId)
            )
        }

        /**
         * Skips to next track in the user’s queue.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-skip-users-playback-to-next-track
         *
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun skipToNext(deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return post(
                "me/player/next",
                jsonBody = null as Unit?,
                queryParams = mapOf("device_id" to deviceId)
            )
        }

        /**
         * Skips to previous track in the user’s queue.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-skip-users-playback-to-previous-track
         *
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun skipToPrevious(deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return post(
                "me/player/previous",
                jsonBody = null as Unit?,
                queryParams = mapOf("device_id" to deviceId)
            )
        }

        /**
         * Seeks to the given position in the user’s currently playing track.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-seek-to-position-in-currently-
         * playing-track
         *
         * @param positionMs The position in milliseconds to seek to. Must be a positive number. Passing in a position
         *  that is greater than the length of the track will cause the player to start playing the next song.
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun seekToPosition(positionMs: Int, deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return put(
                "me/player/seek",
                jsonBody = null as Unit?,
                queryParams = mapOf("position_ms" to positionMs.toString(), "device_id" to deviceId)
            )
        }

        /**
         * Set the repeat mode for the user’s playback. Options are repeat-track, repeat-context, and off.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-set-repeat-mode-on-users-playback
         *
         * @param state track, context or off.
         *  track will repeat the current track.
         *  context will repeat the current context.
         *  off will turn repeat off.
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun setRepeatMode(state: String, deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return put(
                "me/player/repeat",
                jsonBody = null as Unit?,
                queryParams = mapOf("state" to state, "device_id" to deviceId)
            )
        }

        /**
         * Set the volume for the user’s current playback device.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-set-volume-for-users-playback
         *
         * @param volumePercent The volume to set. Must be a value from 0 to 100 inclusive.
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun setVolume(volumePercent: Int, deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return put(
                "me/player/volume",
                jsonBody = null as Unit?,
                queryParams = mapOf("volume_percent" to volumePercent.toString(), "device_id" to deviceId)
            )
        }

        /**
         * Toggle shuffle on or off for user’s playback.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-toggle-shuffle-for-users-playback
         *
         * @param state true : Shuffle user’s playback. false : Do not shuffle user’s playback.
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun toggleShuffle(state: Boolean, deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return put(
                "me/player/shuffle",
                jsonBody = null as Unit?,
                queryParams = mapOf("state" to state.toString(), "device_id" to deviceId)
            )
        }

        /**
         * Get tracks from the current user’s recently played tracks. Note: Currently doesn't support podcast episodes.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-recently-played
         *
         * @param limit The maximum number of items to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param after A Unix timestamp in milliseconds. Returns all items after (but not including) this cursor
         *  position. If after is specified, before must not be specified.
         * @param before A Unix timestamp in milliseconds. Returns all items before (but not including) this cursor
         *  position. If before is specified, after must not be specified.
         */
        suspend fun getRecentlyPlayedTracks(
            limit: Int? = null,
            after: Long? = null,
            before: Long? = null
        ): CursorPaging<PlayHistoryObject> {
            return get(
                "me/player/recently-played",
                queryParams = mapOf(
                    "limit" to limit?.toString(),
                    "after" to after?.toString(),
                    "before" to before?.toString()
                )
            )
        }

        /**
         * Add an item to the end of the user’s current playback queue.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-add-to-queue
         *
         * @param uri The uri of the item to add to the queue. Must be a track or an episode uri.
         * @param deviceId The id of the device this command is targeting. If not supplied, the user’s currently active
         *  device is the target.
         */
        suspend fun addItemToQueue(uri: String, deviceId: String? = null) {
            @Suppress("CastToNullableType")
            return post(
                "me/player/queue",
                jsonBody = null as Unit?,
                queryParams = mapOf("uri" to uri, "device_id" to deviceId)
            )
        }
    }

    /**
     * Endpoints for retrieving information about a user’s playlists and for managing a user’s playlists.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-playlists
     */
    object Playlists {
        /**
         * Get a list of the playlists owned or followed by the current Spotify user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-a-list-of-current-users-playlists
         *
         * @param limit Optional. The maximum number of playlists to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first playlist to return. Default: 0 (the first object). Maximum
         *  offset: 100.000. Use with limit to get the next set of playlists.
         */
        suspend fun getPlaylists(limit: Int? = null, offset: Int? = null): Paging<SimplifiedPlaylist> {
            return get("me/playlists", mapOf("limit" to limit?.toString(), "offset" to offset?.toString()))
        }

        /**
         * Get a list of the playlists owned or followed by a Spotify user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-list-users-playlists
         *
         * @param limit Optional. The maximum number of playlists to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first playlist to return. Default: 0 (the first object). Maximum
         *  offset: 100.000. Use with limit to get the next set of playlists.
         */
        suspend fun getPlaylists(userId: String, limit: Int? = null, offset: Int? = null): Paging<SimplifiedPlaylist> {
            return get("users/$userId/playlists", mapOf("limit" to limit?.toString(), "offset" to offset?.toString()))
        }

        /**
         * Create a playlist for a Spotify user. (The playlist will be empty until you add tracks.)
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-create-playlist
         *
         * @param userId The user's Spotify user ID.
         * @param name The name for the new playlist, for example "Your Coolest Playlist" . This name does not need to
         *  be unique; a user may have several playlists with the same name.
         * @param public Defaults to true. If true the playlist will be public, if false it will be private. To be able
         *  to create private playlists, the user must have granted the playlist-modify-private scope.
         * @param collaborative Defaults to false. If true the playlist will be collaborative. Note that to create a
         *  collaborative playlist you must also set public to false . To create collaborative playlists you must have
         *  granted playlist-modify-private and playlist-modify-public scopes.
         * @param description value for playlist description as displayed in Spotify Clients and in the Web API.
         */
        suspend fun createPlaylist(
            userId: String,
            name: String,
            public: Boolean? = null,
            collaborative: Boolean? = null,
            description: String? = null
        ): FullPlaylist {
            return post(
                "users/$userId/playlists",
                jsonBody = mapOf(
                    "name" to name,
                    "public" to public?.toString(),
                    "collaborative" to collaborative?.toString(),
                    "description" to description
                )
            )
        }

        /**
         * Change a playlist’s name and public/private state. (The user must, of course, own the playlist.)
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-change-playlist-details
         *
         * @param playlistId The Spotify ID for the playlist.
         * @param name The new name for the playlist, for example "My New Playlist Title"
         * @param public If true the playlist will be public, if false it will be private.
         * @param collaborative If true, the playlist will become collaborative and other users will be able to modify
         *  the playlist in their Spotify client. Note: You can only set collaborative to true on non-public playlists.
         * @param description Value for playlist description as displayed in Spotify Clients and in the Web API.
         */
        suspend fun changePlaylistDetails(
            playlistId: String,
            name: String? = null,
            public: Boolean? = null,
            collaborative: Boolean? = null,
            description: String? = null
        ) {
            @Serializable
            data class Body(
                val name: String? = null,
                val public: Boolean? = null,
                val collaborative: Boolean? = null,
                val description: String? = null
            )

            return put(
                "playlists/$playlistId",
                jsonBody = Body(
                    name = name,
                    public = public,
                    collaborative = collaborative,
                    description = description
                )
            )
        }

        /**
         * Add one or more items to a user’s playlist.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-add-tracks-to-playlist
         *
         * @param playlistId The Spotify ID for the playlist.
         * @param position The position to insert the items, a zero-based index. For example, to insert the items in the
         *  first position: position=0; to insert the items in the third position: position=2 . If omitted, the items
         *  will be appended to the playlist. Items are added in the order they are listed in the query string or
         *  request body.
         * @param uris A JSON array of the Spotify URIs to add. A maximum of 100 items can be added in one request.
         */
        suspend fun addItemsToPlaylist(
            playlistId: String,
            position: Int? = null,
            uris: List<String>
        ): String {
            @Serializable
            data class Body(val position: Int? = null, val uris: List<String>)

            return post<Body, SnaphshotId>(
                "playlists/$playlistId/tracks",
                jsonBody = Body(position = position, uris = uris)
            ).snapshotId
        }

        /**
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-reorder-or-replace-playlists-tracks
         *
         * @param playlistId The Spotify ID for the playlist.
         * @param rangeStart The position of the first item to be reordered.
         * @param insertBefore The position where the items should be inserted. To reorder the items to the end of the
         *  playlist, simply set insert_before to the position after the last item. Examples: To reorder the first item
         *  to the last position in a playlist with 10 items, set range_start to 0, and insert_before to 10. To reorder
         *  the last item in a playlist with 10 items to the start of the playlist, set range_start to 9, and
         *  insert_before to 0.
         * @param rangeLength The amount of items to be reordered. Defaults to 1 if not set. The range of items to be
         *  reordered begins from the range_start position, and includes the range_length subsequent items. Example: To
         *  move the items at index 9-10 to the start of the playlist, range_start is set to 9, and range_length is set
         *  to 2.
         * @param snapshotId The playlist’s snapshot ID against which you want to make the changes.
         */
        suspend fun reorderPlaylistItems(
            playlistId: String,
            rangeStart: Int? = null,
            insertBefore: Int? = null,
            rangeLength: Int? = null,
            snapshotId: String? = null
        ): String {
            @Serializable
            data class Body(
                @SerialName("range_start") val rangeStart: Int? = null,
                @SerialName("insert_before") val insertBefore: Int? = null,
                @SerialName("range_length") val rangeLength: Int? = null,
                @SerialName("snapshot_id") val snapshotId: String? = null
            )

            return put<Body, SnaphshotId>(
                "playlists/$playlistId/tracks",
                jsonBody = Body(
                    rangeStart = rangeStart,
                    insertBefore = insertBefore,
                    rangeLength = rangeLength,
                    snapshotId = snapshotId
                )
            ).snapshotId
        }

        /**
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-reorder-or-replace-playlists-tracks
         *
         * @param playlistId The Spotify ID for the playlist.
         * @param uris A comma-separated list of Spotify URIs to set, can be track or episode URIs. A maximum of 100
         *  items can be set in one request.
         */
        suspend fun replacePlaylistItems(playlistId: String, uris: List<String>): String {
            @Serializable
            data class Body(val uris: List<String>)

            return put<Body, SnaphshotId>(
                "playlists/$playlistId/tracks",
                jsonBody = Body(uris = uris)
            ).snapshotId
        }

        /**
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-remove-tracks-playlist
         *
         * @param playlistId The Spotify ID
         * @param tracks An array of objects containing Spotify URIs of the tracks or episodes to remove. A maximum of
         *  100 objects can be sent at once.
         * @param snapshotId The playlist’s snapshot ID against which you want to make the changes. The API will
         *  validate that the specified items exist and in the specified positions and make the changes, even if more
         *  recent changes have been made to the playlist.
         */
        suspend fun removePlaylistTracks(playlistId: String, tracks: List<String>, snapshotId: String? = null): String {
            @Serializable
            data class Body(val uris: List<String>, val snapshotId: String? = null)

            return delete<Body, SnaphshotId>(
                "playlists/$playlistId/tracks",
                jsonBody = Body(uris = tracks, snapshotId = snapshotId)
            ).snapshotId
        }

        /**
         * Get the current image associated with a specific playlist.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-playlist-cover
         *
         * @param playlistId The Spotify ID for the playlist.
         */
        suspend fun getPlaylistCoverImages(playlistId: String): List<Image> {
            return get("playlists/$playlistId/images")
        }

        /**
         * Replace the image used to represent a specific playlist.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-upload-custom-playlist-cover
         *
         * @param playlistId The Spotify ID for the playlist.
         * @param jpegImage The request should contain a Base64 encoded JPEG image data, maximum payload size is 256 KB.
         */
        suspend fun uploadPlaylistCoverImage(playlistId: String, jpegImage: ByteArray) {
            return request(
                method = "PUT",
                path = "playlists/$playlistId/images",
                body = Base64.getEncoder().encodeToString(jpegImage)
                    .toRequestBody(contentType = "image/jpeg".toMediaType())
            )
        }

        /**
         * Get a playlist owned by a Spotify user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-playlist
         *
         * @param playlistId The Spotify ID for the playlist.
         * @param fields Optional. Filters for the query: a comma-separated list of the fields to return. If omitted,
         *  all fields are returned. For example, to get just the playlist’s description and URI:
         *  fields=description,uri. A dot separator can be used to specify non-reoccurring fields, while parentheses can
         *  be used to specify reoccurring fields within objects. For example, to get just the added date and user ID of
         *  the adder: fields=tracks.items(added_at,added_by.id). Use multiple parentheses to drill down into nested
         *  objects, for example: fields=tracks.items(track(name,href,album(name,href))). Fields can be excluded by
         *  prefixing them with an exclamation mark, for example:
         *  fields=tracks.items(track(name,href,album(!name,href)))
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking. For episodes, if a valid user access token is specified in the request
         *  header, the country associated with the user account will take priority over this parameter.
         *  Note: If neither market or user country are provided, the episode is considered unavailable for the client.
         * @param additionalTypes Optional. A comma-separated list of item types that your client supports besides the
         *  default track type. Valid types are: track and episode. Note: This parameter was introduced to allow
         *  existing clients to maintain their current behaviour and might be deprecated in the future. In addition to
         *  providing this parameter, make sure that your client properly handles cases of new types in the future by
         *  checking against the type field of each object.
         */
        suspend fun getPlaylist(
            playlistId: String,
            fields: List<String>? = null,
            market: String? = null,
            additionalTypes: List<String>? = null
        ): FullPlaylist {
            return get(
                "playlists/$playlistId",
                mapOf(
                    "fields" to fields?.joinToString(separator = ","),
                    "market" to market,
                    "additional_types" to additionalTypes?.joinToString(separator = ",")
                )
            )
        }

        /**
         * Get full details of the tracks or episodes of a playlist owned by a Spotify user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-playlists-tracks
         *
         * @param fields Optional. Filters for the query: a comma-separated list of the fields to return. If omitted,
         *  all fields are returned. For example, to get just the total number of tracks and the request limit:
         *  fields=total,limit
         *  A dot separator can be used to specify non-reoccurring fields, while parentheses can be used to specify
         *  reoccurring fields within objects. For example, to get just the added date and user ID of the adder:
         *  fields=items(added_at,added_by.id)
         *  Use multiple parentheses to drill down into nested objects, for example:
         *  fields=items(track(name,href,album(name,href)))
         *  Fields can be excluded by prefixing them with an exclamation mark, for example:
         *  fields=items.track.album(!external_urls,images)
         * @param limit Optional. The maximum number of tracks to return. Default: 100. Minimum: 1. Maximum: 100.
         * @param offset Optional. The index of the first track to return. Default: 0 (the first object).
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking. For episodes, if a valid user access token is specified in the request
         *  header, the country associated with the user account will take priority over this parameter.
         *  _Note: If neither market or user country are provided, the episode is considered unavailable for the client.
         * @param additionalTypes Optional. A comma-separated list of item types that your client supports besides the
         *  default track type. Valid types are: track and episode. Note: This parameter was introduced to allow
         *  existing clients to maintain their current behaviour and might be deprecated in the future. In addition to
         *  providing this parameter, make sure that your client properly handles cases of new types in the future by
         *  checking against the type field of each object.
         */
        suspend fun getPlaylistTracks(
            playlistId: String,
            fields: List<String>? = null,
            limit: Int? = null,
            offset: Int? = null,
            market: String? = null,
            additionalTypes: List<String>? = null
        ): Paging<PlaylistTrack> {
            return get(
                "playlists/$playlistId/tracks",
                mapOf(
                    "fields" to fields?.joinToString(separator = ","),
                    "limit" to limit?.toString(),
                    "offset" to offset?.toString(),
                    "market" to market,
                    "additional_types" to additionalTypes?.joinToString(separator = ",")
                )
            )
        }
    }

    /**
     * Get Spotify Catalog information about albums, artists, playlists, tracks, shows or episodes that match a keyword
     * string.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-search
     */
    object Search {
        @Serializable
        data class SearchResults(
            val albums: Paging<SimplifiedAlbum>? = null,
            val artists: Paging<FullArtist>? = null,
            val tracks: Paging<FullTrack>? = null,
            val playlists: Paging<SimplifiedPlaylist>? = null,
            val shows: Paging<SimplifiedShow>? = null,
            val episodes: Paging<SimplifiedEpisode>? = null
        )

        /**
         * Get Spotify Catalog information about albums, artists, playlists, tracks, shows or episodes that match a
         * keyword string.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-search
         *
         * @param q Required. Search query keywords and optional field filters and operators. For example:
         *  q=roadhouse%20blues.
         * @param type Required. A comma-separated list of item types to search across. Valid types are: album, artist,
         *  playlist, track, show and episode. Search results include hits from all the specified item types. For
         *  example: q=name:abacab&type=album,track returns both albums and tracks with “abacab” included in their name.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. If a country code is
         *  specified, only artists, albums, and tracks with content that is playable in that market is returned. Note:
         *  - Playlist results are not affected by the market parameter.
         *  - If market is set to from_token, and a valid access token is specified in the request header, only content
         *    playable in the country associated with the user account, is returned.
         *  - Users can view the country that is associated with their account in the account settings. A user must
         *    grant access to the user-read-private scope prior to when the access token is issued.
         * @param limit Optional. Maximum number of results to return. Default: 20 Minimum: 1 Maximum: 50 Note: The
         *  limit is applied within each type, not on the total response. For example, if the limit value is 3 and the
         *  type is artist,album, the response contains 3 artists and 3 albums.
         * @param offset Optional. The index of the first result to return. Default: 0 (the first result). Maximum
         *  offset (including limit): 2,000. Use with limit to get the next page of search results.
         * @param includeExternal Optional. Possible values: audio If include_external=audio is specified the response
         *  will include any relevant audio content that is hosted externally. By default external content is filtered
         *  out from responses.
         */
        suspend fun search(
            q: String,
            type: List<String>,
            market: String? = null,
            limit: Int? = null,
            offset: Int? = null,
            includeExternal: String? = null
        ): SearchResults {
            return get(
                "search",
                mapOf(
                    "q" to q,
                    "type" to type.joinToString(separator = ","),
                    "market" to market,
                    "limit" to limit?.toString(),
                    "offset" to offset?.toString(),
                    "include_external" to includeExternal
                )
            )
        }
    }

    /**
     * Endpoints for retrieving information about one or more shows from the Spotify catalog.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-shows
     */
    object Shows {
        /**
         * Get Spotify catalog information for a single show identified by its unique Spotify ID.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-a-show
         *
         * @param id The Spotify ID for the show.
         * @param market Optional. An ISO 3166-1 alpha-2 country code. If a country code is specified, only shows and
         *  episodes that are available in that market will be returned. If a valid user access token is specified in
         *  the request header, the country associated with the user account will take priority over this parameter.
         *  Note: If neither market or user country are provided, the content is considered unavailable for the client.
         *  Users can view the country that is associated with their account in the account settings.
         */
        suspend fun getShow(id: String, market: String? = null): FullShow {
            return get("shows/$id", mapOf("market" to market))
        }

        /**
         * Get Spotify catalog information for multiple shows based on their Spotify IDs.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-multiple-shows
         *
         * @param ids Required. A comma-separated list of the Spotify IDs for the shows. Maximum: 50 IDs.
         * @param market Optional. An ISO 3166-1 alpha-2 country code. If a country code is specified, only shows and
         *  episodes that are available in that market will be returned. If a valid user access token is specified in
         *  the request header, the country associated with the user account will take priority over this parameter.
         *  Note: If neither market or user country are provided, the content is considered unavailable for the client.
         *  Users can view the country that is associated with their account in the account settings.
         */
        suspend fun getShows(ids: List<String>, market: String? = null): List<SimplifiedShow> {
            return get<ShowsModel>(
                "shows",
                mapOf("ids" to ids.joinToString(separator = ","), "market" to market)
            ).shows
        }

        /**
         * Get Spotify catalog information about a show’s episodes. Optional parameters can be used to limit the number
         * of episodes returned.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-a-shows-episodes
         *
         * @param id The Spotify ID for the show.
         * @param limit Optional. The maximum number of episodes to return. Default: 20. Minimum: 1. Maximum: 50.
         * @param offset Optional. The index of the first episode to return. Default: 0 (the first object). Use with
         *  limit to get the next set of episodes.
         * @param market Optional. An ISO 3166-1 alpha-2 country code. If a country code is specified, only shows and
         *  episodes that are available in that market will be returned. If a valid user access token is specified in
         *  the request header, the country associated with the user account will take priority over this parameter.
         *  Note: If neither market or user country are provided, the content is considered unavailable for the client.
         *  Users can view the country that is associated with their account in the account settings.
         */
        suspend fun getShowEpisodes(
            id: String,
            limit: Int? = null,
            offset: Int? = null,
            market: String? = null
        ): Paging<SimplifiedEpisode> {
            return get(
                "shows/$id/episodes",
                mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "market" to market)
            )
        }
    }

    /**
     * Endpoints for retrieving information about one or more tracks from the Spotify catalog.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-tracks
     */
    object Tracks {
        /**
         * Get a detailed audio analysis for a single track identified by its unique Spotify ID.
         *
         * The Audio Analysis endpoint provides low-level audio analysis for all of the tracks in the Spotify catalog.
         * The Audio Analysis describes the track’s structure and musical content, including rhythm, pitch, and timbre.
         * All information is precise to the audio sample.
         *
         * Many elements of analysis include confidence values, a floating-point number ranging from 0.0 to 1.0.
         * Confidence indicates the reliability of its corresponding attribute. Elements carrying a small confidence
         * value should be considered speculative. There may not be sufficient data in the audio to compute the
         * attribute with high certainty.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-audio-analysis
         *
         * @param id Required. The Spotify ID for the track.
         */
        suspend fun getAudioAnalysis(id: String): AudioAnalysis = get("audio-analysis/$id")

        /**
         * Get audio feature information for a single track identified by its unique Spotify ID.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-audio-features
         *
         * @param id Required. The Spotify ID for the track.
         */
        suspend fun getAudioFeatures(id: String): AudioFeatures = get("audio-features/$id")

        /**
         * Get audio features for multiple tracks based on their Spotify IDs.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-several-audio-features
         *
         * @param ids Required. A comma-separated list of the Spotify IDs for the tracks. Maximum: 100 IDs.
         */
        suspend fun getAudioFeatures(ids: List<String>): List<AudioFeatures> {
            return get<AudioFeaturesModel>(
                "audio-features",
                mapOf("ids" to ids.joinToString(separator = ","))
            ).audioFeatures
        }

        /**
         * Get Spotify catalog information for a single track identified by its unique Spotify ID.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-track
         *
         * @param id The Spotify ID for the track.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking.
         */
        suspend fun getTrack(id: String, market: String? = null): FullTrack {
            return get("tracks/$id", mapOf("market" to market))
        }

        /**
         * Get Spotify catalog information for multiple tracks based on their Spotify IDs.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-several-tracks
         *
         * @param ids Required. A comma-separated list of the Spotify IDs for the tracks. Maximum: 50 IDs.
         * @param market Optional. An ISO 3166-1 alpha-2 country code or the string from_token. Provide this parameter
         *  if you want to apply Track Relinking.
         */
        suspend fun getTracks(ids: List<String>, market: String? = null): List<FullTrack> {
            return get<TracksModel>(
                "tracks",
                mapOf("ids" to ids.joinToString(separator = ","), "market" to market)
            ).tracks
        }
    }

    /**
     * Endpoints for retrieving information about a user’s profile.
     *
     * https://developer.spotify.com/documentation/web-api/reference/#category-users-profile
     */
    object UsersProfile {
        /**
         * Get detailed profile information about the current user (including the current user’s username).
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-current-users-profile
         */
        suspend fun getCurrentUser(): PrivateUser {
            return get("me")
        }

        /**
         * Get public profile information about a Spotify user.
         *
         * https://developer.spotify.com/documentation/web-api/reference/#endpoint-get-users-profile
         *
         * @param userId The user’s Spotify user ID.
         */
        suspend fun getUser(userId: String): PublicUser {
            return get("users/$userId")
        }
    }
}