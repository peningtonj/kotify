package com.dominiczirbel.network

import com.github.kittinunf.fuel.core.awaitResponse
import com.github.kittinunf.fuel.gson.gsonDeserializer
import com.github.kittinunf.fuel.httpPost
import java.util.Base64
import java.util.concurrent.TimeUnit

data class AccessToken(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long
) {
    private val received: Long = System.currentTimeMillis()

    val isExpired
        get() = System.currentTimeMillis() > received + TimeUnit.SECONDS.toMillis(expiresIn)

    companion object {
        private val requestCache = RequestCache<Unit, AccessToken>(maxSize = 1)
        private val base64Encoder = Base64.getEncoder()

        fun getCached(): AccessToken? = requestCache.getCached(Unit)

        fun getCachedOrThrow(): AccessToken = requestCache.getCached(Unit) ?: throw NoAccessTokenError

        suspend fun get(clientId: String, clientSecret: String): AccessToken? {
            return requestCache.request(Unit) {
                val unencodedAuth = "$clientId:$clientSecret"
                val encodedAuth = base64Encoder.encodeToString(unencodedAuth.toByteArray())

                // TODO add custom error handling
                "https://accounts.spotify.com/api/token".httpPost()
                    .body("grant_type=client_credentials")
                    .header("Authorization", "Basic $encodedAuth")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .awaitResponse(gsonDeserializer(Spotify.gson))
            }
        }

        suspend fun getOrThrow(clientId: String, clientSecret: String): AccessToken {
            return get(clientId, clientSecret) ?: throw NoAccessTokenError
        }

        object NoAccessTokenError : Throwable()
    }
}
