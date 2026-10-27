package com.colorstruction.update

/**
 * Typed failure of a single update step.
 *
 * It is thrown by [ReleaseSource], [ApkDownloader] and [ApkInstaller], and it is also the reason
 * carried by [UpdateState.Failed], so the UI can map every case to its own message.
 */
sealed class UpdateError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Connection problem, timeout, DNS failure or an unexpected HTTP status. */
    class Network(detail: String? = null, cause: Throwable? = null) :
        UpdateError("network: ${detail ?: cause?.message ?: "unknown"}", cause)

    /** GitHub API rate limit (HTTP 403/429). [retryAfterSeconds] comes from `Retry-After`. */
    class RateLimited(val retryAfterSeconds: Long? = null) :
        UpdateError("rate limited" + (retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""))

    /** The latest release has no `.apk` asset attached. */
    data object NoApkAsset : UpdateError("release has no .apk asset")

    /** The API response could not be parsed. */
    class Parse(detail: String? = null, cause: Throwable? = null) :
        UpdateError("parse: ${detail ?: "invalid response"}", cause)

    /** Writing the APK to the cache failed, or the downloaded size did not match the asset. */
    class Download(detail: String? = null, cause: Throwable? = null) :
        UpdateError("download: ${detail ?: "failed"}", cause)

    /** The installer rejected the APK, the user cancelled, or the session broke. */
    class Install(detail: String? = null, cause: Throwable? = null) :
        UpdateError("install: ${detail ?: "failed"}", cause)

    /** Anything unexpected; keeps the state machine out of a progress state. */
    class Unknown(detail: String? = null, cause: Throwable? = null) :
        UpdateError(detail ?: cause?.message ?: "unexpected error", cause)
}
