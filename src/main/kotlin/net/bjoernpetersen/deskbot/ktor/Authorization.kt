package net.bjoernpetersen.deskbot.ktor

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.principal
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.pipeline.PipelineContext
import net.bjoernpetersen.deskbot.rest.model.AuthExpectation
import net.bjoernpetersen.deskbot.rest.model.AuthType
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.auth.User

@KtorExperimentalAPI
fun StatusPages.Configuration.expectAuth() {
    exception<AuthorizationException> {
        call.respond(HttpStatusCode.Forbidden, it.authExpectation)
    }
}

private class AuthorizationException(val authExpectation: AuthExpectation) : Exception()

val ApplicationCall.user: User
    get() {
        val principal: UserPrincipal = principal() ?: throw IllegalStateException()
        return principal.user
    }

private fun expected(permission: Permission) =
    AuthExpectation(AuthType.Token, permissions = listOf(permission))

fun PipelineContext<Unit, ApplicationCall>.require(permission: Permission) {
    val user = call.user
    if (permission !in user.permissions) {
        throw AuthorizationException(expected(permission))
    }
}
