package com.dzirbel.kotify.network.oauth

import assertk.Assert
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import com.dzirbel.kotify.network.MockRequestInterceptor
import com.dzirbel.kotify.network.Spotify
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.concurrent.TimeUnit

internal class AccessTokenTest {
    @BeforeEach
    @Suppress("unused")
    fun beforeEach() {
        AccessToken.Cache.clear()
    }

    @Test
    fun testIsExpired() {
        fun assertIsExpired(receivedDeltaMs: Long, expiresInS: Long): Assert<Boolean> {
            return assertThat(
                AccessToken(
                    accessToken = "",
                    tokenType = "",
                    received = System.currentTimeMillis() + receivedDeltaMs,
                    expiresIn = expiresInS,
                ).isExpired,
            )
        }

        // received now, expires immediately -> expired
        assertIsExpired(receivedDeltaMs = 0, expiresInS = 0).isTrue()

        // received 100ms in the future, expires 1 seconds after received -> not expired
        assertIsExpired(receivedDeltaMs = 100, expiresInS = 1).isFalse()

        // received 100ms ago, expires 1 seconds after received -> not expired
        assertIsExpired(receivedDeltaMs = -100, expiresInS = 1).isFalse()

        // received 10s ago, expires 5 seconds after received -> expired
        assertIsExpired(receivedDeltaMs = -10_000, expiresInS = 5).isTrue()

        // received 10s ago, expires 15 seconds after received -> not expired
        assertIsExpired(receivedDeltaMs = -10_000, expiresInS = 15).isFalse()
    }

    @Test
    fun testScopes() {
        val token = AccessToken(
            accessToken = "",
            tokenType = "",
            expiresIn = 0,
            scope = "scope1 SCOPE2",
        )

        assertThat(token.hasScope("scope1")).isTrue()
        assertThat(token.hasScope("Scope1")).isTrue()
        assertThat(token.hasScope("SCOPE2")).isTrue()
        assertThat(token.hasScope("")).isFalse()
        assertThat(token.hasScope("abcv")).isFalse()
        assertThat(token.hasScope("scope3")).isFalse()
    }

    @Test
    fun testGetPutClear() {
        assertNoToken()

        val token1 = AccessToken(accessToken = "token1", tokenType = "", expiresIn = 0)
        AccessToken.Cache.put(token1)

        assertThat(AccessToken.Cache.tokenFlow.value).isNotNull()
        assertThat(runBlocking { AccessToken.Cache.get() }).isSameAs(token1)
        assertThat(runBlocking { AccessToken.Cache.getOrThrow() }).isSameAs(token1)

        val token2 = AccessToken(accessToken = "token2", tokenType = "", expiresIn = 0)
        AccessToken.Cache.put(token2)

        assertThat(AccessToken.Cache.tokenFlow.value).isNotNull()
        assertThat(runBlocking { AccessToken.Cache.get() }).isSameAs(token2)
        assertThat(runBlocking { AccessToken.Cache.getOrThrow() }).isSameAs(token2)

        AccessToken.Cache.clear()
        assertNoToken()
    }

    @RepeatedTest(5)
    fun testSaveLoad() {
        val token1 = AccessToken(accessToken = "token1", tokenType = "", expiresIn = 0)
        assertThat(token1.received).isGreaterThan(0)
        AccessToken.Cache.put(token1)

        AccessToken.Cache.reset()
        Thread.sleep(5)

        val loadedToken = runBlocking { AccessToken.Cache.get() }
        assertThat(loadedToken).isEqualTo(token1)
        assertThat(loadedToken).isNotSameAs(token1)
    }

    @Test
    fun testFromJsonNoReceived() {
        val before = System.currentTimeMillis()

        val accessToken = Json.decodeFromString<AccessToken>(
            """
                {
                    "access_token": "abc",
                    "token_type": "def",
                    "expires_in": 30
                }
            """.trimIndent(),
        )

        val after = System.currentTimeMillis()

        assertThat(accessToken.accessToken).isEqualTo("abc")
        assertThat(accessToken.tokenType).isEqualTo("def")
        assertThat(accessToken.expiresIn).isEqualTo(30)
        assertThat(accessToken.received).isBetween(before, after)
    }

    @Test
    fun testFromJsonWithReceived() {
        val accessToken = Json.decodeFromString<AccessToken>(
            """
                {
                    "access_token": "abc",
                    "token_type": "def",
                    "expires_in": 30,
                    "received": 123
                }
            """.trimIndent(),
        )

        assertThat(accessToken.accessToken).isEqualTo("abc")
        assertThat(accessToken.tokenType).isEqualTo("def")
        assertThat(accessToken.expiresIn).isEqualTo(30)
        assertThat(accessToken.received).isEqualTo(123)
    }

