package net.bjoernpetersen.deskbot.lifecycle

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import io.ktor.util.KtorExperimentalAPI
import javafx.application.Platform
import javafx.concurrent.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.bjoernpetersen.deskbot.cert.CertificateHandler
import net.bjoernpetersen.deskbot.cert.CertificateHandlerModule
import net.bjoernpetersen.deskbot.fximpl.FxProgressFeedback
import net.bjoernpetersen.deskbot.impl.Broadcaster
import net.bjoernpetersen.deskbot.impl.FileConfigStorage
import net.bjoernpetersen.deskbot.impl.FileStorageImpl
import net.bjoernpetersen.deskbot.impl.ImageLoaderImpl
import net.bjoernpetersen.deskbot.impl.MainConfigEntries
import net.bjoernpetersen.deskbot.impl.SongPlayedNotifierModule
import net.bjoernpetersen.deskbot.rest.KtorServer
import net.bjoernpetersen.deskbot.rest.location.VersionModule
import net.bjoernpetersen.deskbot.view.DeskBot
import net.bjoernpetersen.deskbot.view.get
import net.bjoernpetersen.deskbot.view.show
import net.bjoernpetersen.musicbot.api.auth.BotUser
import net.bjoernpetersen.musicbot.api.auth.DefaultPermissions
import net.bjoernpetersen.musicbot.api.config.ConfigManager
import net.bjoernpetersen.musicbot.api.config.GenericConfigScope
import net.bjoernpetersen.musicbot.api.config.MainConfigScope
import net.bjoernpetersen.musicbot.api.config.PluginConfigScope
import net.bjoernpetersen.musicbot.api.config.listSerializer
import net.bjoernpetersen.musicbot.api.config.serialization
import net.bjoernpetersen.musicbot.api.config.serialized
import net.bjoernpetersen.musicbot.api.module.BrowserOpenerModule
import net.bjoernpetersen.musicbot.api.module.ConfigModule
import net.bjoernpetersen.musicbot.api.module.DefaultDatabaseConnectionModule
import net.bjoernpetersen.musicbot.api.module.DefaultImageCacheModule
import net.bjoernpetersen.musicbot.api.module.DefaultPlayerModule
import net.bjoernpetersen.musicbot.api.module.DefaultQueueModule
import net.bjoernpetersen.musicbot.api.module.DefaultResourceCacheModule
import net.bjoernpetersen.musicbot.api.module.DefaultSongLoaderModule
import net.bjoernpetersen.musicbot.api.module.DefaultTokenHandlerModule
import net.bjoernpetersen.musicbot.api.module.DefaultUserDatabaseModule
import net.bjoernpetersen.musicbot.api.module.FileStorageModule
import net.bjoernpetersen.musicbot.api.module.InstanceStopper
import net.bjoernpetersen.musicbot.api.module.PluginClassLoaderModule
import net.bjoernpetersen.musicbot.api.module.PluginModule
import net.bjoernpetersen.musicbot.api.player.PlayerState
import net.bjoernpetersen.musicbot.api.player.QueueEntry
import net.bjoernpetersen.musicbot.api.plugin.PluginLoaderImpl
import net.bjoernpetersen.musicbot.api.plugin.category
import net.bjoernpetersen.musicbot.api.plugin.management.DefaultDependencyManager
import net.bjoernpetersen.musicbot.api.plugin.management.PluginFinder
import net.bjoernpetersen.musicbot.api.plugin.management.findDependencies
import net.bjoernpetersen.musicbot.spi.player.Player
import net.bjoernpetersen.musicbot.spi.player.QueueChangeListener
import net.bjoernpetersen.musicbot.spi.player.SongQueue
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.plugin.Plugin
import net.bjoernpetersen.musicbot.spi.plugin.PluginLookup
import net.bjoernpetersen.musicbot.spi.plugin.Provider
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.management.DependencyManager
import net.bjoernpetersen.musicbot.spi.util.BrowserOpener
import org.controlsfx.control.TaskProgressView
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
class Lifecyclist : CoroutineScope {
    private val logger = KotlinLogging.logger {}

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    var stage: Stage = Stage.New
        private set

