package net.bjoernpetersen.deskbot.rest.location

import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.impl.getValue
import net.bjoernpetersen.deskbot.rest.NotFoundException
import net.bjoernpetersen.deskbot.rest.model.NamedPlugin
import net.bjoernpetersen.musicbot.api.plugin.management.PluginFinder
import net.bjoernpetersen.musicbot.spi.plugin.Plugin
import net.bjoernpetersen.musicbot.spi.plugin.PluginLookup
import net.bjoernpetersen.musicbot.spi.plugin.Provider
import net.bjoernpetersen.musicbot.spi.plugin.id
import javax.inject.Inject
import kotlin.math.min
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/provider")
class ProvidersRequest

@KtorExperimentalLocationsAPI
@Location("/provider/{providerId}")
data class SearchRequest(
    val providerId: String,
    val query: String,
    val limit: Int? = null,
    val offset: Int = 0
)

@KtorExperimentalLocationsAPI
@Location("/provider/{providerId}/{songId}")
data class SongRequest(val providerId: String, val songId: String)

private class ProviderAccess @Inject private constructor(
    private val pluginFinder: PluginFinder,
    private val pluginLookup: PluginLookup
) {
    @Suppress("UNCHECKED_CAST")
    fun getProviders(): List<NamedPlugin<*>> {
        return pluginFinder.providers.map {
            NamedPlugin(it.id as KClass<Provider>, it.subject)
        }
    }

    fun getProvider(providerId: String): Provider? {
        return pluginLookup.lookup<Plugin>(providerId) as? Provider
    }
}

@KtorExperimentalLocationsAPI
fun Route.routeProvider(injector: Injector) {
    val access: ProviderAccess by injector
    access.apply {
        authenticate {
            get<ProvidersRequest> {
                call.respond(getProviders())
            }
            get<SearchRequest> {
                val songs = getProvider(it.providerId)
                    ?.search(it.query, it.offset)
                    ?: throw NotFoundException()
                // TODO if search gets a limit parameter, this should be changed
                val limited = if (it.limit == null) songs
                else songs.subList(0, min(it.limit, songs.size))
                call.respond(limited)
            }
            get<SongRequest> {
                val song = getProvider(it.providerId)
                    ?.lookup(it.songId)
                    ?: throw NotFoundException()
                call.respond(song)
            }
        }
    }
}
