package net.bjoernpetersen.deskbot.cert

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.fximpl.SwingBrowserOpener
import net.bjoernpetersen.deskbot.lifecycle.Lifecyclist
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetAddress
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.Base64

private const val PORT = 54321
private const val PASSPHRASE = "supersecretkey"
private const val DOMAIN = "test.instance.kiu"
private const val ALIAS = "certificate"
private val certPath = Paths.get("build/cert.jks")
private val handlerCertPath = Paths.get("build/handlerCert.jks")

class CertificateHandlerTest {
    companion object {
        lateinit var server: NettyApplicationEngine
        var requestTime: OffsetDateTime? = null
        val logger = KotlinLogging.logger { }
        val postRequests = mutableListOf<Pair<ApplicationCall, InitialRequest>>()
        val getRequests = mutableListOf<ApplicationCall>()

        @KtorExperimentalAPI
        @BeforeAll
        @JvmStatic
        fun setup() {

            if (!certPath.toFile().exists()) {
                generateCertificate(certPath.toFile(), keyPassword = PASSPHRASE, jksPassword = PASSPHRASE, keyAlias = ALIAS)
            }

            server = embeddedServer(Netty, port = PORT) {
                install(io.ktor.features.ContentNegotiation) {
                    jackson {
                    }
                }

                routing {
                    post("/") {
                        logger.info { "New post request" }
                        val value = call.receive<InitialRequest>()
                        postRequests.add(Pair(call, value))
                        logger.info { "Parameter: ips: ${value.ips}, format: ${value.keyFormat}" }
                        assert(value.keyFormat == "jks") { "keyFormat not jks" }
                        val domains =
                            value.ips.map { IpDomain(listOf(it), "${it.hashCode()}.$DOMAIN") }
                        requestTime = OffsetDateTime.now()
                        val response = InitialResponse("*.$DOMAIN", domains, PASSPHRASE, "jks")
                        logger.info { "Response: domains: ${response.domains}" }
                        call.respond(response)
                    }

                    get("/$PASSPHRASE") {
                        getRequests.add(call)
                        if (requestTime != null) {
                            if (requestTime!!.isBefore(OffsetDateTime.now().minusSeconds(20))) {
                                call.respond(CertificateResponse(false, null))
                            } else {
                                withContext(Dispatchers.IO) {
                                    call.respond(
                                        CertificateResponse(
                                            true,
                                            Jks(Base64.getEncoder().encodeToString(certPath.toFile().readBytes()))
                                        )
                                    )
                                }
                            }
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Invalid token")
                        }
                    }
                }
            }.apply {
                start(false)
            }
            runBlocking { delay(5000L) }
        }

        @AfterAll
        @JvmStatic
        fun removeCertificate() {
            if (certPath.toFile().exists()) {
                certPath.toFile().delete()
            }
        }
    }

    private fun cleanup(handler: CertificateHandler) {
        handler.domains.set(null)
        handler.key.set(null)
        if (handlerCertPath.toFile().exists()) {
            handlerCertPath.toFile().delete()
        }
        postRequests.clear()
        getRequests.clear()
        requestTime = null
    }

    @KtorExperimentalAPI
    @Test
    fun testFetch() {
        runBlocking {
            val cycle = Lifecyclist()
            cycle.create(File("plugins"))
            cycle.inject(SwingBrowserOpener())
            val certificateHandler = cycle.getInjector().getInstance(CertificateHandler::class.java)
            certificateHandler.acquireCertificate(handlerCertPath, "http://127.0.0.1:$PORT")

            Assertions.assertEquals(1, postRequests.size)
            Assertions.assertEquals(postRequests[0].second.ips.size, certificateHandler.domains.get()!!.size)
            Assertions.assertEquals(PASSPHRASE, certificateHandler.certificate.passphrase)
            Assertions.assertEquals(ALIAS, certificateHandler.certificate.getAlias())
            cleanup(certificateHandler)
        }
    }

    @KtorExperimentalAPI
    @Test
    fun testExisting() {
        runBlocking {
            val cycle = Lifecyclist()
            cycle.create(File("plugins"))
            cycle.inject(SwingBrowserOpener())
            val certificateHandler = cycle.getInjector().getInstance(CertificateHandler::class.java)
            certificateHandler.key.set(PASSPHRASE)

            certificateHandler.acquireCertificate(certPath, "http://127.0.0.1:$PORT")

            Assertions.assertEquals(0, postRequests.size)
            Assertions.assertEquals(PASSPHRASE, certificateHandler.certificate.passphrase)
            Assertions.assertEquals(ALIAS, certificateHandler.certificate.getAlias())
            cleanup(certificateHandler)
        }
    }

    @KtorExperimentalAPI
    @Test
    fun testMissingKey() {
        runBlocking {
            if (!handlerCertPath.toFile().exists()) {
                generateCertificate(handlerCertPath.toFile(), keyPassword = PASSPHRASE, jksPassword = PASSPHRASE, keyAlias = ALIAS)
            }
            val cycle = Lifecyclist()
            cycle.create(File("plugins"))
            cycle.inject(SwingBrowserOpener())
            val certificateHandler = cycle.getInjector().getInstance(CertificateHandler::class.java)
            certificateHandler.acquireCertificate(handlerCertPath, "http://127.0.0.1:$PORT")

            Assertions.assertEquals(1, postRequests.size)
            Assertions.assertEquals(postRequests[0].second.ips.size, certificateHandler.domains.get()!!.size)
            Assertions.assertEquals(PASSPHRASE, certificateHandler.certificate.passphrase)
            Assertions.assertEquals(ALIAS, certificateHandler.certificate.getAlias())
            cleanup(certificateHandler)
        }
    }

    @KtorExperimentalAPI
    @Test
    fun testChangedIp() {
        runBlocking {
            if (!handlerCertPath.toFile().exists()) {
                generateCertificate(handlerCertPath.toFile(), keyPassword = PASSPHRASE, jksPassword = PASSPHRASE, keyAlias = ALIAS)
            }
            val cycle = Lifecyclist()
            cycle.create(File("plugins"))
            cycle.inject(SwingBrowserOpener())
            val certificateHandler = cycle.getInjector().getInstance(CertificateHandler::class.java)

            certificateHandler.domains.set(mapOf(InetAddress.getLoopbackAddress().hostAddress to DOMAIN))
            certificateHandler.key.set(PASSPHRASE)

            certificateHandler.acquireCertificate(handlerCertPath, "http://127.0.0.1:$PORT")

            Assertions.assertEquals(1, postRequests.size)
            Assertions.assertEquals(postRequests[0].second.ips.size, certificateHandler.domains.get()!!.size)
            Assertions.assertEquals(PASSPHRASE, certificateHandler.certificate.passphrase)
            Assertions.assertEquals(ALIAS, certificateHandler.certificate.getAlias())
            cleanup(certificateHandler)
        }
    }
}
