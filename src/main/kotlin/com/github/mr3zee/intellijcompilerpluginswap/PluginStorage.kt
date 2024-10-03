package com.github.mr3zee.intellijcompilerpluginswap

import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

@Service
@State(
    name = "CompilerPluginsSwapStorage",
    storages = [
        Storage(
            "${StoragePathMacros.CACHE_FILE}/kotlin-fir-compiler-plugins-swap-storage.xml",
            roamingType = RoamingType.DISABLED,
        ),
    ]
)
class PluginStorageService(
    private val scope: CoroutineScope,
) : SimplePersistentStateComponent<PluginStorage>(PluginStorage()) {
    private val cacheFolder: Path
    private val compatible: Boolean = SystemInfo.isLinux || SystemInfo.isWindows || SystemInfo.isMac

    init {
        val folderName = "kotlin-fir-compiler-plugin-swap"
        cacheFolder = when {
            SystemInfo.isLinux -> {
                System.getenv("XDG_CACHE_HOME") ?: "~/.cache"
            }

            SystemInfo.isWindows -> {
                System.getenv("LOCALAPPDATA") ?: "${System.getenv("USERPROFILE")}/AppData/Local"
            }

            SystemInfo.isMac -> {
                "~/Library/Caches/"
            }

            else -> {
                error("Unsupported OS")
            }
        }.let(Path::of).resolve(folderName).toAbsolutePath()
    }

    fun actualizePlugins() {
        if (!compatible) {
            return
        }

        scope.launch {
            val kotlinIdeVersion = service<KotlinVersionService>().getKotlinPluginVersion()
            val plugins = service<PluginSettingsService>().state.plugins

            plugins.forEach { plugin ->
                launch(CoroutineName("jar-fetcher-${plugin.groupId}-${plugin.artifactId}")) {
                    val destination = cacheFolder
                        .resolve(kotlinIdeVersion)
                        .resolve(plugin.getPluginGroupPath())

                    withContext(Dispatchers.IO) {
                        val jarPath = JarDownloader.downloadLatestIfNotExists(
                            repoUrl = plugin.repoUrl,
                            groupId = plugin.groupId,
                            artifactId = plugin.artifactId,
                            kotlinIdeVersion = kotlinIdeVersion,
                            dest = destination,
                        )

                        val old = state.pluginPaths.getOrPut(kotlinIdeVersion) {
                            mutableMapOf()
                        }

                        val oldJar = old[plugin.id]?.let(Path::of)
                        if (oldJar != null && File(oldJar.toUri()).exists()) {
                            deletePlugin(oldJar)
                        }

                        state.pluginPaths.replace(
                            kotlinIdeVersion,
                            old + (plugin.id to jarPath.toString())
                        )
                    }
                }
            }
        }
    }

    fun getPluginPath(descriptor: PluginDescriptor): Path? {
        if (!compatible) {
            return null
        }

        val kotlinVersion = service<KotlinVersionService>().getKotlinPluginVersion()

        return state.pluginPaths[kotlinVersion]?.get(descriptor.id)?.let(Path::of)
    }

    private fun deletePlugin(path: Path) {
        scope.launch(CoroutineName("jar-deleter-${path.fileName}")) {
            withContext(Dispatchers.IO) {
                if (path.toFile().exists()) {
                    path.toFile().delete()
                }
            }
        }
    }
}

internal fun PluginDescriptor.getPluginPath(version: String): Path {
    return getPluginGroupPath().resolve("$artifactId-$version.jar")
}

internal fun PluginDescriptor.getPluginGroupPath(): Path {
    val group = groupId.split(".")
    return Path.of(group[0], *group.drop(1).toTypedArray())
}

class PluginStorage : BaseState() {
    /**
     * <Kotlin IDE Version> to <plugin id> to <plugin jar path>
     * ```
     * {
     *    "2.1.0-dev-8741": {
     *        "org.jetbrains.kotlinx:kotlinx-rpc-compiler-plugin-cli" : "/path/to/plugin.jar"
     *     }
     * }
     * ```
     */
    val pluginPaths by map<String, Map<String, String>>()
}
