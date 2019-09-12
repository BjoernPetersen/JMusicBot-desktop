package net.bjoernpetersen.deskbot.rest.model

import net.bjoernpetersen.musicbot.api.auth.GuestUser
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.auth.User

enum class AuthType {
    Token, Basic
}

enum class UserType {
    Guest, Full
}

val User.type: UserType
    get() = when (this) {
        is GuestUser -> UserType.Guest
        else -> UserType.Full
    }

/**
 * @param format The required authentication format.
 * @param type Basic Auth: If authorization failed, this describes the type of the user the login
 * attempt was for. If the user is a Full user, a password is expected.
 * Otherwise some unique identifier.
 * @param permissions Token auth: Required permissions for the endpoint.
 */
data class AuthExpectation(
    val format: AuthType,
    val type: UserType? = null,
    val permissions: List<Permission>? = null
)

fun tokenExpect(permissions: List<Permission>?): AuthExpectation =
    AuthExpectation(AuthType.Token, permissions = permissions)

fun basicExpect(type: UserType): AuthExpectation =
    AuthExpectation(AuthType.Token, type = type)

data class RegisterCredentials(val name: String, val userId: String)
data class PasswordChange(val newPassword: String)
