package com.github.mr3zee.intellijcompilerpluginswap

import com.intellij.openapi.diagnostic.thisLogger
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import org.jetbrains.kotlin.config.MavenComparableVersion
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.nio.file.Path
import kotlin.io.path.exists

object JarDownloader {
    private val logger by lazy { JarDownloader.thisLogger() }

    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    suspend fun downloadLatestIfNotExists(
        repoUrl: String,
        groupId: String,
        artifactId: String,
        kotlinIdeVersion: String,
        dest: Path,
    ): Path {
        logger.debug("Checking the latest version of $groupId:$artifactId for $kotlinIdeVersion from $repoUrl")

        val groupUrl = groupId.replace(".", "/")
        val artifactUrl = "$repoUrl/$groupUrl/$artifactId"

        val versions = downloadManifestAndGetVersions(artifactUrl)

        val latest = getLatestVersion(versions, kotlinIdeVersion)
            ?: error("No compiler plugin with id '$groupId:$artifactId' exists for $kotlinIdeVersion in $repoUrl")

        val filename = "$artifactId-$latest.jar"

        if (!dest.exists()) {
            dest.toFile().mkdirs()
        }

        val file = dest.resolve(filename).toFile()

        if (file.exists()) {
            logger.debug("A file already exists at ${file.path}")
            return file.toPath()
        }

        client.prepareGet("$artifactUrl/$latest/$filename").execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes: ByteArray = packet.readBytes()
                    file.appendBytes(bytes)
                    logger.debug("Received ${file.length()} bytes from ${httpResponse.contentLength()}")
                }
            }

            logger.debug("A file saved to ${file.path}")
        }

        return file.toPath()
    }

    internal suspend fun downloadManifestAndGetVersions(artifactUrl: String): List<String> {
        val manifest = client.get("$artifactUrl/maven-metadata.xml")
            .bodyAsText()

        return parseManifestXmlToVersions(manifest)
    }
}

internal fun getLatestVersion(versions: List<String>, prefix: String): String? =
    versions.filter { it.startsWith(prefix) }.maxByOrNull { string ->
        MavenComparableVersion(string.removePrefix(prefix))
    }

internal fun parseManifestXmlToVersions(manifest: String): List<String> {
    return try {
        Jsoup.parse(manifest, "", Parser.xmlParser())
            .getElementsByTag("metadata")[0]
            .getElementsByTag("versioning")[0]
            .getElementsByTag("versions")[0]
            .getElementsByTag("version")
            .map {
                it.text()
            }
    } catch (_: IndexOutOfBoundsException) {
        emptyList()
    }
}
