package com.earalarm.app.update

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class UpdateReleaseTest {
    private val url = "https://github.com/ggumtak/earphonealarm/releases/download/v1.10/earphonealarm.apk"

    @Test fun acceptsOnlyANewerVersionNumerically() {
        val release = requireNotNull(UpdateRelease.parse(json()))
        assertEquals("1.10", release.version)
        assertTrue(release.isNewerThan("1.9.9"))
        assertFalse(release.isNewerThan("1.10.0"))
        assertFalse(release.isNewerThan("1.10.1"))
        assertFalse(release.isNewerThan("2.0"))
    }

    @Test fun rejectsUnexpectedAssetsAndOtherRepos() {
        assertNull(UpdateRelease.parse(json(apkUrl = url.replace("ggumtak", "elsewhere"))))
        assertNull(UpdateRelease.parse(json().replace("v1.10", "v1.10-beta")))
        assertNull(UpdateRelease.parse(json(digest = "\"sha256:invalid\"")))
        assertNull(UpdateRelease.parse(json(size = UpdateRelease.MAX_APK_BYTES + 1)))
        assertNull(UpdateRelease.parse(json().replace("\"draft\":false", "\"draft\":true")))
    }

    @Test fun readsOptionalAssetSha256() {
        val digest = "ab".repeat(32)
        assertEquals(digest, UpdateRelease.parse(json(digest = "\"sha256:$digest\""))?.sha256)
        assertNull(UpdateRelease.parse(json())?.sha256)
    }

    private fun json(apkUrl: String = url, size: Long = 500, digest: String = "null") =
        """{"tag_name":"v1.10","draft":false,"assets":[
            {"name":"earphonealarm.apk","browser_download_url":"$apkUrl","size":$size,"digest":$digest}
        ]}"""
}
