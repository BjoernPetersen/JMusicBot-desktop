package net.bjoernpetersen.deskbot.ktor

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.util.pipeline.PipelineContext
import net.bjoernpetersen.musicbot.spi.image.ImageData

suspend fun PipelineContext<Unit, ApplicationCall>.respondImage(image: ImageData?) {
    if (image == null) {
        call.respond(HttpStatusCode.NotFound)
    } else {
        call.respondBytes(image.data, ContentType.parse(image.type))
    }
}
