package com.earalarm.app.update

import org.json.JSONObject

internal data class UpdateRelease(
    val version: String,
    val url: String,
    val size: Long,
    val sha256: String?,
) {
    fun isNewerThan(installed: String): Boolean {
        val current = versionParts(installed) ?: return false
        val candidate = versionParts(version) ?: return false
        val firstDifference = candidate.zip(current).firstOrNull { (new, old) -> new != old }
        return firstDifference != null && firstDifference.first > firstDifference.second
    }

    companion object {
        const val API_URL = "https://api.github.com/repos/ggumtak/earphonealarm/releases/latest"
        private const val RELEASES_URL = "https://github.com/ggumtak/earphonealarm/releases/download"
        private const val APK_NAME = "earphonealarm.apk"
        const val MAX_APK_BYTES = 64L * 1024 * 1024
        private val versionPattern = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
        private val digestPattern = Regex("sha256:([0-9a-fA-F]{64})")

        fun parse(json: String): UpdateRelease? {
            val release = runCatching { JSONObject(json) }.getOrNull() ?: return null
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val tag = release.optString("tag_name")
            val version = tag.removePrefix("v")
            if (tag == version || versionParts(version) == null) return null
            val expectedUrl = "$RELEASES_URL/$tag/$APK_NAME"
            val assets = release.optJSONArray("assets") ?: return null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != APK_NAME || asset.optString("browser_download_url") != expectedUrl) continue
                val size = asset.optLong("size")
                if (size !in 1..MAX_APK_BYTES) return null
                val digest = if (asset.isNull("digest")) null
                    else digestPattern.matchEntire(asset.optString("digest"))?.groupValues?.get(1) ?: return null
                return UpdateRelease(version, expectedUrl, size, digest)
            }
            return null
        }

        private fun versionParts(version: String): List<Int>? {
            val (_, major, minor, patch) = versionPattern.matchEntire(version)?.groupValues ?: return null
            return listOf(major, minor, patch.ifEmpty { "0" }).map { it.toIntOrNull() ?: return null }
        }
    }
}
