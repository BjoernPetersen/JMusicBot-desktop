@file:Suppress("MatchingDeclarationName")

package net.bjoernpetersen.deskbot.rest.location

import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.put
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.impl.getValue
import net.bjoernpetersen.deskbot.rest.model.PlayerState
import net.bjoernpetersen.deskbot.rest.model.PlayerStateAction
import net.bjoernpetersen.deskbot.rest.model.PlayerStateChange
import net.bjoernpetersen.deskbot.rest.model.toModel
import net.bjoernpetersen.deskbot.rest.require
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.player.ProgressTracker
import net.bjoernpetersen.musicbot.spi.player.Player
import javax.inject.Inject

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/player")
class PlayerStateRequest

private class PlayerAccess @Inject private constructor(
    val player: Player,
    val progressTracker: ProgressTracker
) {
    suspend fun resume() {
        player.play()
    }

    suspend fun pause() {
        player.pause()
    }

    suspend fun skip() {
        player.next()
    }

    suspend fun getPlayerState(): PlayerState {
        var progress = progressTracker.getCurrentProgress().duration.seconds.toInt()

        if (progress < 0) {
            logger.warn { "Got negative progress from tracker" }
            progress = 0
        }

        val state = player.state
        val songDuration = state.entry?.song?.duration
        if (songDuration != null && progress > songDuration) {
            progress = songDuration
        }

        return player.state.toModel(progress)
    }
}

@KtorExperimentalLocationsAPI
fun Route.routePlayer(injector: Injector) {
    val playerAccess: PlayerAccess by injector
    playerAccess.apply {
        authenticate {
            get<PlayerStateRequest> {
                call.respond(getPlayerState())
            }

            put<PlayerStateRequest> {
                val change: PlayerStateChange = call.receive()
                when (change.action) {
                    PlayerStateAction.PLAY -> {
                        require(Permission.PAUSE)
                        resume()
                    }
                    PlayerStateAction.PAUSE -> {
                        require(Permission.PAUSE)
                        pause()
                    }
                    PlayerStateAction.SKIP -> {
                        require(Permission.SKIP)
                        skip()
                    }
                }
                call.respond(getPlayerState())
            }
        }
    }
}
