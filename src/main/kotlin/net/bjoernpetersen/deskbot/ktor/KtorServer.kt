package net.bjoernpetersen.deskbot.ktor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.basic
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.ktor.location.Version
import net.bjoernpetersen.deskbot.ktor.location.routeExit
import net.bjoernpetersen.deskbot.ktor.location.routePlayer
import net.bjoernpetersen.deskbot.ktor.location.routeUser
import net.bjoernpetersen.musicbot.ServerConstraints
import net.bjoernpetersen.musicbot.api.auth.UserManager
import net.bjoernpetersen.musicbot.api.image.ImageServerConstraints
import net.bjoernpetersen.musicbot.spi.image.ImageCache
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
class KtorServer @Inject private constructor(
    private val userManager: UserManager,
    private val imageCache: ImageCache,
    private val injector: Injector
) {
    private val logger = KotlinLogging.logger {}
    private val server: ApplicationEngine = embeddedServer(CIO, port = ServerConstraints.port) {
        install(StatusPages) {
            expectAuth()
            exception<StatusException> {
                call.respond(it.code, it.message ?: "")
            }
        }
        // TODO maybe install(DataConversion)
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }

        install(Authentication) {
            register(BearerAuthentication(userManager))
            basic("Basic") {
                realm = AUTH_REALM
                validate {
                    val user = userManager.getUser(it.name)
                    if (user.hasPassword(it.password)) {
                        UserPrincipal(user)
                    } else null
                }
            }
        }

        install(Locations)

        routing {
            get<Version> {
                call.respond(Version.versionInfo)
            }

            routePlayer(injector)
            routeUser(injector)
            routeExit()

            get("${ImageServerConstraints.LOCAL_PATH}/{providerId}/{songId}") {
                val providerId = call.parameters["providerId"]!!.decode()
                val songId = call.parameters["songId"]!!.decode()
                val image = imageCache.getLocal(providerId, songId)
                call.respondImage(image)
            }
            get("${ImageServerConstraints.REMOTE_PATH}/{url}") {
                val url = call.parameters["url"]!!.decode()
                val image = imageCache.getRemote(url)
                call.respondImage(image)
            }
        }
    }

    fun start() {
        server.start()
    }

    fun close() {
        server.stop(1L, 5L, TimeUnit.SECONDS)
    }

    private companion object {
        private val decoder = Base64.getDecoder()
        fun String.decode(): String {
            return String(decoder.decode(toByteArray()), Charsets.UTF_8)
        }
    }
}
