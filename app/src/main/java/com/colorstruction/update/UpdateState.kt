package com.colorstruction.update

/**
 * In-memory state of the in-app updater. It is never persisted: a fresh process always starts
 * at [Idle], so a killed app can never come back stuck in a progress state.
 */
sealed interface UpdateState {
    /** Nothing is going on. */
    data object Idle : UpdateState

    /** Asking GitHub for the latest release. */
    data object Checking : UpdateState

    /** The installed version is the latest one. */
    data object UpToDate : UpdateState

    /** A newer release is available and can be downloaded. */
    data class Available(val release: ReleaseInfo) : UpdateState

    /** The APK is being downloaded; [progress] is in `0..1`. */
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState

    /** An install session was committed; waiting for [UpdateInstallReceiver]. */
    data class Installing(val release: ReleaseInfo, val sessionId: Int) : UpdateState

    /** The APK is ready but the user must allow installing from this app. */
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdateState

    /** Something went wrong; [release] is kept when a retry is possible. */
    data class Failed(val reason: UpdateError, val release: ReleaseInfo?) : UpdateState
}

/** The release the updater is currently dealing with, if any. */
val UpdateState.currentRelease: ReleaseInfo?
    get() = when (this) {
        is UpdateState.Available -> this.release
        is UpdateState.Downloading -> this.release
        is UpdateState.Installing -> this.release
        is UpdateState.NeedsInstallPermission -> this.release
        is UpdateState.Failed -> this.release
        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> null
    }

/** True while a download or an install is in flight. */
val UpdateState.isBusy: Boolean
    get() = this is UpdateState.Checking ||
        this is UpdateState.Downloading ||
        this is UpdateState.Installing
