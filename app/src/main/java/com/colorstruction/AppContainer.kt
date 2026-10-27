package com.colorstruction

import android.content.Context
import com.colorstruction.data.ColorStructionDatabase
import com.colorstruction.data.FavoriteRepository
import com.colorstruction.data.PaintRepository
import com.colorstruction.data.RoomFavoriteRepository
import com.colorstruction.data.RoomPaintRepository
import com.colorstruction.data.SettingsRepository
import com.colorstruction.update.GitHubReleaseSource
import com.colorstruction.update.HttpApkDownloader
import com.colorstruction.update.SystemApkInstaller
import com.colorstruction.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container: app-scoped singletons, created once in
 * [ColorStructionApp.onCreate].
 *
 * @param context any context; the application context is used for storage.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Long-lived scope for fire-and-forget app work (first-launch setup, update checks). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The Room database behind [paintRepository]. */
    val database: ColorStructionDatabase = ColorStructionDatabase.create(appContext)

    /** Palettes and paints. */
    val paintRepository: PaintRepository = RoomPaintRepository(database)

    /** Saved favorite recipes (snapshots, independent of later paint edits). */
    val favoriteRepository: FavoriteRepository = RoomFavoriteRepository(database)

    /** User settings stored in DataStore. */
    val settingsRepository: SettingsRepository = SettingsRepository.fromContext(appContext)

    /** GitHub Releases updater: version check, APK download and the install session. */
    val updateManager: UpdateManager = UpdateManager(
        currentVersionName = BuildConfig.VERSION_NAME,
        releaseSource = GitHubReleaseSource(),
        downloader = HttpApkDownloader(appContext.cacheDir),
        installer = SystemApkInstaller(appContext),
        scope = applicationScope,
    )
}
