package com.colorstruction.update

import kotlinx.coroutines.flow.Flow
import java.io.File

/** Events emitted by [ApkDownloader.download]. */
sealed interface ApkDownloadEvent {
    /** Fraction of the APK written so far, in `0..1`. */
    data class Progress(val fraction: Float) : ApkDownloadEvent

    /** Download finished and verified; [file] is the cached APK. */
    data class Finished(val file: File) : ApkDownloadEvent
}

/** Downloads a release APK into the app cache; swapped for a fake in tests. */
interface ApkDownloader {
    /**
     * Streams [asset] into `cacheDir/updates/ColorStruction-<tag>.apk`, emitting progress.
     * Throws [UpdateError] on failure; collecting the flow is cancellable.
     */
    fun download(asset: ReleaseAsset, tag: String): Flow<ApkDownloadEvent>

    /** Deletes cached APKs whose version is not newer than the installed [versionName]. */
    suspend fun clearStale(versionName: String)
}
