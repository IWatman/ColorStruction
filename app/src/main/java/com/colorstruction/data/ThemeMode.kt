package com.colorstruction.data

/** How the app chooses between light and dark color schemes. */
enum class ThemeMode {
    /** Follow the system dark-mode setting. */
    SYSTEM,

    /** Always use the light scheme. */
    LIGHT,

    /** Always use the dark scheme. */
    DARK,
}
