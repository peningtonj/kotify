package com.dzirbel.kotify.network.model

import com.dzirbel.kotify.cache.CacheableObject
import com.dzirbel.kotify.util.CaseInsensitiveEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("ComplexInterface")
interface Album : SpotifyObject {
    /** A link to the Web API endpoint providing full details of the album. */
    override val href: String?

    /** The Spotify ID for the album. */
    override val id: String?

    /** The name of the album. In case of an album takedown, the value may be an empty string. */
    override val name: String

    /** The object type: "album" */
    override val type: String

    /** The Spotify URI for the album. */
    override val uri: String?

    /** The type of the album: one of "album", "single", or "compilation". */
    val albumType: Type?

    /**
     * The artists of the album. Each artist object includes a link in href to more detailed information about the
     * artist.
     */
    val artists: List<SimplifiedArtist>

    /**
     * The markets in which the album is available: ISO 3166-1 alpha-2 country codes. Note that an album is considered
     * available in a market when at least 1 of its tracks is available in that market.
     */
    val availableMarkets: List<String>?

    /** Known external URLs for this album. */
    val externalUrls: Map<String, String>

    /** The cover art for the album in various sizes, widest first. */
    val images: List<Image>

    /**
     * The date the album was first released, for example 1981. Depending on the precision, it might be shown as
     * 1981-12 or 1981-12-15.
     */
    val releaseDate: String?

    /** The precision with which release_date value is known: year, month, or day. */
    val releaseDatePrecision: String?

    /**
     * Part of the response when Track Relinking is applied, the original track is not available in the given market,
     * and Spotify did not have any tracks to relink it with. The track response will still contain metadata for the
     * original track, and a restrictions object containing the reason why the track is not available:
     * "restrictions" : {"reason" : "market"}
     */
    val restrictions: Map<String, String>?

    /** Undocumented field. */
    val totalTracks: Int?

    @Serializable(with = Type.Serializer::class)
    enum class Type {
        ALBUM,
        SINGLE,
        APPEARS_ON,
        COMPILATION;

        object Serializer : CaseInsensitiveEnumSerializer<Type>(Type::class)
    }
}

/**
 * https://developer.spotify.com/documentation/web-api/reference/#object-simplifiedalbumobject
 */
@Serializable
data class SimplifiedAlbum(
    @SerialName("album_type") override val albumType: Album.Type? = null,
    override val artists: List<SimplifiedArtist>,
    @SerialName("available_markets") override val availableMarkets: List<String>? = null,
    @SerialName("external_urls") override val externalUrls: Map<String, String>,
    override val href: String? = null,
    override val id: String? = null,
    override val images: List<Image>,
    override val name: String,
    @SerialName("release_date") override val releaseDate: String? = null,
    @SerialName("release_date_precision") override val releaseDatePrecision: String? = null,
    override val restrictions: Map<String, String>? = null,
    @SerialName("total_tracks") override val totalTracks: Int? = null,
    override val type: String,
    override val uri: String? = null,

    /**
     * The field is present when getting an artist’s albums. Possible values are "album", "single", "compilation",
     * "appears_on". Compare to album_type this field represents relationship between the artist and the album.
     */
    @SerialName("album_group")
    val albumGroup: Album.Type? = null
) : Album {
    override val cacheableObjects: Collection<CacheableObject>
        get() = artists
}

/**
 * https://developer.spotify.com/documentation/web-api/reference/#object-albumobject
 */
@Serializable
data class FullAlbum(
    @SerialName("album_type") override val albumType: Album.Type? = null,
    override val artists: List<SimplifiedArtist>,
    @SerialName("available_markets") override val availableMarkets: List<String>? = null,
    @SerialName("external_urls") override val externalUrls: Map<String, String>,
    override val href: String,
    override val id: String,
    override val images: List<Image>,
    override val name: String,
    @SerialName("release_date") override val releaseDate: String,
    @SerialName("release_date_precision") override val releaseDatePrecision: String,
    override val restrictions: Map<String, String>? = null,
    @SerialName("total_tracks") override val totalTracks: Int? = null,
    override val type: String,
    override val uri: String,

    /** The copyright statements of the album. */
    val copyrights: List<Copyright>,

    /** Known external IDs for the album. */
    @SerialName("external_ids")
    val externalIds: ExternalId,

    /**
     * A list of the genres used to classify the album. For example: "Prog Rock", "Post-Grunge". (If not yet classified,
     * the array is empty.)
     * */
    val genres: List<String>,

    /** The label for the album. */
    val label: String,

    /**
     * The popularity of the album. The value will be between 0 and 100, with 100 being the most popular. The popularity
     * is calculated from the popularity of the album’s individual tracks.
     */
    val popularity: Int,

    /** The tracks of the album. */
    val tracks: Paging<SimplifiedTrack>
) : Album {
    override val cacheableObjects: Collection<CacheableObject>
        get() = artists.plus(tracks.items) // TODO doesn't cache tracks beyond the first page
}

/**
 * https://developer.spotify.com/documentation/web-api/reference/#object-savedalbumobject
 */
@Serializable
data class SavedAlbum(
    /**
     * The date and time the album was saved Timestamps are returned in ISO 8601 format as Coordinated Universal Time
     * (UTC) with a zero offset: YYYY-MM-DDTHH:MM:SSZ. If the time is imprecise (for example, the date/time of an album
     * release), an additional field indicates the precision; see for example, release_date in an album object.
     */
    @SerialName("added_at") val addedAt: String,

    /** Information about the album. */
    val album: FullAlbum
) : CacheableObject {
    override val id: String?
        get() = null

    override val cacheableObjects
        get() = setOf(album)
}