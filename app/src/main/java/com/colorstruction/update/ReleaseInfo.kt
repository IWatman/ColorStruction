package com.colorstruction.update

/** An `.apk` asset attached to a GitHub release. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

/** A GitHub release of the app, reduced to what the updater needs. */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val htmlUrl: String,
    val apk: ReleaseAsset,
) {
    /** Parsed [tag]; `null` when the tag is not a version. */
    val version: Version? = Version.parse(tag)
}
