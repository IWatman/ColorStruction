package com.colorstruction.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where the latest available release comes from; swapped for a fake in tests. */
interface ReleaseSource {
    /** @return the latest release, or throws [UpdateError] when it cannot be fetched. */
    suspend fun fetchLatestRelease(): ReleaseInfo
}

/** Reads the latest release from the GitHub REST API. */
class GitHubReleaseSource(
    private val apiUrl: String = API_URL,
    private val connectTimeoutMs: Int = TIMEOUT_MS,
    private val readTimeoutMs: Int = TIMEOUT_MS,
) : ReleaseSource {

    override suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = openConnection()
        try {
            checkStatus(connection)
            val body = try {
                connection.inputStream.use { it.readBytes().decodeToString() }
            } catch (e: IOException) {
                throw UpdateError.Network(e.message, e)
            }
            parseRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(): HttpURLConnection {
        val connection = try {
            URL(apiUrl).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw UpdateError.Network(e.message, e)
        }
        return connection.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", ACCEPT)
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
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
            status != HTTP_OK -> throw UpdateError.Network("HTTP $status")
        }
    }

    companion object {
        const val API_URL = "https://api.github.com/repos/IWatman/ColorStruction/releases/latest"
        private const val ACCEPT = "application/vnd.github+json"
        private const val USER_AGENT = "ColorStruction"
        private const val TIMEOUT_MS = 10_000
        private const val HTTP_OK = 200
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

/**
 * Parses a `releases/latest` payload into [ReleaseInfo].
 *
 * Pure function (no network, no Android): the first asset whose name ends with `.apk` wins.
 *
 * @throws UpdateError.Parse when the JSON or its `tag_name` is unusable.
 * @throws UpdateError.NoApkAsset when no APK is attached.
 */
fun parseRelease(json: String): ReleaseInfo {
    val root = try {
        JSONObject(json)
    } catch (e: JSONException) {
        throw UpdateError.Parse("invalid json", e)
    }
    val tag = root.optString("tag_name").takeIf { it.isNotBlank() }
        ?: throw UpdateError.Parse("missing tag_name")
    return ReleaseInfo(
        tag = tag,
        name = root.optString("name").ifBlank { tag },
        notes = root.optString("body").trim(),
        htmlUrl = root.optString("html_url"),
        apk = findApk(root),
    )
}

private fun findApk(root: JSONObject): ReleaseAsset {
    val assets = root.optJSONArray("assets") ?: throw UpdateError.NoApkAsset
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        val name = asset.optString("name")
        val url = asset.optString("browser_download_url")
        if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
        return ReleaseAsset(name = name, downloadUrl = url, size = asset.optLong("size", 0L))
    }
    throw UpdateError.NoApkAsset
}
