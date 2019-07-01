package net.bjoernpetersen.deskbot.ktor.location

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.post
import io.ktor.routing.Route
import javafx.application.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.ktor.require
import net.bjoernpetersen.deskbot.ktor.respondEmpty
import net.bjoernpetersen.musicbot.api.auth.Permission

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/exit")
private class ExitRequest

@KtorExperimentalLocationsAPI
fun Route.routeExit() {
    authenticate {
        post<ExitRequest> {
            require(Permission.EXIT)
            call.respondEmpty()
            GlobalScope.launch(Dispatchers.Main) {
                delay(500)
                logger.info { "Closing due to remote user request" }
                Platform.exit()
            }
        }
    }
}
