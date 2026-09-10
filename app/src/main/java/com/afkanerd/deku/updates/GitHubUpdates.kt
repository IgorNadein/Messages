package com.afkanerd.deku.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.afkanerd.deku.DefaultSMS.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ProjectLinks {
    private val repositorySlug = BuildConfig.GITHUB_REPOSITORY
        .takeIf { it.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) }
        ?: "IgorNadein/Messages"

    val repository = "https://github.com/$repositorySlug"
    val releases = "$repository/releases/latest"
    val apiLatestRelease = "https://api.github.com/repos/$repositorySlug/releases/latest"
}

data class GitHubRelease(
    val versionName: String,
    val versionCode: Long,
    val pageUrl: String,
    val apkUrl: String,
    val size: Long,
    val digest: String?,
)

data class UpdateState(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val release: GitHubRelease? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val apkPath: String? = null,
    val error: String? = null,
)

object GitHubUpdates {
    private const val MAX_APK_SIZE = 250L * 1024 * 1024
    private const val MAX_API_RESPONSE_SIZE = 2 * 1024 * 1024
    private const val ASSET_PREFIX = "Messages-"

    fun currentVersion(context: Context): Pair<String, Long> =
        context.packageManager.getPackageInfo(context.packageName, 0).let {
            it.versionName.orEmpty() to versionCode(it)
        }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()

    fun parseRelease(json: JSONObject): GitHubRelease {
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease")) {
            "Релиз ещё не опубликован"
        }
        val tag = json.getString("tag_name")
        val versionName = tag.removePrefix("v").removePrefix("V")
        require(versionName.isNotBlank()) { "В релизе не указана версия" }

        val releaseCode = Regex("(?im)^Version code:\\s*(\\d+)\\s*$")
            .find(json.optString("body"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        require(releaseCode != null && releaseCode in 1..Int.MAX_VALUE.toLong()) {
            "В релизе не указан корректный номер сборки"
        }

        val pageUrl = json.getString("html_url")
        require(pageUrl.startsWith("${ProjectLinks.repository}/releases/tag/")) {
            "Неверная страница релиза"
        }
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length())
            .map(assets::getJSONObject)
            .singleOrNull {
                val name = it.optString("name")
                name.startsWith(ASSET_PREFIX) && name.endsWith(".apk", ignoreCase = true)
            }
            ?: error("В релизе не найден единственный APK приложения")
        val apkUrl = apk.getString("browser_download_url")
        require(apkUrl.startsWith("${ProjectLinks.repository}/releases/download/")) {
            "Неверный адрес APK"
        }
        val size = apk.getLong("size")
        require(size in 1..MAX_APK_SIZE) { "Некорректный размер APK" }
        val digest = apk.optString("digest")
            .takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
        require(digest == null || digest.matches(Regex("[a-fA-F0-9]{64}"))) {
            "Некорректная контрольная сумма"
        }
        return GitHubRelease(versionName, releaseCode, pageUrl, apkUrl, size, digest)
    }

