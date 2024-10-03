package com.github.mr3zee.intellijcompilerpluginswap

import com.intellij.openapi.components.Service
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins

@Service
class KotlinVersionService {
    private val version by lazy {
        KotlinK2BundledCompilerPlugins.javaClass.classLoader
            .getResourceAsStream("META-INF/compiler.version")?.use { stream ->
                stream.readAllBytes().decodeToString()
            }?.trim() ?: error("Kotlin version file not found")
    }

    fun getKotlinPluginVersion(): String {
        return version
    }
}
