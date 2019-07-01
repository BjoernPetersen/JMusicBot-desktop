package net.bjoernpetersen.deskbot.ktor

import io.ktor.http.HttpStatusCode

sealed class StatusException(
    val code: HttpStatusCode,
    val response: String? = null
) : Exception(response ?: code.toString())

class NotFoundException : StatusException(HttpStatusCode.NotFound)
class ConflictException : StatusException(HttpStatusCode.Conflict)


class UnavailableException : StatusException(HttpStatusCode.ServiceUnavailable)
