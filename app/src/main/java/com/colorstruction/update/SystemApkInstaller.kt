package com.colorstruction.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** [ApkInstaller] backed by a real [PackageInstaller] session. */
class SystemApkInstaller(private val context: Context) : ApkInstaller {

    private val installer: PackageInstaller get() = context.packageManager.packageInstaller

    override fun hasInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun isSessionActive(sessionId: Int): Boolean {
        val info = try {
            installer.getSessionInfo(sessionId)
        } catch (e: Exception) {
            // NameNotFoundException: the session is gone (finished, failed or abandoned).
            null
        }
        return info != null && info.isActive
    }

    override suspend fun startInstall(file: File): InstallStart = withContext(Dispatchers.IO) {
        if (!hasInstallPermission()) return@withContext InstallStart.NeedsPermission
        val sessionId = try {
            installer.createSession(sessionParams())
        } catch (e: Exception) {
            throw UpdateError.Install(e.message, e)
        }
        val session = try {
            installer.openSession(sessionId)
        } catch (e: Exception) {
            throw UpdateError.Install(e.message, e)
        }
        try {
            file.inputStream().use { input ->
                session.openWrite(APK_NAME, 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(confirmationIntent(sessionId).intentSender)
        } catch (e: CancellationException) {
            runCatching { session.abandon() }
            throw e
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw UpdateError.Install(e.message, e)
        } finally {
            runCatching { session.close() }
        }
        InstallStart.Started(sessionId)
    }

    private fun sessionParams(): PackageInstaller.SessionParams =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }

    private fun confirmationIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
            .putExtra(UpdateInstallReceiver.EXTRA_SESSION_ID, sessionId)
        // The system installer fills the status extras into the intent, so it must stay mutable.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    private companion object {
        const val APK_NAME = "base.apk"
    }
}
