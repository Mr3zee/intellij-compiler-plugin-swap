package com.github.mr3zee.intellijcompilerpluginswap

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.idea.fir.extensions.KotlinBundledFirCompilerPluginProvider
import java.nio.file.Path

@Suppress("UnstableApiUsage")
class PluginProvider : KotlinBundledFirCompilerPluginProvider {
    override fun provideBundledPluginJar(userSuppliedPluginJar: Path): Path? {
        val descriptor = userSuppliedPluginJar.toPluginDescriptor() ?: return null
        return service<PluginStorageService>().getPluginPath(descriptor)
    }
}

internal fun Path.toPluginDescriptor(): PluginDescriptor? {
    val artifact = last().toString()
    val withGroup = parent.toString()
    val plugins = service<PluginSettingsService>().state.plugins
    return plugins.firstOrNull {
        withGroup.endsWith(it.groupUrl) && artifact.startsWith(it.artifactId)
    }
}
