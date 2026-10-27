package com.colorstruction.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/** Scriptable [ReleaseSource] for [UpdateManagerTest]. */
class FakeReleaseSource : ReleaseSource {

    var response: () -> ReleaseInfo = { error("no response configured") }
    var calls: Int = 0
        private set

    override suspend fun fetchLatestRelease(): ReleaseInfo {
        calls++
        return response()
    }
}

/** Scriptable [ApkDownloader]: emits two progress events and the finished file, or [failure]. */
class FakeDownloader(private val apk: File) : ApkDownloader {

    var failure: Throwable? = null
    var lastTag: String? = null
    var staleVersion: String? = null

    override fun download(asset: ReleaseAsset, tag: String): Flow<ApkDownloadEvent> = flow {
        lastTag = tag
        emit(ApkDownloadEvent.Progress(0f))
        failure?.let { throw it }
        emit(ApkDownloadEvent.Progress(0.5f))
        emit(ApkDownloadEvent.Finished(apk))
    }

    override suspend fun clearStale(versionName: String) {
        staleVersion = versionName
    }
}

/** Scriptable [ApkInstaller] whose permission, session liveness and result are test-controlled. */
class FakeInstaller : ApkInstaller {

    var permissionGranted: Boolean = true
    var sessionActive: Boolean = true
    var startFailure: Throwable? = null
    var starts: Int = 0
        private set

    override fun hasInstallPermission(): Boolean = permissionGranted

    override fun isSessionActive(sessionId: Int): Boolean = sessionActive

    override suspend fun startInstall(file: File): InstallStart {
        starts++
        if (!permissionGranted) return InstallStart.NeedsPermission
        startFailure?.let { throw it }
        return InstallStart.Started(SESSION_ID)
    }

    companion object {
        const val SESSION_ID = 42
    }
}

/** The release handed out by [FakeReleaseSource] in the tests. */
val FAKE_RELEASE = ReleaseInfo(
    tag = "v0.2.0",
    name = "ColorStruction 0.2.0",
    notes = "- In-app updates",
    htmlUrl = "https://github.com/IWatman/ColorStruction/releases/tag/v0.2.0",
    apk = ReleaseAsset(
        name = "ColorStruction-0.2.0.apk",
        downloadUrl = "https://example.com/ColorStruction-0.2.0.apk",
        size = 16L,
    ),
)
