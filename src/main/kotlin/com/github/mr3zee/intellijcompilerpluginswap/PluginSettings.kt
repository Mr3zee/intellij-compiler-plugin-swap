package com.github.mr3zee.intellijcompilerpluginswap

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(
    name = "CompilerPluginsSwapSettings",
    storages = [Storage("kotlin-fir-compiler-plugins-swap.xml")]
)
class PluginSettingsService : SimplePersistentStateComponent<PluginSettings>(PluginSettings())

class PluginSettings : BaseState() {
    val plugins by list<PluginDescriptor>()

    init {
        plugins.add(PluginDescriptor.KotlinxRpc.CLI)
        plugins.add(PluginDescriptor.KotlinxRpc.K2)
        plugins.add(PluginDescriptor.KotlinxRpc.BACKEND)
        plugins.add(PluginDescriptor.KotlinxRpc.COMMON)
    }
}

data class PluginDescriptor(
    val repoUrl: String,
    val groupId: String,
    val artifactId: String,
) {
    val id get() = "$groupId:$artifactId"
    val groupUrl get() = groupId.replace(".", "/")

    object KotlinxRpc {
        private fun kotlinxRpc(suffix: String) = PluginDescriptor(
            repoUrl = "https://maven.pkg.jetbrains.space/public/p/krpc/for-ide",
            groupId = "org.jetbrains.kotlinx",
            artifactId = "kotlinx-rpc-compiler-plugin$suffix",
        )

        val CLI = kotlinxRpc("-cli")
        val K2 = kotlinxRpc("-k2")
        val BACKEND = kotlinxRpc("-backend")
        val COMMON = kotlinxRpc("-common")
    }
}
