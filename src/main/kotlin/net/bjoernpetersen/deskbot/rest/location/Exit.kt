@file:Suppress("MatchingDeclarationName")

package net.bjoernpetersen.deskbot.rest.location

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
import net.bjoernpetersen.deskbot.rest.require
import net.bjoernpetersen.deskbot.rest.respondEmpty
import net.bjoernpetersen.musicbot.api.auth.Permission

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/exit")
class ExitRequest

private const val GRACE_PERIOD_MILLIS = 500L

@KtorExperimentalLocationsAPI
fun Route.routeExit() {
    authenticate {
        post<ExitRequest> {
            require(Permission.EXIT)
            call.respondEmpty()
            GlobalScope.launch(Dispatchers.Main) {
                delay(GRACE_PERIOD_MILLIS)
                logger.info { "Closing due to remote user request" }
                Platform.exit()
            }
        }
    }
}
