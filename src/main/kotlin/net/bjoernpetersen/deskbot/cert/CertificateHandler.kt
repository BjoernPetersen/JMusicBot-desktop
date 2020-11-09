package net.bjoernpetersen.deskbot.cert

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.view.DeskBot
import net.bjoernpetersen.deskbot.view.get
import net.bjoernpetersen.musicbot.api.config.ConfigManager
import net.bjoernpetersen.musicbot.api.config.GenericConfigScope
import net.bjoernpetersen.musicbot.api.config.listSerializer
import net.bjoernpetersen.musicbot.api.config.serialization
import net.bjoernpetersen.musicbot.api.config.serialized
import net.bjoernpetersen.musicbot.api.config.string
import net.bjoernpetersen.musicbot.spi.domain.DomainHandler
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Path
import java.util.Base64
import javax.inject.Inject

/**
 * Serialize the IP to domain mapping
 */
private val certificateSerializer = serialization<Pair<String, String>> {
    deserialize { it.split('|').let { (a, b) -> a to b } }
    serialize { "${it.first}|${it.second}" }
}

class CertificateHandler @Inject private constructor(
    configManager: ConfigManager
) : DomainHandler {

    private val logger = KotlinLogging.logger {}
    lateinit var certificate: Certificate

    val key by configManager[GenericConfigScope(CertificateHandler::class)].secrets.string {
        description = ""
        check { null }
    }

    /**
     * Maps InetAddress to domains
     */
    val domains by configManager[GenericConfigScope(CertificateHandler::class)].plain
        .serialized<Map<String, String>> {
            description = ""
            check { null }
            default(emptyMap())
            serialization {
                deserialize {
                    certificateSerializer.listSerializer()
                        .deserialize(it)
                        .fold(mutableMapOf(), { acc, pair -> acc[pair.first] = pair.second; return@fold acc })
                }

                serialize {
                    certificateSerializer.listSerializer()
                        .serialize(it.map { entry -> entry.key to entry.value })
                }
            }
        }

    /**
     * Load a certificate from disk
     *
     * @param path Path to certificate store
     * @param passphrase Passphrase for the certificate store
     */
    private fun loadCertificate(path: Path, passphrase: String): Certificate {
        val cert = Certificate(passphrase)
        cert.keystore.load(path.toFile().inputStream(), passphrase.toCharArray())
        return cert
    }

    /**
     * Request a new certificate from LSaaS
     *
     * @param url LSaas url
     * @param addresses List of IP addresses to request a certificate for
     * @return The newly retrieved certificate
     */
    @KtorExperimentalAPI
    suspend fun retrieveCertificate(url: String, addresses: List<String>): Certificate {
        return withContext(Dispatchers.IO) {
            logger.info { "Requesting new certificate from $url" }
            val client = HttpClient(OkHttp) {
                install(JsonFeature) {
                    serializer = JacksonSerializer()
                }
            }
            val response = client.post<InitialResponse> {
                url(url)
                contentType(ContentType.Application.Json)
                body = InitialRequest(addresses)
            }
            domains.set(
                response.domains.fold(
                    mutableMapOf(),
                    { acc, ipDomain -> ipDomain.ips.forEach { ip -> acc[ip] = ipDomain.domain }; acc }
                )
            )
            val certificate = Certificate(response.token)

            var done = false
            while (!done) {
                val certResponse = client.get<CertificateResponse> {
                    url("$url/${response.token}")
                }
                if (certResponse.hasCertificate) {
                    logger.debug { "Receiving new certificate" }
                    assert(certResponse.jks != null)
                    val decodedStream = Base64.getDecoder().decode(certResponse.jks!!.jks).inputStream()
                    logger.debug { "Loading new certificate" }
                    certificate.keystore.load(
                        decodedStream,
                        certificate.passphrase.toCharArray()
                    )
                    logger.debug { "Completed loading new certificate" }
                    done = true
                }
                delay(10000L)
            }
            certificate
        }
    }

    /**
     * Check the provided path for a valid certificate and request a new one, if the file does not exist
     * or if the store does not contain a valid X.509 certificate.
     * If a new certificate is acquired it will be saved under the provided location.
     *
     * @param path Path to the certificate store
     * @param url Url to access LSaaS by
     */
    @KtorExperimentalAPI
    suspend fun acquireCertificate(path: Path, url: String) {
        val file = path.toFile()
        if (file.exists() && !key.get().isNullOrEmpty() &&
            (domains.get()!! == emptyMap<String, String>() || domains.get()!!.keys.containsAll(findAdresses()))
        ) {
            try {
                val cert = loadCertificate(path, key.get()!!)
                if (cert.isValid()) {
                    logger.info { "Using existing certificate" }
                    certificate = cert
                    return
                }
            } catch (exc: Exception) {
                logger.info(exc) { "Local certificate invalid" }
            }
        }
        if (file.exists()) {
            logger.debug { "Deleting existing invalid certificate in $path" }
            file.delete()
        } else {
            logger.debug { "No local certificate found" }
        }
        val cert = retrieveCertificate(url, findAdresses())
        logger.debug { "Storing new certificate" }
        withContext(Dispatchers.IO) {
            val stream = file.outputStream()
            cert.keystore.store(stream, cert.passphrase.toCharArray())
            stream.close()
        }
        logger.debug { "Stored certificate successfully" }
        key.set(cert.passphrase)
        certificate = cert
    }

    /**
     * Returns a list of all IP addresses that this mashine could be accessible under.
     */
    private fun findAdresses(): List<String> {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter {
                !it.isLoopback &&
                    !it.isVirtual &&
                    it.isUp
            }.flatMap {
                it.inetAddresses.toList().filter { a ->
                    !a.isMulticastAddress &&
                        a is Inet4Address
                }.asSequence()
            }.map { i -> i.hostAddress }.toList()
    }

    override fun getDomainByIp(): Map<String, String> {
        return domains.get()!!
    }
}

data class InitialRequest(val ips: List<String>, val keyFormat: String = "jks")
data class InitialResponse(
    val wildcardDomain: String,
    val domains: List<IpDomain>,
    val token: String,
    val keyFormat: String
)

data class CertificateResponse(val hasCertificate: Boolean, val jks: Jks?)
data class Jks(val jks: String)
data class IpDomain(val ips: List<String>, val domain: String)

class CertificateHandlerModule() : AbstractModule() {

    override fun configure() {
        bind(CertificateHandler::class.java).`in`(Scopes.SINGLETON)
        bind(DomainHandler::class.java).to(CertificateHandler::class.java).`in`(Scopes.SINGLETON)
    }
}
