package net.bjoernpetersen.deskbot.rest

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondBytes
import net.bjoernpetersen.musicbot.spi.image.ImageData

suspend fun ApplicationCall.respondImage(image: ImageData?) {
    if (image == null) {
        respond(HttpStatusCode.NotFound)
    } else {
        respondBytes(image.data, ContentType.parse(image.type))
    }
}

suspend fun ApplicationCall.respondEmpty() {
    respond(HttpStatusCode.NoContent, "")
}
