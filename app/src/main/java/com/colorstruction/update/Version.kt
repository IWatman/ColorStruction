package com.colorstruction.update

/**
 * A semantic version `major.minor.patch`.
 *
 * A leading `v` and any pre-release / build suffix (`-beta`, `-rc1`, `+4`) are ignored, so that
 * GitHub tags (`v0.1.0`) and `BuildConfig.VERSION_NAME` (`0.1.0`) can be compared directly.
 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Parses [raw] (`"v0.1.0"`, `"0.1.0"`, `"0.1.0-beta"`, `"1.2"`).
         *
         * @return the version, or `null` when [raw] does not start with a number.
         */
        fun parse(raw: String): Version? {
            var text = raw.trim()
            if (text.startsWith("v", ignoreCase = true)) text = text.substring(1)
            val numeric = text.substringBefore('-').substringBefore('+').trim()
            if (numeric.isEmpty()) return null
            val numbers = IntArray(3)
            numeric.split('.').forEachIndexed { index, part ->
                if (part.isEmpty()) return@forEachIndexed
                val number = part.toIntOrNull() ?: return null
                if (index < numbers.size) numbers[index] = number
            }
            return Version(numbers[0], numbers[1], numbers[2])
        }
    }
}

/**
 * True when [remoteTag] (`"v1.2.0"`) is newer than [currentVersionName] (`"1.1.9"`).
 * Unparsable input is treated as "not newer" so a broken tag can never trigger an update.
 */
fun isNewer(remoteTag: String, currentVersionName: String): Boolean {
    val remote = Version.parse(remoteTag) ?: return false
    val current = Version.parse(currentVersionName) ?: return false
    return remote > current
}