    // Created stage vars
    private lateinit var configManager: ConfigManager
    private lateinit var classLoader: ClassLoader
    private lateinit var dependencyManager: DependencyManager

    // Injected stage vars
    private lateinit var pluginFinder: PluginFinder
    private lateinit var injector: Injector
    private lateinit var mainConfig: MainConfigEntries

    // Run stage vars
    private lateinit var broadcaster: Broadcaster

    private fun <T> stagedBlock(stage: Stage, exact: Boolean = true, action: () -> T): T {
        if (exact) {
            check(this.stage == stage)
        } else {
            check(this.stage >= stage)
        }
        return action()
    }

    private suspend fun <T> staged(
        stage: Stage,
        exact: Boolean = true,
        action: suspend () -> T
    ): T {
        if (exact) {
            check(this.stage == stage)
        } else {
            check(this.stage >= stage)
        }
        return action()
    }

    fun getConfigManager() = stagedBlock(Stage.Created, false) { configManager }
    fun getPluginClassLoader() = stagedBlock(Stage.Created, false) { classLoader }

    fun getDependencyManager() = stagedBlock(
        Stage.Created, false
    ) { dependencyManager }

    fun getPluginFinder() = stagedBlock(Stage.Injected, false) { pluginFinder }
    fun getInjector() = stagedBlock(Stage.Injected, false) { injector }
    fun getMainConfig() = stagedBlock(Stage.Injected, false) { mainConfig }

    private fun createConfig() {
        configManager = ConfigManager(
            FileConfigStorage(configDir),
            FileConfigStorage(secretDir),
            FileConfigStorage(stateDir)
        )
    }

    private fun createPlugins(pluginDir: File) {
        val loader = PluginLoaderImpl(pluginDir)
        dependencyManager = DefaultDependencyManager(configManager[MainConfigScope].plain, loader)
        classLoader = loader.loader
    }

    fun create(pluginDir: File): DependencyManager = stagedBlock(Stage.New) {
        job = Job()
        createConfig()
        createPlugins(pluginDir)

        stage = Stage.Created
        dependencyManager
    }

    private fun modules(browserOpener: BrowserOpener, suggester: Suggester?): List<Module> = listOf(
        ConfigModule(configManager),
        DefaultPlayerModule(suggester),
        DefaultQueueModule(),
        DefaultSongLoaderModule(),
        DefaultDatabaseConnectionModule(Paths.get("UserDatabase.db")),
        DefaultUserDatabaseModule(),
        DefaultTokenHandlerModule(),
        PluginClassLoaderModule(classLoader),
        PluginModule(pluginFinder),
        BrowserOpenerModule(browserOpener),
        SongPlayedNotifierModule(),
        DefaultImageCacheModule(),
        ImageLoaderImpl,
        DefaultResourceCacheModule(),
        FileStorageModule(FileStorageImpl::class),
        CertificateHandlerModule(),
        VersionModule()
    )

    fun inject(browserOpener: BrowserOpener) = stagedBlock(Stage.Created) {
        pluginFinder = dependencyManager.finish(emptyList(), emptyList())
        mainConfig = MainConfigEntries(configManager, pluginFinder, classLoader)
        // TODO calling finish twice is terrible.
        pluginFinder = dependencyManager.finish(
            mainConfig.providerOrder.get() ?: emptyList(),
            mainConfig.suggesterOrder.get() ?: emptyList()
        )

        val suggester = mainConfig.defaultSuggester.get()
        logger.info { "Default suggester: ${suggester?.name}" }

        injector = Guice.createInjector(modules(browserOpener, suggester))

        pluginFinder.allPlugins().forEach {
            injector.injectMembers(it)
        }

        pluginFinder.allPlugins().forEach {
            val configs = configManager[PluginConfigScope(it::class)]
            it.createConfigEntries(configs.plain)
            it.createSecretEntries(configs.secrets)
            it.createStateEntries(configs.state)
        }

        stage = Stage.Injected
    }

