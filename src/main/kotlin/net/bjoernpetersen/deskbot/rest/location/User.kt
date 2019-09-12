package net.bjoernpetersen.deskbot.rest.location

import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.locations.put
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.impl.getValue
import net.bjoernpetersen.deskbot.rest.ConflictException
import net.bjoernpetersen.deskbot.rest.respondEmpty
import net.bjoernpetersen.deskbot.rest.user
import net.bjoernpetersen.deskbot.rest.model.PasswordChange
import net.bjoernpetersen.deskbot.rest.model.RegisterCredentials
import net.bjoernpetersen.musicbot.api.auth.DuplicateUserException
import net.bjoernpetersen.musicbot.api.auth.User
import net.bjoernpetersen.musicbot.api.auth.UserManager
import javax.inject.Inject

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/user")
class UserRequest

@KtorExperimentalLocationsAPI
@Location("/token")
class LoginRequest

private class UserAccess @Inject private constructor(
    private val userManager: UserManager
) {
    fun registerUser(credentials: RegisterCredentials): User {
        return try {
            userManager.createTemporaryUser(credentials.name, credentials.userId)
        } catch (e: DuplicateUserException) {
            throw ConflictException()
        }
    }

    fun changePassword(user: User, change: PasswordChange): User {
        return try {
            userManager.updateUser(user, change.newPassword)
        } catch (e: DuplicateUserException) {
            throw ConflictException()
        }
    }

    fun deleteUser(user: User) {
        userManager.deleteUser(user)
    }

    fun User.toToken(): String {
        return userManager.toToken(this)
    }
}

@KtorExperimentalLocationsAPI
fun Route.routeUser(injector: Injector) {
    val userAccess: UserAccess by injector
    userAccess.apply {
        post<UserRequest> {
            val credentials: RegisterCredentials = call.receive()
            call.respond(registerUser(credentials).toToken())
        }
        authenticate("Basic") {
            get<LoginRequest> {
                call.respond(call.user.toToken())
            }
        }
        authenticate {
            get<UserRequest> {
                call.respond(call.user)
            }

            put<UserRequest> {
                val change: PasswordChange = call.receive()
                call.respond(changePassword(call.user, change).toToken())
            }

            delete<UserRequest> {
                deleteUser(call.user)
                call.respondEmpty()
            }
        }
    }
}
