package com.earalarm.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class UpdateFileProvider : FileProvider()

internal class AppUpdater(context: Context) {
    private val app = context.applicationContext
    private val packages = app.packageManager
    private val cache = File(app.cacheDir, "updates")

    private val current: PackageInfo get() = packages.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

    suspend fun check(): UpdateRelease = withContext(Dispatchers.IO) {
        deleteExpiredDownloads()
        val result = open(URL(UpdateRelease.API_URL))
        try {
            check(result.responseCode == 200)
            val json = result.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                input.readChunks { bytes, count, total ->
                    ensureActive()
                    check(total <= MAX_RELEASE_BYTES)
                    output.write(bytes, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            checkNotNull(UpdateRelease.parse(json))
        } finally {
            result.disconnect()
        }
    }

    fun canInstall(): Boolean = packages.canRequestPackageInstalls()

    fun installSettings() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", app.packageName, null))

    fun installer(file: File): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    suspend fun download(release: UpdateRelease, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        check(cache.isDirectory || cache.mkdirs())
        val file = File.createTempFile("earphonealarm-", ".apk", cache)
        var validated = false
        try {
            val connection = openAsset(URL(release.url))
            val hash = MessageDigest.getInstance("SHA-256")
            var lastPercent = -1
            try {
                check(connection.contentLengthLong <= 0 || connection.contentLengthLong == release.size)
                file.outputStream().buffered().use { output ->
                    connection.inputStream.use { input ->
                        val size = input.readChunks { bytes, count, total ->
                            ensureActive()
                            check(total <= release.size)
                            hash.update(bytes, 0, count)
                            output.write(bytes, 0, count)
                            val percent = (total * 100 / release.size).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                        check(size == release.size)
                    }
                }
            } finally {
                connection.disconnect()
            }
            val sha256 = hash.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && !sha256.equals(release.sha256, ignoreCase = true)) {
                throw InvalidUpdate("업데이트 파일이 손상됐어요. 다시 내려받아 주세요.")
            }
            validateApk(file, release)
            ensureActive()
            validated = true
            file
        } finally {
            if (!validated) file.delete()
        }
    }

    private fun validateApk(file: File, release: UpdateRelease) {
        val installed = current
        val update = packages.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        if (update == null || update.packageName != app.packageName ||
            update.versionName != release.version || update.longVersionCode <= installed.longVersionCode
        ) throw InvalidUpdate("이 기기에 설치할 수 있는 새 버전이 아니에요.")

        val installedSigners = installed.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        val updateSigners = update.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        if (installedSigners.isEmpty() || updateSigners != installedSigners) {
            throw InvalidUpdate("설치된 앱과 업데이트 파일의 서명이 달라 업데이트할 수 없어요.")
        }
    }

    private fun deleteExpiredDownloads() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        cache.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun openAsset(initial: URL): HttpsURLConnection {
        var url = initial
        repeat(5) {
            check(url.protocol == "https" && url.host in DOWNLOAD_HOSTS && url.userInfo == null &&
                (url.port == -1 || url.port == 443))
            val connection = open(url)
            try {
                if (connection.responseCode == 200) return connection
                check(connection.responseCode in REDIRECTS)
                url = URL(url, checkNotNull(connection.getHeaderField("Location")))
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
        error("Too many release redirects")
    }

    private fun open(url: URL) = (url.openConnection() as HttpsURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "earphonealarm")
    }

    private inline fun InputStream.readChunks(consume: (ByteArray, Int, Long) -> Unit): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count == -1) return total
            total += count
            consume(buffer, count, total)
        }
    }

    class InvalidUpdate(message: String) : Exception(message)

    private companion object {
        const val MAX_RELEASE_BYTES = 256L * 1024
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    }
}
