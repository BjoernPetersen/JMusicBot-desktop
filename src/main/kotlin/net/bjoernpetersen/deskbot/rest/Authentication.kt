package net.bjoernpetersen.deskbot.rest

import io.ktor.application.call
import io.ktor.auth.AuthenticationContext
import io.ktor.auth.AuthenticationFailedCause
import io.ktor.auth.AuthenticationPipeline
import io.ktor.auth.AuthenticationProvider
import io.ktor.auth.Principal
import io.ktor.auth.UnauthorizedResponse
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.rest.model.tokenExpect
import net.bjoernpetersen.musicbot.api.auth.InvalidTokenException
import net.bjoernpetersen.musicbot.api.auth.User
import net.bjoernpetersen.musicbot.api.auth.UserManager

private const val BEARER_KEY = "CustomBearer"
const val AUTH_REALM = "MusicBot"

class UserPrincipal(val user: User) : Principal

class BearerAuthentication(
    private val userManager: UserManager,
    name: String? = null
) : AuthenticationProvider(name) {
    private val logger = KotlinLogging.logger { }
    private val scheme = "Bearer"
    private val realm = "MusicBot"

    init {
        pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            val token = call.request.parseAuthorizationHeaderOrNull()
            if (token == null) {
                logger.warn { "Missing token" }
                context.bearerChallenge(AuthenticationFailedCause.NoCredentials, realm, scheme)
                return@intercept
            }

            val user = try {
                token.getBlob(scheme)?.let {
                    userManager.fromToken(it)
                }
            } catch (e: InvalidTokenException) {
                logger.debug(e) { "Invalid token" }
                null
            }

            if (user == null) {
                logger.warn { "Invalid token" }
                context.bearerChallenge(AuthenticationFailedCause.InvalidCredentials, realm, scheme)
                return@intercept
            }

            context.principal(UserPrincipal(user))
        }
    }
}

private fun HttpAuthHeader.getBlob(scheme: String) = when {
    this is HttpAuthHeader.Single && authScheme == scheme -> blob
    else -> null
}

private fun ApplicationRequest.parseAuthorizationHeaderOrNull() = try {
    parseAuthorizationHeader()
} catch (ex: IllegalArgumentException) {
    null
}

private fun AuthenticationContext.bearerChallenge(
    cause: AuthenticationFailedCause,
    realm: String,
    scheme: String
) = challenge(BEARER_KEY, cause) {
    val copied = UnauthorizedResponse(
        bearerAuthChallenge(
            realm,
            scheme
        )
    )
    // We want to send an AuthExpectation body so we can't send the above response directly
    call.response.headers.let { headers ->
        copied.headers.forEach { key, values ->
            if (!headers.contains(key)) values.forEach { value -> headers.append(key, value) }
        }
    }
    call.response.status(HttpStatusCode.Unauthorized)
    call.respond(tokenExpect(null))
    it.complete()
}

private fun bearerAuthChallenge(realm: String, scheme: String): HttpAuthHeader =
    HttpAuthHeader.Parameterized(scheme, mapOf(HttpAuthHeader.Parameters.Realm to realm))
