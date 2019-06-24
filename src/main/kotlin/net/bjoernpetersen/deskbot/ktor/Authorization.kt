package net.bjoernpetersen.deskbot.ktor

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.Principal
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import io.ktor.features.BadRequestException
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import net.bjoernpetersen.deskbot.rest.model.AuthExpectation
import net.bjoernpetersen.deskbot.rest.model.AuthType
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.auth.User

fun StatusPages.Configuration.expectAuth() {
    exception<AuthenticationException> {
        call.respond(HttpStatusCode.Unauthorized, it.message ?: "401 Unauthorized")
    }
    exception<AuthorizationException> {
        call.respond(HttpStatusCode.Forbidden, it.message ?: "403 Forbidden")
    }
}

class AuthenticationException(val authExpectation: AuthExpectation) :
    BadRequestException("Authentication expected: $authExpectation")

class AuthorizationException(val authExpectation: AuthExpectation) :
    BadRequestException("Authorization expected: $authExpectation")

val ApplicationCall.user: User
    get() {
        val principal: JWTPrincipal = principal() ?: throw IllegalStateException()

        authentication.principal(principal)
    }

private fun expectation(permission: Permission) =
    AuthExpectation(AuthType.Token, permissions = listOf(permission))

fun PipelineContext<Any, ApplicationCall>.require(permission: Permission) {
    val principal: JWTPrincipal = call.principal()
        ?: throw AuthenticationException(expectation(permission))


}

class UserPrincipal(val user: User) : Principal
