package com.colorstruction.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Tests for the download/install half of [UpdateManager]: the state must never stay stuck. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerInstallTest {

    private val source = FakeReleaseSource()
    private val installer = FakeInstaller()
    private lateinit var apk: File
    private lateinit var downloader: FakeDownloader
    private var now = 0L

    @Before
    fun setUp() {
        apk = File.createTempFile("ColorStruction-update", ".apk").apply { writeBytes(ByteArray(16)) }
        downloader = FakeDownloader(apk)
        source.response = { FAKE_RELEASE }
    }

    private fun TestScope.build(): UpdateManager = UpdateManager(
        currentVersionName = "0.1.0",
        releaseSource = source,
        downloader = downloader,
        installer = installer,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        clock = { now },
    )

    private fun TestScope.record(manager: UpdateManager): List<UpdateState> {
        val states = mutableListOf<UpdateState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.state.collect { states += it }
        }
        return states
    }

    private fun TestScope.install(manager: UpdateManager) {
        manager.check()
        advanceUntilIdle()
        manager.downloadAndInstall()
        advanceUntilIdle()
    }

    @Test
    fun `download progress is reported and ends in installing`() = runTest {
        val manager = build()
        val states = record(manager)

        manager.check()
        advanceUntilIdle()
        manager.downloadAndInstall()
        assertEquals(UpdateState.Downloading(FAKE_RELEASE, 0f), manager.state.value)

        advanceUntilIdle()

        assertTrue(states.any { it is UpdateState.Downloading && it.progress > 0f })
        assertEquals(UpdateState.Installing(FAKE_RELEASE, FakeInstaller.SESSION_ID), manager.state.value)
        assertEquals("v0.2.0", downloader.lastTag)
    }

    @Test
    fun `user cancelling the install dialog returns to available without an error`() = runTest {
        val manager = build()
        install(manager)
        manager.onInstallResult(InstallResult.Cancelled)

        assertEquals(UpdateState.Available(FAKE_RELEASE), manager.state.value)
    }

    @Test
    fun `dismissed install dialog without callback does not leave installing`() = runTest {
        val manager = build()
        install(manager)
        manager.onInstallResult(InstallResult.AwaitingUserConfirmation)
        installer.sessionActive = true // the session lingers, no callback ever arrives

        manager.onAppResumed()
        assertTrue(manager.state.value is UpdateState.Installing)
        advanceUntilIdle()

        assertEquals(UpdateState.Available(FAKE_RELEASE), manager.state.value)
    }

    @Test
    fun `install success resets to idle`() = runTest {
        val manager = build()
        install(manager)

        manager.onInstallResult(InstallResult.Installed)

        assertEquals(UpdateState.Idle, manager.state.value)
    }

    @Test
    fun `installer abort leaves the installing state and keeps the release`() = runTest {
        val manager = build()
        install(manager)

        manager.onInstallResult(InstallResult.Failed("installation cancelled"))

        val state = manager.state.value as UpdateState.Failed
        assertTrue(state.reason is UpdateError.Install)
        assertEquals(FAKE_RELEASE, state.release)
    }

    @Test
    fun `signature conflict becomes a signature mismatch error`() = runTest {
        val manager = build()
        install(manager)

        manager.onInstallResult(
            InstallResult.Failed(
                "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.colorstruction " +
                    "signatures do not match newer version; ignoring!",
            ),
        )

        val state = manager.state.value as UpdateState.Failed
        assertEquals(UpdateError.SignatureMismatch, state.reason)
        assertEquals(FAKE_RELEASE, state.release)
    }

    @Test
    fun `resume with a dead session falls back to available`() = runTest {
        installer.sessionActive = false
        val manager = build()
        install(manager)

        manager.onAppResumed()

        assertEquals(UpdateState.Available(FAKE_RELEASE), manager.state.value)
    }

    @Test
    fun `resume after the install timeout falls back to available`() = runTest {
        val manager = build()
        install(manager)

        now += UpdateManager.INSTALL_TIMEOUT_MS + 1
        manager.onAppResumed()

        assertEquals(UpdateState.Available(FAKE_RELEASE), manager.state.value)
    }

    @Test
    fun `resume keeps installing while the session is alive`() = runTest {
        val manager = build()
        install(manager)

        now += 1_000
        manager.onAppResumed()

        assertEquals(UpdateState.Installing(FAKE_RELEASE, FakeInstaller.SESSION_ID), manager.state.value)
    }

    @Test
    fun `granting the install permission resumes the install`() = runTest {
        installer.permissionGranted = false
        val manager = build()
        install(manager)
        assertEquals(UpdateState.NeedsInstallPermission(FAKE_RELEASE), manager.state.value)

        installer.permissionGranted = true
        manager.onAppResumed()
        advanceUntilIdle()

        assertEquals(UpdateState.Installing(FAKE_RELEASE, FakeInstaller.SESSION_ID), manager.state.value)
        assertEquals(2, installer.starts)
    }

    @Test
    fun `download exception becomes a failure`() = runTest {
        downloader.failure = UpdateError.Download("boom")
        val manager = build()
        install(manager)

        val state = manager.state.value as UpdateState.Failed
        assertTrue(state.reason is UpdateError.Download)
        assertEquals(FAKE_RELEASE, state.release)
    }

    @Test
    fun `install exception becomes a failure`() = runTest {
        installer.startFailure = UpdateError.Install("no session")
        val manager = build()
        install(manager)

        val state = manager.state.value as UpdateState.Failed
        assertTrue(state.reason is UpdateError.Install)
        assertEquals(FAKE_RELEASE, state.release)
    }
}
