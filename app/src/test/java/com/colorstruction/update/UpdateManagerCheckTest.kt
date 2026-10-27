package com.colorstruction.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Tests for the "check for updates" half of [UpdateManager] with fake collaborators. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerCheckTest {

    private val source = FakeReleaseSource()
    private val installer = FakeInstaller()
    private lateinit var apk: File
    private lateinit var downloader: FakeDownloader

    @Before
    fun setUp() {
        apk = File.createTempFile("ColorStruction-update", ".apk").apply { writeBytes(ByteArray(16)) }
        downloader = FakeDownloader(apk)
    }

    private fun TestScope.build(): UpdateManager = UpdateManager(
        currentVersionName = "0.1.0",
        releaseSource = source,
        downloader = downloader,
        installer = installer,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `newer release becomes available`() = runTest {
        source.response = { FAKE_RELEASE }
        val manager = build()

        manager.check()
        assertEquals(UpdateState.Checking, manager.state.value)

        advanceUntilIdle()
        assertEquals(UpdateState.Available(FAKE_RELEASE), manager.state.value)
    }

    @Test
    fun `the installed version is up to date`() = runTest {
        source.response = { FAKE_RELEASE.copy(tag = "v0.1.0") }
        val manager = build()

        manager.check()
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, manager.state.value)
    }

    @Test
    fun `rate limit becomes a failure without a release`() = runTest {
        source.response = { throw UpdateError.RateLimited(60) }
        val manager = build()

        manager.check()
        advanceUntilIdle()

        val state = manager.state.value as UpdateState.Failed
        assertTrue(state.reason is UpdateError.RateLimited)
        assertNull(state.release)
    }

    @Test
    fun `an unexpected exception becomes a failure`() = runTest {
        source.response = { throw IllegalStateException("boom") }
        val manager = build()

        manager.check()
        advanceUntilIdle()

        val state = manager.state.value as UpdateState.Failed
        assertTrue(state.reason is UpdateError.Unknown)
    }

    @Test
    fun `stale cached apks are cleared on startup`() = runTest {
        val manager = build()

        advanceUntilIdle()

        assertEquals("0.1.0", downloader.staleVersion)
        assertEquals(UpdateState.Idle, manager.state.value)
    }

    @Test
    fun `cancel while installing returns to available`() = runTest {
        source.response = { FAKE_RELEASE }
        val manager = build()
        manager.check()
        advanceUntilIdle()

        manager.downloadAndInstall()
        advanceUntilIdle()
        assertEquals(UpdateState.Installing(FAKE_RELEASE, FakeInstaller.SESSION_ID), manager.state.value)

        manager.cancel()
        assertEquals(UpdateState.Available(FAKE_RELEASE), manager.state.value)
    }
}
