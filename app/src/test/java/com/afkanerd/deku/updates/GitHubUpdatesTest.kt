package com.afkanerd.deku.updates

import android.content.pm.PackageInfo
import android.content.pm.Signature
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], manifest = Config.NONE)
class GitHubUpdatesTest {
    private fun release(): JSONObject = JSONObject()
        .put("draft", false)
        .put("prerelease", false)
        .put("tag_name", "v0.78.0-4")
        .put("body", "Обновление\n\nVersion code: 9500\nCommit: abc")
        .put("html_url", "${ProjectLinks.repository}/releases/tag/v0.78.0-4")
        .put(
            "assets",
            JSONArray().put(
                JSONObject()
                    .put("name", "Messages-v0.78.0-4.apk")
                    .put("size", 14_000)
                    .put(
                        "browser_download_url",
                        "${ProjectLinks.repository}/releases/download/v0.78.0-4/" +
                            "Messages-v0.78.0-4.apk",
                    )
                    .put("digest", "sha256:${"a".repeat(64)}")
            ),
        )

    @Test
    fun `release requires stable Messages apk and numeric build code`() {
        val result = GitHubUpdates.parseRelease(release())
        assertEquals(9500L, result.versionCode)
        assertEquals("0.78.0-4", result.versionName)
        assertEquals("a".repeat(64), result.digest)

        val invalidReleases = listOf<(JSONObject) -> Unit>(
            { it.put("prerelease", true) },
            { it.put("body", "Version code: nope") },
            { it.put("html_url", "https://example.org/release") },
            {
                it.getJSONArray("assets").getJSONObject(0)
                    .put("name", "another-app.apk")
            },
            {
                it.getJSONArray("assets").getJSONObject(0)
                    .put("browser_download_url", "https://example.org/update.apk")
            },
            { it.getJSONArray("assets").getJSONObject(0).put("size", 0) },
            {
                it.getJSONArray("assets").getJSONObject(0)
                    .put("digest", "sha256:invalid")
            },
        )
        invalidReleases.forEach { mutate ->
            assertTrue(runCatching { GitHubUpdates.parseRelease(release().also(mutate)) }.isFailure)
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(
        code: Int,
        signature: String = "aabb",
        packageName: String = "com.afkanerd.deku",
    ) = PackageInfo().apply {
        this.packageName = packageName
        versionCode = code
        signatures = arrayOf(Signature(signature))
    }

    @Test
    fun `downloaded apk must match package signature and announced newer version`() {
        GitHubUpdates.validateIdentity(packageInfo(100), packageInfo(101), 101)

        listOf(
            packageInfo(100),
            packageInfo(99),
            packageInfo(101, signature = "ccdd"),
            packageInfo(101, packageName = "another.app"),
            packageInfo(102),
        ).forEach { candidate ->
            assertTrue(
                runCatching {
                    GitHubUpdates.validateIdentity(packageInfo(100), candidate, 101)
                }.isFailure
            )
        }
    }
}
