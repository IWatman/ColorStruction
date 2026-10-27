package com.colorstruction.update

/** What [UpdateInstallReceiver] reported about a committed install session. */
sealed interface InstallResult {
    /** The system needs the user to confirm the installation; the session is still alive. */
    data object AwaitingUserConfirmation : InstallResult

    /** The new version was installed. */
    data object Installed : InstallResult

    /** The user cancelled the system install dialog; not an error. */
    data object Cancelled : InstallResult

    /** Blocked or failed; [message] is the system's explanation. */
    data class Failed(val message: String) : InstallResult
}
