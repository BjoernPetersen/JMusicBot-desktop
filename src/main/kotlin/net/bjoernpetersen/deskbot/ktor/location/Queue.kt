package net.bjoernpetersen.deskbot.ktor.location

import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.locations.put
import io.ktor.response.respond
import io.ktor.routing.Route
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.impl.getValue
import net.bjoernpetersen.deskbot.ktor.NotFoundException
import net.bjoernpetersen.deskbot.ktor.require
import net.bjoernpetersen.deskbot.ktor.user
import net.bjoernpetersen.deskbot.rest.model.CoreQueueEntry
import net.bjoernpetersen.deskbot.rest.model.QueueEntry
import net.bjoernpetersen.deskbot.rest.model.SongEntry
import net.bjoernpetersen.deskbot.rest.model.toModel
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.auth.User
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.spi.player.PlayerHistory
import net.bjoernpetersen.musicbot.spi.player.SongQueue
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.plugin.Plugin
import net.bjoernpetersen.musicbot.spi.plugin.PluginLookup
import net.bjoernpetersen.musicbot.spi.plugin.Provider
import javax.inject.Inject

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/player/queue")
private class GetQueueRequest

@KtorExperimentalLocationsAPI
@Location("/player/queue/history")
private class GetRecentQueueRequest

@KtorExperimentalLocationsAPI
@Location("/player/queue")
private class ModifyQueueRequest(
    val songId: String,
    val providerId: String
)

@KtorExperimentalLocationsAPI
@Location("/player/queue/order")
private class MoveRequest(
    val index: Int,
    val songId: String,
    val providerId: String
)

private class QueueAccess @Inject private constructor(
    private val queue: SongQueue,
    private val playerHistory: PlayerHistory,
    private val pluginLookup: PluginLookup
) {
    fun getQueue(): List<QueueEntry> {
        return queue.toList().map { it.toModel() }
    }

    fun getRecentQueue(): List<SongEntry> {
        return playerHistory.getHistory().map { it.toModel() }
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

    fun User.enqueue(song: Song) {
        queue.insert(CoreQueueEntry(song, this))
    }

    fun getEntry(providerId: String, songId: String): CoreQueueEntry? {
        return queue.toList()
            .firstOrNull { it.song.provider.id == providerId && it.song.id == songId }
    }

    fun dequeue(song: Song) {
        queue.remove(song)
    }

    fun moveSong(song: Song, index: Int) {
        queue.move(song, index)
    }
}

@KtorExperimentalLocationsAPI
fun Route.routeQueue(injector: Injector) {
    val access: QueueAccess by injector
    access.apply {
        get<GetQueueRequest> {
            call.respond(getQueue())
        }
        get<GetRecentQueueRequest> {
            call.respond(getRecentQueue())
        }
        put<ModifyQueueRequest> {
            require(Permission.ENQUEUE)
            val song = getProvider(it.providerId)
                ?.getSong(it.songId)
                ?: throw NotFoundException()
            call.user.enqueue(song)
            call.respond(getQueue())
        }
        delete<ModifyQueueRequest> {
            val entry = getEntry(it.providerId, it.songId)
            if (entry != null) {
                if (entry.user != call.user) require(Permission.SKIP)
                dequeue(entry.song)
            }
            call.respond(getQueue())
        }
        put<MoveRequest> {
            require(Permission.MOVE)
            val song = getProvider(it.providerId)
                ?.getSong(it.songId)
                ?: throw NotFoundException()
            moveSong(song, it.index)
            call.respond(getQueue())
        }
    }
}
