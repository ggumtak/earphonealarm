package com.earalarm.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal object ReleaseChecker {
    private const val MAX_RELEASE_BYTES = 256 * 1024

    suspend fun check(): UpdateRelease = withContext(Dispatchers.IO) {
        val connection = (URL(UpdateRelease.API_URL).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "earphonealarm")
        }
        try {
            check(connection.responseCode == 200)
            val json = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    check(output.size() + count <= MAX_RELEASE_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            checkNotNull(UpdateRelease.parse(json))
        } finally {
            connection.disconnect()
        }
    }
}