    @KtorExperimentalAPI
    suspend fun run(result: (Throwable?) -> Unit) = staged(Stage.Injected) {
        // TODO rollback in case of failure
        coroutineScope {
            Initializer(pluginFinder, injector).start {
                if (it != null) {
                    logger.error(it) { "Could not initialize!" }
                    result(it)
                    return@start
                }
                DefaultPermissions.defaultPermissions = mainConfig.defaultPermissions.get()!!

                val player = injector.getInstance(Player::class.java)
                player.start()

                val ktor = injector.getInstance(KtorServer::class.java)
                ktor.start()

                broadcaster = Broadcaster().apply { start() }

                GlobalScope.launch {
                    val dumper = injector.getInstance(QueueDumper::class.java)
                    dumper.restoreQueue()
                    player.addListener { _, newState -> dumper.dumpQueue(newState) }
                    injector.getInstance(SongQueue::class.java)
                        .addListener(object : QueueChangeListener {
                            override fun onAdd(entry: QueueEntry) {
                                dumper.dumpQueue()
                            }

                            override fun onMove(entry: QueueEntry, fromIndex: Int, toIndex: Int) {
                                dumper.dumpQueue()
                            }

                            override fun onRemove(entry: QueueEntry) {
                                dumper.dumpQueue()
                            }
                        })
                }

                DeskBot.runningInstance = this@Lifecyclist
                stage = Stage.Running
                result(null)
            }
        }
    }

    fun stop() = stagedBlock(Stage.Running) {
        try {
            broadcaster.close()
        } catch (e: IOException) {
            logger.error(e) { "Could not close broadcaster" }
        }

        runBlocking {
            coroutineScope {
                withContext(coroutineContext) {
                    val stopper = InstanceStopper(injector).apply {
                        register(KtorServer::class.java) { ktor ->
                            ktor.close()
                        }
                    }
                    stopper.stop()
                    stage = Stage.Stopped
                }
            }

            job.cancel()
        }
    }

    enum class Stage {
        Stopped, New, Created, Injected, Running
    }

    private companion object {
        val stateDir = File("state")
        val configDir = File("config")
        val secretDir = File(configDir, "secret")
    }
}

@Suppress("MagicNumber")
private class Initializer(
    private val finder: PluginFinder,
    private val injector: Injector
) {

    private val logger = KotlinLogging.logger {}

    @KtorExperimentalAPI
    fun start(result: (Throwable?) -> Unit) {
        val view = TaskProgressView<Task<*>>()
        val tasks = view.tasks

        val lock: Lock = ReentrantLock()
        val done = lock.newCondition()
        val finished: MutableSet<Plugin> = HashSet(64)
        val errors: MutableList<Throwable> = ArrayList()
        var certDone = false

        val res = DeskBot.resources
        val window = view.show(modal = true, title = res["window.initialization"])

        val parentTask = object : Task<Unit>() {
            override fun call() {
                updateTitle(res["task.parent.title"])
                updateMessage(res["task.parent.description"])

                // TODO timeout/cancel
                val todo = finder.run {
                    genericPlugins.size + playbackFactories.size + providers.size + suggesters.size
                }.toLong()
                updateProgress(0, todo)
                lock.withLock {
                    var finishedCount = finished.size.toLong()
                    while (finishedCount != todo) {
                        updateProgress(finishedCount, todo)
                        done.await()
                        finishedCount = finished.size.toLong()
                    }
                    while (!certDone) {
                        done.await()
                    }
                }

                val exception = if (errors.isEmpty()) null
                else errors.fold(Exception("One or more initializations failed")) { e, t ->
                    e.apply { addSuppressed(t) }
                }

                Platform.runLater {
                    result(exception)
                    window.close()
                }
            }
        }
        thread(name = "InitializationParent", isDaemon = true) { parentTask.run() }
        tasks.add(parentTask)

        val certTask = object : Task<Unit>() {
            val writer = FxProgressFeedback(::updateMessage)

            override fun call() {
                updateTitle(res["task.cert.title"])
                updateMessage(res["task.cert.description"])
                runBlocking {
                    val certHandler = injector.getInstance(CertificateHandler::class.java)
                    try {
                        // TODO: Make url configurable?
                        certHandler.acquireCertificate(Paths.get("cert.jks"), "https://instance.kiu.party")
                    } catch (e: Throwable) {
                        logger.error(e) { "Unable to load or create certificate\n$e" }
                        throw e
                    } finally {
                        lock.withLock {
                            certDone = true
                            done.signalAll()
                        }
                    }
                }
            }
        }
        thread(name = "InitializationCertificate", isDaemon = true) { certTask.run() }
        tasks.add(certTask)

        finder.allPlugins().forEach { plugin ->
            val task = object : Task<Unit>() {
                val writer = FxProgressFeedback(::updateMessage)

                override fun call() {
                    updateTitle(
                        res["task.plugin.title"].format(plugin.category.simpleName, plugin.name)
                    )
                    plugin.findDependencies()
                        .asSequence()
                        .map { finder[it]!! }
                        .forEach {
                            if (it !in finished) {
                                val type = it.category.simpleName
                                lock.withLock {
                                    while (it !in finished) {
                                        writer.state(
                                            res["task.plugin.waiting"].format(type, it.name)
                                        )
                                        done.await()
                                    }
                                }
                            }
                        }
                    writer.state("Starting...")

                    runBlocking {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            plugin.initialize(writer)
                        } catch (e: Throwable) {
                            logger.error(e) { "Could not initialize $plugin" }
                            throw e
                        }
                    }

                    lock.withLock {
                        finished.add(plugin)
                        done.signalAll()
                    }
                }
            }
            tasks.add(task)
            thread(isDaemon = true, name = "Init${plugin.category.simpleName}${plugin.name}") {
                task.run()
            }
        }
    }
}

