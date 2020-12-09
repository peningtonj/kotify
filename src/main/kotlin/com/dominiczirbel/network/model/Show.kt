package com.dominiczirbel.network.model

@Suppress("ComplexInterface")
interface Show : SpotifyObject {
    /** A link to the Web API endpoint providing full details of the show. */
    override val href: String

    /** The Spotify ID for the show. */
    override val id: String

    /** The name of the show. */
    override val name: String

    /** The object type: “show”. */
    override val type: String

    /** The Spotify URI for the show. */
    override val uri: String

    /** A list of the countries in which the show can be played, identified by their ISO 3166-1 alpha-2 code. */
    val availableMarkets: List<String>

    /** The copyright statements of the show. */
    val copyrights: List<Copyright>

    /** A description of the show. */
    val description: String

    /** Whether or not the show has explicit content (true = yes it does; false = no it does not OR unknown). */
    val explicit: Boolean

    /** Known external URLs for this show. */
    val externalUrls: ExternalUrls

    /** The cover art for the show in various sizes, widest first. */
    val images: List<Image>

    /**
     * True if all of the show’s episodes are hosted outside of Spotify’s CDN. This field might be null in some cases.
     * */
    val isExternallyHosted: Boolean?

    /** A list of the languages used in the show, identified by their ISO 639 code. */
    val languages: List<String>

    /** The media type of the show. */
    val mediaType: String

    /** The publisher of the show. */
    val publisher: String

    /** Undocumented field. */
    val totalEpisodes: Int?
}

/**
 * https://developer.spotify.com/documentation/web-api/reference/object-model/#show-object-simplified
 */
data class SimplifiedShow(
    override val availableMarkets: List<String>,
    override val copyrights: List<Copyright>,
    override val description: String,
    override val explicit: Boolean,
    override val externalUrls: ExternalUrls,
    override val href: String,
    override val id: String,
    override val images: List<Image>,
    override val isExternallyHosted: Boolean?,
    override val languages: List<String>,
    override val mediaType: String,
    override val name: String,
    override val publisher: String,
    override val totalEpisodes: Int,
    override val type: String,
    override val uri: String
) : Show

/**
 * https://developer.spotify.com/documentation/web-api/reference/object-model/#show-object-full
 */
data class FullShow(
    override val availableMarkets: List<String>,
    override val copyrights: List<Copyright>,
    override val description: String,
    override val explicit: Boolean,
    override val externalUrls: ExternalUrls,
    override val href: String,
    override val id: String,
    override val images: List<Image>,
    override val isExternallyHosted: Boolean?,
    override val languages: List<String>,
    override val mediaType: String,
    override val name: String,
    override val publisher: String,
    override val totalEpisodes: Int,
    override val type: String,
    override val uri: String,

    val episodes: Paging<SimplifiedEpisode>
) : Show
