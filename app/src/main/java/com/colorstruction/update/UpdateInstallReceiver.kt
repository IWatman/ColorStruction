package com.colorstruction.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.colorstruction.ColorStructionApp

/**
 * Receives the final status of the [PackageInstaller] session started by
 * [SystemApkInstaller] and forwards it to [UpdateManager], so the UI never keeps a stale
 * "installing" progress indicator.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as? ColorStructionApp)
            ?.container
            ?.updateManager
            ?: return
        manager.onInstallResult(toResult(context, intent))
    }

    private fun toResult(context: Context, intent: Intent): InstallResult {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        return when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> showConfirmation(context, intent)
            PackageInstaller.STATUS_SUCCESS -> InstallResult.Installed
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Cancelled
            else -> InstallResult.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: describe(status),
            )
        }
    }

    /** The system asks us to show its install dialog; the session stays open until it closes. */
    private fun showConfirmation(context: Context, intent: Intent): InstallResult {
        val confirmation = confirmationIntent(intent)
            ?: return InstallResult.Failed("no confirmation intent")
        return try {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmation)
            InstallResult.AwaitingUserConfirmation
        } catch (e: Exception) {
            InstallResult.Failed(e.message ?: "cannot show the install dialog")
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun describe(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "installation cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "installation blocked"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "installation conflict"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "installation incompatible"
        PackageInstaller.STATUS_FAILURE_INVALID -> "installer received an invalid apk"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "not enough storage"
        else -> "installation failed (status $status)"
    }

    companion object {
        /** Session id of the install that produced the broadcast. */
        const val EXTRA_SESSION_ID = "com.colorstruction.update.EXTRA_SESSION_ID"
    }
}