    suspend fun latest(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = connect(ProjectLinks.apiLatestRelease, "application/vnd.github+json")
        try {
            when(val responseCode = connection.responseCode) {
                404 -> error("Первый выпуск ещё готовится")
                403, 429 -> error("GitHub временно ограничил запросы. Попробуйте позже")
                !in 200..299 -> error("GitHub недоступен (HTTP $responseCode)")
            }
            val response = connection.inputStream.use {
                it.readBytesLimited(MAX_API_RESPONSE_SIZE)
            }
            parseRelease(JSONObject(response.toString(Charsets.UTF_8)))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        context: Context,
        release: GitHubRelease,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "Messages-${release.versionCode}.apk")
        val partial = File(directory, "Messages-${release.versionCode}.part")
        val connection = connect(release.apkUrl, "application/octet-stream")
        try {
            require(connection.responseCode in 200..299) {
                "Не удалось скачать APK (HTTP ${connection.responseCode})"
            }
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val length = input.read(buffer)
                        if(length < 0) break
                        total += length
                        require(total <= release.size && total <= MAX_APK_SIZE) {
                            "Размер скачанного APK не совпадает с релизом"
                        }
                        hash.update(buffer, 0, length)
                        output.write(buffer, 0, length)
                        val percent = (total * 100 / release.size).toInt()
                        if(percent != lastPercent) {
                            onProgress(percent / 100f)
                            lastPercent = percent
                        }
                    }
                }
            }
            require(total == release.size) { "APK скачан не полностью. Повторите загрузку" }
            val actualHash = hash.digest().toHex()
            require(release.digest == null || actualHash.equals(release.digest, ignoreCase = true)) {
                "Контрольная сумма APK не совпадает"
            }
            validateApk(context, partial, release.versionCode)
            if(target.exists()) target.delete()
            require(partial.renameTo(target)) { "Не удалось сохранить обновление" }
            directory.listFiles()?.filter { it != target }?.forEach(File::delete)
            target
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    fun validateApk(context: Context, file: File, expectedCode: Long? = null) {
        val flags = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = context.packageManager.getPackageArchiveInfo(file.path, flags)
            ?: error("Файл не является APK")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        validateIdentity(installed, candidate, expectedCode)
    }

    @Suppress("DEPRECATION")
    fun validateIdentity(installed: PackageInfo, candidate: PackageInfo, expectedCode: Long? = null) {
        require(candidate.packageName == installed.packageName) {
            "APK относится к другому приложению"
        }
        require(versionCode(candidate) > versionCode(installed)) {
            "Эта версия уже установлена или устарела"
        }
        require(expectedCode == null || versionCode(candidate) == expectedCode) {
            "Версия APK не совпадает с релизом"
        }
        fun signers(info: PackageInfo): Set<String> {
            val signatures = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            }
            return signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .toHex()
            }.toSet()
        }
        val installedSigners = signers(installed)
        require(installedSigners.isNotEmpty() && installedSigners == signers(candidate)) {
            "Подпись APK отличается от установленного приложения"
        }
    }

    /** Returns false when Android must first allow installs from this application. */
    fun install(context: Context, file: File): Boolean {
        validateApk(context, file)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    fun openRelease(context: Context, release: GitHubRelease? = null) {
        val address = release?.pageUrl ?: ProjectLinks.releases
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(address)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun connect(address: String, accept: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Messages-Android-Updater")
        }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while(true) {
            val length = read(buffer)
            if(length < 0) break
            require(output.size() + length <= limit) { "Ответ GitHub слишком большой" }
            output.write(buffer, 0, length)
        }
        return output.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

class AppUpdates(private val context: Context, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private var downloadJob: Job? = null

    fun check() {
        if(mutableState.value.checking || mutableState.value.downloading) return
        mutableState.update { it.copy(checking = true, error = null) }
        scope.launch {
            try {
                val release = GitHubUpdates.latest()
                mutableState.update { previous ->
                    previous.copy(
                        checking = false,
                        checked = true,
                        release = release,
                        apkPath = previous.apkPath?.takeIf { path ->
                            previous.release?.versionCode == release.versionCode && File(path).exists()
                        },
                    )
                }
            } catch(cancelled: CancellationException) {
                throw cancelled
            } catch(error: Exception) {
                mutableState.update {
                    it.copy(
                        checking = false,
                        checked = true,
                        error = error.message ?: "Проверьте подключение к интернету",
                    )
                }
            }
        }
    }

    fun download() {
        val release = mutableState.value.release ?: return
        if(mutableState.value.downloading) return
        mutableState.update { it.copy(downloading = true, progress = 0f, error = null) }
        downloadJob = scope.launch {
            try {
                val file = GitHubUpdates.download(context, release) { progress ->
                    mutableState.update { it.copy(progress = progress) }
                }
                mutableState.update { it.copy(apkPath = file.path) }
            } catch(cancelled: CancellationException) {
                throw cancelled
            } catch(error: Exception) {
                mutableState.update {
                    it.copy(error = error.message ?: "Не удалось скачать APK")
                }
            } finally {
                mutableState.update { it.copy(downloading = false) }
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
    }
}
