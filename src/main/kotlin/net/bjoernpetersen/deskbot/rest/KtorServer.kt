package net.bjoernpetersen.deskbot.rest

import com.fasterxml.jackson.annotation.JsonInclude
import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.basic
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
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
import net.bjoernpetersen.deskbot.rest.location.Version
import net.bjoernpetersen.deskbot.rest.location.routeExit
import net.bjoernpetersen.deskbot.rest.location.routePlayer
import net.bjoernpetersen.deskbot.rest.location.routeProvider
import net.bjoernpetersen.deskbot.rest.location.routeQueue
import net.bjoernpetersen.deskbot.rest.location.routeSuggester
import net.bjoernpetersen.deskbot.rest.location.routeUser
import net.bjoernpetersen.deskbot.rest.location.routeVolume
import net.bjoernpetersen.deskbot.rest.model.AuthExpectation
import net.bjoernpetersen.deskbot.rest.model.basicExpect
import net.bjoernpetersen.deskbot.rest.model.type
import net.bjoernpetersen.musicbot.ServerConstraints
import net.bjoernpetersen.musicbot.api.auth.UserManager
import net.bjoernpetersen.musicbot.api.auth.UserNotFoundException
import net.bjoernpetersen.musicbot.api.image.ImageServerConstraints
import net.bjoernpetersen.musicbot.spi.image.ImageCache
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@UseExperimental(KtorExperimentalAPI::class, KtorExperimentalLocationsAPI::class)
class KtorServer @Inject private constructor(
    private val userManager: UserManager,
    private val imageCache: ImageCache,
    private val injector: Injector
) {
    private val logger = KotlinLogging.logger {}
    private val server: ApplicationEngine = embeddedServer(CIO, port = ServerConstraints.port) {
        install(StatusPages) {
            expectAuth()
            exception<AuthExpectationException> {
                call.respond(HttpStatusCode.Unauthorized, it.authExpectation)
            }
            exception<StatusException> {
                call.respond(it.code, it.message ?: "")
            }
            exception<IllegalArgumentException> {
                logger.debug(it) { "IllegalArgumentException from pipeline" }
                call.respond(HttpStatusCode.BadRequest, it.message ?: "")
            }
            exception<NoSuchSongException> {
                call.respond(HttpStatusCode.NotFound, it.message ?: "Song not found.")
            }
        }
        install(DataConversion)
        install(ContentNegotiation) {
            jackson {
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        }

        install(Authentication) {
            register(BearerAuthentication(userManager))
            basic("Basic") {
                realm = AUTH_REALM
                validate {
                    try {
                        val user = userManager.getUser(it.name)
                        if (user.hasPassword(it.password)) {
                            UserPrincipal(user)
                        } else throw AuthExpectationException(
                            basicExpect(user.type)
                        )
                    } catch (e: UserNotFoundException) {
                        throw NotFoundException()
                    }
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
            routeProvider(injector)
            routeSuggester(injector)
            routeVolume(injector)
            routeQueue(injector)
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
        server.stop(GRACE_PERIOD, TIMEOUT, TimeUnit.SECONDS)
    }

    private companion object {
        const val GRACE_PERIOD = 1L
        const val TIMEOUT = 5L

        private val decoder = Base64.getDecoder()
        fun String.decode(): String {
            return String(decoder.decode(toByteArray()), Charsets.UTF_8)
        }
    }
}

private class AuthExpectationException(val authExpectation: AuthExpectation) : Exception()
