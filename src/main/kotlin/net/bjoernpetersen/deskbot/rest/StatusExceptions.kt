package net.bjoernpetersen.deskbot.rest

import io.ktor.http.HttpStatusCode

sealed class StatusException(
    val code: HttpStatusCode,
    val response: String? = null
) : Exception(response ?: code.toString())

class BadRequestException(message: String? = null) :
    StatusException(HttpStatusCode.BadRequest, message)

class NotFoundException : StatusException(HttpStatusCode.NotFound)
class ConflictException : StatusException(HttpStatusCode.Conflict)

class UnavailableException : StatusException(HttpStatusCode.ServiceUnavailable)
