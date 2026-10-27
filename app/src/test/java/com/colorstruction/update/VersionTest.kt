package com.colorstruction.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [Version] parsing and comparison against `BuildConfig.VERSION_NAME`. */
class VersionTest {

    @Test
    fun `parses tags with a leading v and plain versions`() {
        assertEquals(Version(0, 1, 0), Version.parse("v0.1.0"))
        assertEquals(Version(0, 1, 0), Version.parse("0.1.0"))
        assertEquals(Version(1, 2, 3), Version.parse(" V1.2.3 "))
        assertEquals(Version(2, 0, 0), Version.parse("2.0"))
        assertEquals(Version(3, 0, 0), Version.parse("v3"))
    }

    @Test
    fun `ignores pre-release and build suffixes`() {
        assertEquals(Version(0, 1, 0), Version.parse("0.1.0-beta"))
        assertEquals(Version(0, 2, 0), Version.parse("v0.2.0-rc1.2"))
        assertEquals(Version(1, 0, 0), Version.parse("1.0.0+42"))
    }

    @Test
    fun `rejects values that do not start with a number`() {
        assertNull(Version.parse(""))
        assertNull(Version.parse("v"))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse("vNext.1.0"))
    }

    @Test
    fun `compares numerically part by part`() {
        assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
        assertEquals(0, Version.parse("1.2.3")!!.compareTo(Version.parse("v1.2.3")!!))
        assertEquals("1.2.3", Version(1, 2, 3).toString())
    }

    @Test
    fun `isNewer compares the release tag with the installed version`() {
        assertTrue(isNewer("v0.2.0", "0.1.0"))
        assertTrue(isNewer("v1.0.0", "0.99.99"))
        assertFalse(isNewer("v0.1.0", "0.1.0"))
        assertFalse(isNewer("0.0.9", "0.1.0"))
        assertFalse(isNewer("0.1.0-beta", "0.1.0"))
        assertFalse(isNewer("nightly", "0.1.0"))
        assertFalse(isNewer("v0.2.0", "not-a-version"))
    }
}
