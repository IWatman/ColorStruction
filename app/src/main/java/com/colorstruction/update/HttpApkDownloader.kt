package com.colorstruction.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [ApkDownloader] that streams the APK with [HttpURLConnection] into `cacheDir/updates`. */
class HttpApkDownloader(
    cacheDir: File,
    private val connectTimeoutMs: Int = TIMEOUT_MS,
    private val readTimeoutMs: Int = TIMEOUT_MS,
) : ApkDownloader {

    private val updatesDir = File(cacheDir, DIR_NAME)

    /** The file [download] writes for [tag]. */
    fun fileFor(tag: String): File = File(updatesDir, "$FILE_PREFIX$tag.apk")

    override fun download(asset: ReleaseAsset, tag: String): Flow<ApkDownloadEvent> = flow {
        updatesDir.mkdirs()
        // Only one update is ever pending: drop whatever was cached before.
        updatesDir.listFiles()?.forEach { it.delete() }
        val target = fileFor(tag)
        val part = File(updatesDir, target.name + PART_SUFFIX)
        emit(ApkDownloadEvent.Progress(0f))
        val connection = open(asset.downloadUrl)
        var completed = false
        try {
            checkStatus(connection)
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            val written = try {
                var count = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            count += read
                            if (total > 0) {
                                emit(ApkDownloadEvent.Progress((count.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                        output.flush()
                    }
                }
                count
            } catch (e: IOException) {
                throw UpdateError.Download(e.message, e)
            }
            if (asset.size > 0 && written != asset.size) {
                throw UpdateError.Download("expected ${asset.size} bytes, got $written")
            }
            if (!part.renameTo(target)) throw UpdateError.Download("cannot move the downloaded file")
            completed = true
            emit(ApkDownloadEvent.Finished(target))
        } finally {
            connection.disconnect()
            if (!completed) part.delete()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clearStale(versionName: String) = withContext(Dispatchers.IO) {
        val current = Version.parse(versionName)
        updatesDir.listFiles()?.forEach { file ->
            val tag = file.name.removePrefix(FILE_PREFIX).removeSuffix(".apk")
            val version = Version.parse(tag)
            // Unparsable leftovers and anything not newer than the installed build are junk.
            if (version == null || current == null || version <= current) file.delete()
        }
        Unit
    }

    private fun open(url: String): HttpURLConnection {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw UpdateError.Network(e.message, e)
        }
        return connection.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
        }
    }

    private fun checkStatus(connection: HttpURLConnection) {
        val status = try {
            connection.responseCode
        } catch (e: IOException) {
            throw UpdateError.Network(e.message, e)
        }
        when {
            status == HTTP_FORBIDDEN || status == HTTP_TOO_MANY_REQUESTS ->
                throw UpdateError.RateLimited(connection.getHeaderField("Retry-After")?.toLongOrNull())
            status !in 200..299 -> throw UpdateError.Network("HTTP $status")
        }
    }

    companion object {
        private const val DIR_NAME = "updates"
        private const val FILE_PREFIX = "ColorStruction-"
        private const val PART_SUFFIX = ".part"
        private const val USER_AGENT = "ColorStruction"
        private const val TIMEOUT_MS = 10_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
