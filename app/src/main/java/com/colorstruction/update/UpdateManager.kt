package com.colorstruction.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the in-app updater: check GitHub, download the APK, hand it to the installer and track
 * the install session.
 *
 * The state lives in memory only and every path — success, abort, exception, dead session,
 * timeout — leaves the progress states, so the UI indicator can never spin forever.
 *
 * @param currentVersionName the installed version, normally `BuildConfig.VERSION_NAME`.
 * @param clock source of "now", injectable for the install-timeout test.
 */
class UpdateManager(
    private val currentVersionName: String,
    private val releaseSource: ReleaseSource,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** Current updater state for the UI. */
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private var pendingApk: File? = null
    private var installingSince: Long? = null
    private var confirmationShown = false

    init {
        // A cached APK that is not newer than the installed build means the update already happened.
        scope.launch { runCatching { downloader.clearStale(currentVersionName) } }
    }

    /** Asks GitHub for the latest release: [UpdateState.Checking] → Available / UpToDate / Failed. */
    fun check() {
        if (_state.value.isBusy) return
        job?.cancel()
        _state.value = UpdateState.Checking
        job = scope.launch {
            try {
                val release = releaseSource.fetchLatestRelease()
                _state.value = if (isNewer(release.tag, currentVersionName)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = UpdateState.Failed(e.asUpdateError(), null)
            }
        }
    }

    /** Downloads the known release and starts installing it. */
    fun downloadAndInstall() {
        val release = _state.value.currentRelease ?: return
        if (_state.value is UpdateState.Installing) return
        job?.cancel()
        _state.value = UpdateState.Downloading(release, 0f)
        job = scope.launch {
            var downloaded: File? = null
            try {
                downloader.download(release.apk, release.tag).collect { event ->
                    when (event) {
                        is ApkDownloadEvent.Progress ->
                            _state.value = UpdateState.Downloading(release, event.fraction.coerceIn(0f, 1f))
                        is ApkDownloadEvent.Finished -> downloaded = event.file
                    }
                }
                val file = downloaded ?: throw UpdateError.Download("the download produced no file")
                pendingApk = file
                startInstall(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = UpdateState.Failed(e.asUpdateError(), release)
            }
        }
    }

    /** Aborts the download or the install and falls back to [UpdateState.Available]. */
    fun cancel() {
        job?.cancel()
        job = null
        installingSince = null
        _state.value = when (val current = _state.value) {
            is UpdateState.Downloading -> UpdateState.Available(current.release)
            is UpdateState.Installing -> UpdateState.Available(current.release)
            is UpdateState.NeedsInstallPermission -> UpdateState.Available(current.release)
            else -> UpdateState.Idle
        }
    }

    /**
     * Called from `MainActivity.onResume`. Recovers from a dead install session, from an install
     * that got no callback for [INSTALL_TIMEOUT_MS], and continues installing once the permission
     * was granted.
     */
    fun onAppResumed() {
        when (val current = _state.value) {
            is UpdateState.Installing -> when {
                isInstallStale(current.sessionId) -> {
                    installingSince = null
                    _state.value = UpdateState.Available(current.release)
                }
                // Back from the system dialog with no callback: some Android versions send none
                // when the dialog is dismissed. A real self-update kills this process meanwhile.
                confirmationShown -> scope.launch {
                    delay(CONFIRMATION_GRACE_MS)
                    if (_state.value == current) {
                        installingSince = null
                        confirmationShown = false
                        _state.value = UpdateState.Available(current.release)
                    }
                }
            }
            is UpdateState.NeedsInstallPermission -> {
                val file = pendingApk
                if (!installer.hasInstallPermission()) return
                if (file == null) {
                    _state.value = UpdateState.Available(current.release)
                } else {
                    retryInstall(current.release, file)
                }
            }
            else -> Unit
        }
    }

    /** Reports the status that [UpdateInstallReceiver] got from the system installer. */
    fun onInstallResult(result: InstallResult) {
        when (result) {
            InstallResult.AwaitingUserConfirmation -> {
                installingSince = clock()
                confirmationShown = true
            }
            InstallResult.Installed -> {
                installingSince = null
                confirmationShown = false
                pendingApk = null
                job = null
                _state.value = UpdateState.Idle
            }
            InstallResult.Cancelled -> {
                installingSince = null
                confirmationShown = false
                _state.value = _state.value.currentRelease
                    ?.let { UpdateState.Available(it) }
                    ?: UpdateState.Idle
            }
            is InstallResult.Failed -> {
                installingSince = null
                confirmationShown = false
                _state.value = UpdateState.Failed(
                    UpdateError.Install(result.message),
                    _state.value.currentRelease,
                )
            }
        }
    }

    private fun isInstallStale(sessionId: Int): Boolean {
        val startedAt = installingSince
        val timedOut = startedAt == null || clock() - startedAt > INSTALL_TIMEOUT_MS
        return timedOut || !installer.isSessionActive(sessionId)
    }

    private fun retryInstall(release: ReleaseInfo, file: File) {
        job?.cancel()
        job = scope.launch {
            try {
                startInstall(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = UpdateState.Failed(e.asUpdateError(), release)
            }
        }
    }

    private suspend fun startInstall(release: ReleaseInfo, file: File) {
        when (val start = installer.startInstall(file)) {
            InstallStart.NeedsPermission -> _state.value = UpdateState.NeedsInstallPermission(release)
            is InstallStart.Started -> {
                installingSince = clock()
                confirmationShown = false
                _state.value = UpdateState.Installing(release, start.sessionId)
            }
        }
    }

    companion object {
        /** An install that produced no callback for this long is treated as dead. */
        const val INSTALL_TIMEOUT_MS = 2 * 60 * 1000L

        /** How long to wait after returning from the install dialog before giving up on it. */
        const val CONFIRMATION_GRACE_MS = 5_000L
    }
}

private fun Throwable.asUpdateError(): UpdateError =
    this as? UpdateError ?: UpdateError.Unknown(message, this)
