package net.bjoernpetersen.deskbot.rest

import com.fasterxml.jackson.annotation.JsonInclude
import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.basic
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.cert.CertificateHandler
import net.bjoernpetersen.deskbot.rest.location.VersionConstraints
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
import net.bjoernpetersen.musicbot.spi.auth.TokenHandler
import net.bjoernpetersen.musicbot.spi.image.ImageCache
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.version.Version
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(KtorExperimentalAPI::class, KtorExperimentalLocationsAPI::class)
class KtorServer @Inject private constructor(
    private val userManager: UserManager,
    private val tokenHandler: TokenHandler,
    private val imageCache: ImageCache,
    private val injector: Injector,
    private val certificateHandler: CertificateHandler,
    private val version: Version
) {
    private val logger = KotlinLogging.logger {}
    private val env = applicationEngineEnvironment {
        val certificate = certificateHandler.certificate
        sslConnector(
            certificate.keystore,
            certificate.getAlias()!!,
            { certificate.passphrase.toCharArray() },
            { certificate.passphrase.toCharArray() }
        ) {
            host = "0.0.0.0"
            port = ServerConstraints.port + 1
        }

        connector {
            host = "0.0.0.0"
            port = ServerConstraints.port
        }

        this.module {
            install(CORS) {
                anyHost()
                allowCredentials = true
                allowNonSimpleContentTypes = true
                method(HttpMethod.Put)
                method(HttpMethod.Delete)
                header(HttpHeaders.Authorization)
            }
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
                register(BearerAuthentication(tokenHandler))
                register(RefreshTokenAuthentication(tokenHandler, "RefreshToken"))
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

                get(VersionConstraints.PATH) {
                    call.respond(version)
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
    }
    private val server: ApplicationEngine = embeddedServer(Netty, env)

    fun start() {
        server.start()
    }

    fun close() {
        server.stop(GRACE_PERIOD, TIMEOUT)
    }

    private companion object {
        const val GRACE_PERIOD = 1000L
        const val TIMEOUT = 5000L

        private val decoder = Base64.getDecoder()
        fun String.decode(): String {
            return String(decoder.decode(toByteArray()), Charsets.UTF_8)
        }
    }
}

private class AuthExpectationException(val authExpectation: AuthExpectation) : Exception()
