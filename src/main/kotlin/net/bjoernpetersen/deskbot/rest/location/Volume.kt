package net.bjoernpetersen.deskbot.rest.location

import com.google.inject.Injector
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.put
import io.ktor.response.respond
import io.ktor.routing.Route
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.impl.getValue
import net.bjoernpetersen.deskbot.rest.BadRequestException
import net.bjoernpetersen.deskbot.rest.require
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.plugin.volume.Volume
import net.bjoernpetersen.musicbot.api.plugin.volume.VolumeManager
import javax.inject.Inject

private val logger = KotlinLogging.logger {}

@KtorExperimentalLocationsAPI
@Location("/volume")
class GetVolumeRequest

@KtorExperimentalLocationsAPI
@Location("/volume")
data class SetVolumeRequest(val value: Int)

private class VolumeAccess @Inject private constructor(
    private val volumeManager: VolumeManager
) {
    suspend fun getVolume(): Volume {
        return volumeManager.getVolume()
    }

    suspend fun setVolume(value: Int) {
        volumeManager.setVolume(value)
    }
}

@KtorExperimentalLocationsAPI
fun Route.routeVolume(injector: Injector) {
    val access: VolumeAccess by injector
    access.apply {
        authenticate {
            get<GetVolumeRequest> {
                call.respond(getVolume())
            }
            put<SetVolumeRequest> {
                require(Permission.CHANGE_VOLUME)
                try {
                    setVolume(it.value)
                } catch (e: IllegalArgumentException) {
                    throw BadRequestException(e.message)
                }
                call.respond(getVolume())
            }
        }
    }
}