    @Test
    fun testRefresh() {
        val tokenBody = """
            {
                "access_token": "abc",
                "token_type": "def",
                "expires_in": 30
            }
        """.trimIndent()

        withSpotifyConfiguration(
            Spotify.configuration.copy(
                oauthOkHttpClient = MockRequestInterceptor(
                    responseBody = tokenBody.toResponseBody("text/plain".toMediaType()),
                ).client,
            ),
        ) {
            val expiredToken = AccessToken(
                accessToken = "",
                tokenType = "",
                expiresIn = 10,
                received = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(15),
                refreshToken = "refresh",
            )
            assertThat(expiredToken.isExpired).isTrue()
            AccessToken.Cache.put(expiredToken)

            val newToken = requireNotNull(runBlocking { AccessToken.Cache.get() })

            assertThat(newToken).isNotEqualTo(expiredToken)
            assertThat(newToken.isExpired).isFalse()
            assertThat(newToken.accessToken).isEqualTo("abc")
            assertThat(newToken.tokenType).isEqualTo("def")
            assertThat(newToken.expiresIn).isEqualTo(30)

            assertThat(runBlocking { AccessToken.Cache.get() }).isEqualTo(newToken)
        }
    }

    @Test
    fun testRefreshError() {
        withSpotifyConfiguration(
            Spotify.configuration.copy(
                oauthOkHttpClient = MockRequestInterceptor(
                    responseCode = 500,
                    responseMessage = "Internal server error",
                ).client,
            ),
        ) {
            val expiredToken = AccessToken(
                accessToken = "",
                tokenType = "",
                expiresIn = 10,
                received = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(15),
                refreshToken = "refresh",
            )
            assertThat(expiredToken.isExpired).isTrue()
            AccessToken.Cache.put(expiredToken)

            val newToken = runBlocking { AccessToken.Cache.get() }

            assertThat(newToken).isNull()
            assertNoToken()
        }
    }

    @RepeatedTest(3)
    fun testRefreshConcurrent() {
        val tokenBody = """
            {
                "access_token": "abc",
                "token_type": "def",
                "expires_in": 30
            }
        """.trimIndent()

        val interceptor = MockRequestInterceptor(
            responseBody = tokenBody.toResponseBody("text/plain".toMediaType()),
            delayMs = 100,
        )

        withSpotifyConfiguration(Spotify.configuration.copy(oauthOkHttpClient = interceptor.client)) {
            val expiredToken = AccessToken(
                accessToken = "",
                tokenType = "",
                expiresIn = 10,
                received = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(15),
                refreshToken = "refresh",
            )
            assertThat(expiredToken.isExpired).isTrue()
            AccessToken.Cache.put(expiredToken)

            runBlocking {
                val request1 = async { AccessToken.Cache.get() }
                val request2 = async {
                    delay(50)
                    AccessToken.Cache.get()
                }

                val token1 = request1.await()
                val token2 = request2.await()

                assertThat(token1).isSameAs(token2)
                assertThat(interceptor.requests).hasSize(1)
            }
        }
    }

    @Test
    fun testRequireRefreshable() {
        val notRefreshable = AccessToken(accessToken = "token", tokenType = "type", expiresIn = 10)
        assertThat(notRefreshable.refreshToken).isNull()

        AccessToken.Cache.put(notRefreshable)
        assertThat(AccessToken.Cache.tokenFlow.value).isNotNull()

        AccessToken.Cache.requireRefreshable()

        assertNoToken()

        val refreshable = AccessToken(
            accessToken = "token2",
            tokenType = "type",
            expiresIn = 10,
            refreshToken = "refresh",
        )
        assertThat(refreshable.refreshToken).isNotNull()

        AccessToken.Cache.put(refreshable)
        assertThat(AccessToken.Cache.tokenFlow.value).isNotNull()

        AccessToken.Cache.requireRefreshable()

        assertThat(AccessToken.Cache.tokenFlow.value).isNotNull()
    }

    private fun assertNoToken() {
        assertThat(AccessToken.Cache.tokenFlow.value).isNull()
        assertThat(runBlocking { AccessToken.Cache.get() }).isNull()
        assertThrows<AccessToken.Cache.NoAccessTokenError> { runBlocking { AccessToken.Cache.getOrThrow() } }
    }

    companion object {
        private val tempFile = File("temp_access_token.json")
        private var originalCacheFile: File? = null

        @BeforeAll
        @JvmStatic
        @Suppress("unused")
        fun before() {
            originalCacheFile = AccessToken.Cache.cacheFile
            AccessToken.Cache.cacheFile = tempFile
        }

        @AfterAll
        @JvmStatic
        @Suppress("unused")
        fun after() {
            AccessToken.Cache.cacheFile = originalCacheFile
            originalCacheFile = null
        }

        /**
         * Temporarily sets the [Spotify.configuration] to [configuration], runs [block], and then resets the
         * [Spotify.configuration] to its previous value.
         */
        private fun withSpotifyConfiguration(configuration: Spotify.Configuration, block: () -> Unit) {
            val oldConfig = Spotify.configuration
            Spotify.configuration = configuration

            try {
                block()
            } finally {
                Spotify.configuration = oldConfig
            }
        }
    }
}
