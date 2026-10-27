package com.colorstruction.ui.settings

/**
 * UI language of the app.
 *
 * @param tag the BCP-47 language tag, empty for [SYSTEM] (follow the device locales).
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en"),
}

/**
 * Maps the application locales as returned by `LocaleListCompat.toLanguageTags()` to the
 * closest supported [AppLanguage]: the first matching tag wins, unknown tags fall back to
 * [AppLanguage.SYSTEM].
 */
fun appLanguageFromTags(tags: String): AppLanguage {
    val tag = tags.trim().lowercase()
    return when {
        tag.isEmpty() -> AppLanguage.SYSTEM
        tag.startsWith("ru") -> AppLanguage.RUSSIAN
        tag.startsWith("en") -> AppLanguage.ENGLISH
        else -> AppLanguage.SYSTEM
    }
}
