package net.bjoernpetersen.deskbot.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bjoernpetersen.deskbot.view.DefaultPermissionConfig
import net.bjoernpetersen.deskbot.view.DeskBot
import net.bjoernpetersen.deskbot.view.PluginOrderConfig
import net.bjoernpetersen.deskbot.view.get
import net.bjoernpetersen.deskbot.view.load
import net.bjoernpetersen.deskbot.view.show
import net.bjoernpetersen.musicbot.api.auth.Permission
import net.bjoernpetersen.musicbot.api.config.ChoiceBox
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.ConfigManager
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.DeserializationException
import net.bjoernpetersen.musicbot.api.config.MainConfigScope
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker
import net.bjoernpetersen.musicbot.api.config.PathSerializer
import net.bjoernpetersen.musicbot.api.config.TextBox
import net.bjoernpetersen.musicbot.api.config.actionButton
import net.bjoernpetersen.musicbot.api.config.choiceBox
import net.bjoernpetersen.musicbot.api.config.listSerializer
import net.bjoernpetersen.musicbot.api.config.openDirectory
import net.bjoernpetersen.musicbot.api.config.serialized
import net.bjoernpetersen.musicbot.api.config.setSerializer
import net.bjoernpetersen.musicbot.api.config.string
import net.bjoernpetersen.musicbot.api.plugin.DeclarationException
import net.bjoernpetersen.musicbot.api.plugin.PluginId
import net.bjoernpetersen.musicbot.api.plugin.id
import net.bjoernpetersen.musicbot.api.plugin.management.PluginFinder
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Named

// TODO: make constructor private (force injection)
class MainConfigEntries @Inject constructor(
    configManager: ConfigManager,
    pluginFinder: PluginFinder,
    @Named("PluginClassLoader")
    classLoader: ClassLoader
) {
    // TODO put in constructor
    private val pluginIdSerializer = PluginId.Serializer(classLoader)

    private val plain = configManager[MainConfigScope].plain
    private val secret = configManager[MainConfigScope].secrets

    val defaultSuggester by plain.serialized<Suggester> {
        description = "The suggester providing songs if the queue is empty"
        serializer = SuggesterSerializer(classLoader, pluginFinder)
        check { null }
        uiNode = ChoiceBox({ it.name }, { pluginFinder.suggesters })
    }

    val defaultPermissions: Config.SerializedEntry<Set<Permission>> by plain.serialized {
        description = "Permissions for new users and guests"
        serializer = Permission.setSerializer()
        check(NonnullConfigChecker)
        actionButton {
            label = DeskBot.resources["action.edit"]
            describe { it.sorted().joinToString() }
            val mutex = Mutex()
            action {
                mutex.withLock {
                    withContext(Dispatchers.Main) {
                        load<DefaultPermissionConfig>().apply {
                            configEntry = defaultPermissions
                            show(modal = true, wait = true)
                        }
                    }
                }
                true
            }
        }
        default(Permission.getDefaults())
    }

    val storageDir by plain.serialized<Path> {
        description = "Directory to store plugin files in." +
            " This should preferably be somewhere with a lot of free space."
        serializer = PathSerializer
        check {
            if (it != null && Files.isDirectory(it)) null
            else "Must be an existing directory"
        }
        openDirectory()
        default(Paths.get("storage"))
    }

    val loadAlbumArt by plain.serialized<AlbumArtMode> {
        description = "Which album art images to load. Disable to save bandwidth."
        serializer = AlbumArtMode
        check(NonnullConfigChecker)
        choiceBox {
            describe { it.friendlyName }
            refresh {
                AlbumArtMode.values().asList()
            }
        }
        default(AlbumArtMode.ALL)
    }

    val providerOrder: Config.SerializedEntry<List<PluginId>> by plain.serialized {
        description = "The order in which providers are shown in clients"
        serializer = pluginIdSerializer.listSerializer()
        check { null }
        default(emptyList())
        actionButton {
            label = DeskBot.resources["action.edit"]
            describe { providerIds ->
                providerIds.joinToString { it.displayName }
            }
            action {
                withContext(Dispatchers.Main) {
                    load<PluginOrderConfig>().apply {
                        configEntry = providerOrder
                        loadedPlugins = pluginFinder.providers
                            .mapNotNull {
                                try {
                                    it.id
                                } catch (e: DeclarationException) {
                                    null
                                }
                            }
                            .distinct()
                        show(modal = true, wait = true)
                    }
                }
                true
            }
        }
    }

    val suggesterOrder: Config.SerializedEntry<List<PluginId>> by plain.serialized {
        description = "The order in which suggesters are shown in clients"
        serializer = pluginIdSerializer.listSerializer()
        check { null }
        default(emptyList())
        actionButton {
            label = DeskBot.resources["action.edit"]
            describe { suggesterIds ->
                suggesterIds.joinToString { it.displayName }
            }
            action {
                withContext(Dispatchers.Main) {
                    load<PluginOrderConfig>().apply {
                        configEntry = suggesterOrder
                        loadedPlugins = pluginFinder.suggesters
                            .mapNotNull {
                                try {
                                    it.id
                                } catch (e: DeclarationException) {
                                    null
                                }
                            }
                            .distinct()
                        show(modal = true, wait = true)
                    }
                }
                true
            }
        }
    }

    val instanceName = plain.StringEntry(
        "instanceName",
        "A name to identify this instance by. Will be used for registrations and when displayed by clients",
        NonnullConfigChecker,
        TextBox,
        "NamelessBot"
    )

    val allPlain: List<Config.Entry<*>> = listOf(
        defaultSuggester,
        storageDir,
        loadAlbumArt,
        defaultPermissions,
        providerOrder,
        suggesterOrder,
        instanceName
    )
    val allSecret: List<Config.Entry<*>> = listOf()
}

private class SuggesterSerializer(
    private val classLoader: ClassLoader,
    private val pluginFinder: PluginFinder
) : ConfigSerializer<Suggester> {

    override fun serialize(obj: Suggester): String {
        return try {
            obj.id.qualifiedName
        } catch (e: DeclarationException) {
            throw DeserializationException()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(string: String): Suggester {
        val type = try {
            classLoader.loadClass(string)
        } catch (e: ClassNotFoundException) {
            throw DeserializationException()
        } as Class<out Suggester>
        return pluginFinder[type.kotlin] ?: throw DeserializationException()
    }
}
