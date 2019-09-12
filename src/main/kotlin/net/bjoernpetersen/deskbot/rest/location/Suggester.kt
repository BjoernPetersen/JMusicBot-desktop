package net.bjoernpetersen.deskbot.rest.location

import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.impl.getValue
import net.bjoernpetersen.deskbot.rest.NotFoundException
import net.bjoernpetersen.deskbot.rest.UnavailableException
import net.bjoernpetersen.deskbot.rest.require
import net.bjoernpetersen.deskbot.rest.respondEmpty
import net.bjoernpetersen.deskbot.rest.model.NamedPlugin
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.plugin.management.PluginFinder
import net.bjoernpetersen.musicbot.spi.plugin.BrokenSuggesterException
import net.bjoernpetersen.musicbot.spi.plugin.Plugin
import net.bjoernpetersen.musicbot.spi.plugin.PluginLookup
import net.bjoernpetersen.musicbot.spi.plugin.Provider
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.id
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/suggester")
class SuggestersRequest

@KtorExperimentalLocationsAPI
@Location("/suggester/{suggesterId}")
data class SuggestionsRequest(
    val suggesterId: String,
    val max: Int = 32
)

@KtorExperimentalLocationsAPI
@Location("/suggester/{suggesterId}")
data class DislikeRequest(
    val suggesterId: String,
    val providerId: String,
    val songId: String
)

private class SuggesterAccess @Inject private constructor(
    private val pluginFinder: PluginFinder,
    private val pluginLookup: PluginLookup
) {
    @Suppress("UNCHECKED_CAST")
    fun getSuggesters(): List<NamedPlugin<*>> {
        return pluginFinder.suggesters.map {
            NamedPlugin(it.id as KClass<Suggester>, it.subject)
        }
    }

    fun getProvider(providerId: String): Provider? {
        return pluginLookup.lookup<Plugin>(providerId) as? Provider
    }

    fun getSuggester(suggesterId: String): Suggester? {
        return pluginLookup.lookup<Plugin>(suggesterId) as? Suggester
    }
}

@KtorExperimentalLocationsAPI
fun Route.routeSuggester(injector: Injector) {
    val access: SuggesterAccess by injector
    access.apply {
        authenticate {
            get<SuggestersRequest> {
                call.respond(getSuggesters())
            }
            get<SuggestionsRequest> {
                val suggester = getSuggester(it.suggesterId) ?: throw NotFoundException()
                val max = max(1, min(64, it.max))
                val suggestions = try {
                    suggester.getNextSuggestions(max)
                } catch (e: BrokenSuggesterException) {
                    throw UnavailableException()
                }

                val limited = suggestions.subList(0, min(suggestions.size, max))
                call.respond(limited)
            }
            delete<DislikeRequest> {
                require(Permission.DISLIKE)
                val suggester = getSuggester(it.suggesterId) ?: throw NotFoundException()
                val song = getProvider(it.providerId)
                    ?.lookup(it.songId)
                    ?: throw NotFoundException()

                suggester.dislike(song)
                call.respondEmpty()
            }
        }
    }
}
