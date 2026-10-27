package com.colorstruction.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests for [parseRelease]: a pure function over a `releases/latest` payload. */
@RunWith(RobolectricTestRunner::class)
class GitHubReleaseSourceTest {

    private val sample = """
        {
          "tag_name": "v0.2.0",
          "name": "ColorStruction 0.2.0",
          "body": "## What's new\n- In-app updates\n",
          "html_url": "https://github.com/IWatman/ColorStruction/releases/tag/v0.2.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "ColorStruction-0.2.0.apk.sha256",
              "browser_download_url": "https://example.com/app.apk.sha256",
              "size": 64
            },
            {
              "name": "ColorStruction-0.2.0.apk",
              "browser_download_url": "https://example.com/ColorStruction-0.2.0.apk",
              "size": 1234567
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses notes and the first apk asset`() {
        val release = parseRelease(sample)

        assertEquals("v0.2.0", release.tag)
        assertEquals(Version(0, 2, 0), release.version)
        assertEquals("ColorStruction 0.2.0", release.name)
        assertEquals("## What's new\n- In-app updates", release.notes)
        assertEquals(
            "https://github.com/IWatman/ColorStruction/releases/tag/v0.2.0",
            release.htmlUrl,
        )
        assertEquals("ColorStruction-0.2.0.apk", release.apk.name)
        assertEquals("https://example.com/ColorStruction-0.2.0.apk", release.apk.downloadUrl)
        assertEquals(1234567L, release.apk.size)
    }

    @Test
    fun `falls back to the tag when the release has no name and no size`() {
        val release = parseRelease(
            """
            {"tag_name": "v1.0.0", "assets": [
              {"name": "app.apk", "browser_download_url": "https://example.com/app.apk"}
            ]}
            """.trimIndent(),
        )

        assertEquals("v1.0.0", release.name)
        assertEquals("", release.notes)
        assertEquals(0L, release.apk.size)
    }

    @Test
    fun `reports NoApkAsset when only other files are attached`() {
        val json = JSONObject()
            .put("tag_name", "v0.2.0")
            .put(
                "assets",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("name", "source.zip")
                        .put("browser_download_url", "https://example.com/source.zip"),
                ),
            )
            .toString()

        assertThrows(UpdateError.NoApkAsset::class.java) { parseRelease(json) }
    }

    @Test
    fun `reports Parse for broken json or a missing tag`() {
        assertThrows(UpdateError.Parse::class.java) { parseRelease("{ not json") }
        assertThrows(UpdateError.Parse::class.java) {
            parseRelease("""{"tag_name": "", "assets": []}""")
        }
    }
}
