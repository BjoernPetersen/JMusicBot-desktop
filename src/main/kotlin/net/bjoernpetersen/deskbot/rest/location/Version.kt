package net.bjoernpetersen.deskbot.rest.location

import com.github.zafarkhaja.semver.ParseException
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import net.bjoernpetersen.deskbot.rest.model.ImplementationInfo
import net.bjoernpetersen.deskbot.rest.model.VersionInfo
import java.io.IOException
import java.util.Properties

private const val PROJECT_PAGE = "https://github.com/BjoernPetersen/MusicBot-desktop"
private const val PROJECT_NAME = "DeskBot"

@KtorExperimentalLocationsAPI
@Location("/version")
class Version {
    companion object {
        val versionInfo: VersionInfo by lazy { loadInfo() }

        private fun loadInfo(): VersionInfo {
            val implVersion = loadImplementationVersion()
            val apiVersion = loadApiVersion()
            return VersionInfo(
                apiVersion,
                ImplementationInfo(
                    PROJECT_PAGE,
                    PROJECT_NAME,
                    implVersion
                )
            )
        }

        private fun loadImplementationVersion() = try {
            val properties = Properties()
            Version::class.java
                .getResourceAsStream("/net/bjoernpetersen/deskbot/version.properties")
                .use { versionStream -> properties.load(versionStream) }
            properties.getProperty("version") ?: throw IllegalStateException("Version is missing")
        } catch (e: IOException) {
            throw IllegalStateException("Could not read version resource", e)
        } catch (e: ParseException) {
            throw IllegalStateException("Could not read version resource", e)
        }

        private fun loadApiVersion(): String = "0.13.0"
    }
}