private val songSerializer = serialization<Pair<String, String>> {
    serialize { "${it.first}|${it.second}" }
    deserialize { it.split('|').let { (a, b) -> a to b } }
}

private class QueueDumper @Inject private constructor(
    private val queue: SongQueue,
    private val player: Player,
    private val pluginLookup: PluginLookup,
    configManager: ConfigManager
) {

    private val logger = KotlinLogging.logger {}
    private val entry by configManager[GenericConfigScope(QueueDumper::class)].state
        .serialized<List<Pair<String, String>>> {
            serializer = songSerializer.listSerializer()
            description = ""
            check { null }
        }
    private var lastQueue: List<QueueEntry> = emptyList()

    private fun buildDumpQueue(
        playerState: PlayerState,
        queue: List<QueueEntry>
    ): List<QueueEntry> {
        val result = ArrayList<QueueEntry>(queue.size)
        val currentEntry = playerState.entry
        if (currentEntry is QueueEntry) {
            // If there is a current song which has not been auto-suggested, prepend it
            logger.debug { "Dumping current song first" }
            result.add(currentEntry)
        } else {
            logger.debug { "Not dumping current song. State: ${player.state}" }
        }
        result.addAll(queue)
        return result
    }

    fun dumpQueue(playerState: PlayerState = player.state) {
        logger.debug { "Dumping queue" }
        val dumpQueue = buildDumpQueue(playerState, queue.toList())
        if (dumpQueue == lastQueue) {
            logger.debug { "Not dumping unchanged queue." }
            return
        } else {
            lastQueue = dumpQueue
        }

        entry.set(dumpQueue.map { it.song.provider.id to it.song.id })
    }

    suspend fun restoreQueue() {
        logger.info("Restoring queue")
        withContext(Dispatchers.IO) {
            val pairs = entry.get()
            if (pairs.isNullOrEmpty()) return@withContext
            val songs = pairs.asSequence()
                .map { pluginLookup.lookup<Provider>(it.first) to it.second }
                .map {
                    async {
                        try {
                            it.first?.lookup(it.second)
                        } catch (e: NoSuchSongException) {
                            null
                        }
                    }
                }
                .toList()

            songs.forEach {
                val song = it.await()
                if (song != null) {
                    queue.insert(QueueEntry(song, BotUser))
                }
            }
        }
    }
}
