package net.bjoernpetersen.deskbot.rest.location

import com.github.zafarkhaja.semver.ParseException
import com.google.inject.AbstractModule
import com.google.inject.Provides
import net.bjoernpetersen.deskbot.impl.MainConfigEntries
import net.bjoernpetersen.musicbot.spi.version.ImplementationInfo
import net.bjoernpetersen.musicbot.spi.version.Version
import java.io.IOException
import java.util.Properties
import javax.inject.Singleton

private const val PROJECT_PAGE = "https://github.com/BjoernPetersen/MusicBot-desktop"
private const val PROJECT_NAME = "DeskBot"

object VersionConstraints {
    const val PATH = "/version"
}

class VersionModule() : AbstractModule() {

    companion object {
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

        private fun loadApiVersion(): String = "0.15.3"
    }

    @Provides
    @Singleton
    fun provideVersion(configEntries: MainConfigEntries): Version {
        val apiVersion = loadApiVersion()
        val botName = configEntries.instanceName.get()!!
        val implementation =
            ImplementationInfo(
                PROJECT_PAGE,
                PROJECT_NAME,
                loadImplementationVersion()
            )
        return Version(apiVersion, botName, implementation)
    }
}
