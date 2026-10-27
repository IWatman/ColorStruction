package com.colorstruction.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [appLanguageFromTags]. */
class AppLanguageTest {

    @Test
    fun `empty tags mean follow the system`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTags(""))
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTags("   "))
    }

    @Test
    fun `maps russian and english tags`() {
        assertEquals(AppLanguage.RUSSIAN, appLanguageFromTags("ru"))
        assertEquals(AppLanguage.RUSSIAN, appLanguageFromTags("ru-RU"))
        assertEquals(AppLanguage.ENGLISH, appLanguageFromTags("en"))
        assertEquals(AppLanguage.ENGLISH, appLanguageFromTags("en-US"))
    }

    @Test
    fun `is case insensitive and ignores surrounding spaces`() {
        assertEquals(AppLanguage.RUSSIAN, appLanguageFromTags(" RU-ru "))
        assertEquals(AppLanguage.ENGLISH, appLanguageFromTags("EN"))
    }

    @Test
    fun `unknown tags fall back to the system`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTags("de-DE"))
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTags("x"))
    }

    @Test
    fun `tags round-trip through the enum values`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromTags(AppLanguage.SYSTEM.tag))
        assertEquals(AppLanguage.RUSSIAN, appLanguageFromTags(AppLanguage.RUSSIAN.tag))
        assertEquals(AppLanguage.ENGLISH, appLanguageFromTags(AppLanguage.ENGLISH.tag))
    }
}
