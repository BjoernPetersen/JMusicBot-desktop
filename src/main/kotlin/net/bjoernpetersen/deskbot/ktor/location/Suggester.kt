package net.bjoernpetersen.deskbot.ktor.location

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
import net.bjoernpetersen.deskbot.ktor.NotFoundException
import net.bjoernpetersen.deskbot.ktor.UnavailableException
import net.bjoernpetersen.deskbot.ktor.require
import net.bjoernpetersen.deskbot.ktor.respondEmpty
import net.bjoernpetersen.deskbot.rest.model.NamedPlugin
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.plugin.management.PluginFinder
import net.bjoernpetersen.musicbot.spi.plugin.BrokenSuggesterException
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.plugin.Plugin
import net.bjoernpetersen.musicbot.spi.plugin.PluginLookup
import net.bjoernpetersen.musicbot.spi.plugin.Provider
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.id
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/suggester")
private class SuggestersRequest

@KtorExperimentalLocationsAPI
@Location("/suggester/{suggesterId}")
private data class SuggestionsRequest(
    val suggesterId: String,
    val max: Int = 32
)

@KtorExperimentalLocationsAPI
@Location("/provider/{suggesterId}")
private data class DislikeRequest(
    val suggesterId: String,
    val providerId: String,
    val songId: String
)

private class SuggesterAccess @Inject private constructor(
    private val pluginFinder: PluginFinder,
    private val pluginLookup: PluginLookup
) {
    fun getSuggesters(): List<NamedPlugin<Plugin>> {
        return pluginFinder.suggesters.map {
            NamedPlugin(it.id, it.subject)
        }
    }

    fun getProvider(providerId: String): Provider? {
        return pluginLookup.lookup<Plugin>(providerId) as? Provider
    }

    suspend fun Provider.getSong(songId: String): Song? {
        return try {
            lookup(songId)
        } catch (e: NoSuchSongException) {
            return null
        }
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
                val suggestions = try {
                    suggester.getNextSuggestions(max(1, min(64, it.max)))
                } catch (e: BrokenSuggesterException) {
                    throw UnavailableException()
                }

                call.respond(suggestions)
            }
            delete<DislikeRequest> {
                require(Permission.DISLIKE)
                val suggester = getSuggester(it.suggesterId) ?: throw NotFoundException()
                val song = getProvider(it.providerId)
                    ?.getSong(it.songId)
                    ?: throw NotFoundException()

                suggester.dislike(song)
                call.respondEmpty()
            }
        }
    }
}
