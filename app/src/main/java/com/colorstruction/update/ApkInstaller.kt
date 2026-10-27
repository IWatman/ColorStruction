package com.colorstruction.update

import java.io.File

/** Outcome of asking [ApkInstaller] to install a downloaded APK. */
sealed interface InstallStart {
    /** A [PackageInstaller][android.content.pm.PackageInstaller] session was committed. */
    data class Started(val sessionId: Int) : InstallStart

    /** The user has not allowed this app to install packages yet. */
    data object NeedsPermission : InstallStart
}

/** Installs a downloaded APK; swapped for a fake in tests. */
interface ApkInstaller {
    /** True when the "install unknown apps" permission is granted for this app. */
    fun hasInstallPermission(): Boolean

    /** True when [sessionId] still exists and is neither finished, failed nor abandoned. */
    fun isSessionActive(sessionId: Int): Boolean

    /**
     * Writes [file] into a `PackageInstaller` session and commits it; the final status arrives at
     * [UpdateInstallReceiver]. Throws [UpdateError] when the session cannot be created or written.
     */
    suspend fun startInstall(file: File): InstallStart
}
