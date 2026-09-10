package com.afkanerd.deku.attachments.cloud

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class CloudObjectReference(
    val provider: CloudProvider,
    /** Public capability URL, or a provider public key resolvable without sender credentials. */
    val locator: String,
    /** Provider-private deletion locator. It is persisted only on the sender. */
    val deleteLocator: String? = null,
)

interface CloudObjectStore {
    suspend fun validate()
    suspend fun upload(file: File, objectName: String): CloudObjectReference
    suspend fun delete(deleteLocator: String)
}

object CloudObjectStoreFactory {
    fun create(credentials: CloudStorageCredentials): CloudObjectStore = when(credentials.profile.provider) {
        CloudProvider.SELF_HOSTED -> SelfHostedObjectStore(credentials)
        CloudProvider.YANDEX_DISK -> YandexDiskObjectStore(credentials)
        CloudProvider.GOOGLE_DRIVE -> GoogleDriveObjectStore(credentials)
    }
}

class CloudObjectDownloader {
    suspend fun download(reference: CloudObjectReference, destination: File, maxBytes: Long) =
        withContext(Dispatchers.IO) {
            require(maxBytes > 0)
            val resolved = when(reference.provider) {
                CloudProvider.YANDEX_DISK -> resolveYandexDownload(reference.locator)
                CloudProvider.SELF_HOSTED, CloudProvider.GOOGLE_DRIVE -> reference.locator
            }
            requireHttps(resolved)
            val connection = open(resolved, "GET")
            try {
                checkSuccess(connection)
                val declared = connection.contentLengthLong
                require(declared < 0 || declared <= maxBytes) { "Cloud object is too large" }
                var total = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while(true) {
                            val read = input.read(buffer)
                            if(read < 0) break
                            total += read
                            require(total <= maxBytes) { "Cloud object is too large" }
                            output.write(buffer, 0, read)
                        }
                        output.fd.sync()
                    }
                }
            } catch(error: Exception) {
                destination.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }

    private fun resolveYandexDownload(publicKey: String): String {
        val endpoint = Uri.parse(YANDEX_API + "/public/resources/download").buildUpon()
            .appendQueryParameter("public_key", publicKey)
            .build().toString()
        val connection = open(endpoint, "GET")
        return try {
            checkSuccess(connection)
            JSONObject(readResponse(connection)).getString("href").also(::requireHttps)
        } finally {
            connection.disconnect()
        }
    }
}

private class SelfHostedObjectStore(
    private val credentials: CloudStorageCredentials,
) : CloudObjectStore {
    override suspend fun validate() = withContext(Dispatchers.IO) {
        val connection = open(
            credentials.profile.endpoint.trimEnd('/') + "/v1/capabilities",
            "GET",
            credentials.accessToken,
        )
        try { checkSuccess(connection) } finally { connection.disconnect() }
    }

    override suspend fun upload(file: File, objectName: String): CloudObjectReference =
        withContext(Dispatchers.IO) {
            val base = credentials.profile.endpoint.trimEnd('/')
            requireHttps(base)
            val uploadUrl = "$base/v1/objects/${Uri.encode(credentials.profile.folder)}/${Uri.encode(objectName)}"
            val connection = open(uploadUrl, "PUT", credentials.accessToken)
            try {
                uploadFile(connection, file, "application/octet-stream")
                checkSuccess(connection)
                val response = readResponse(connection, allowEmpty = true)
                val locator = connection.getHeaderField("Location")
                    ?: response.takeIf(String::isNotBlank)?.let {
                        JSONObject(it).optString("downloadUrl").takeIf(String::isNotBlank)
                    }
                    ?: error("Storage server did not return a download URL")
                requireHttps(locator)
                val deleteLocator = response.takeIf(String::isNotBlank)?.let {
                    JSONObject(it).optString("deleteUrl").takeIf(String::isNotBlank)
                } ?: uploadUrl
                requireHttps(deleteLocator)
                CloudObjectReference(CloudProvider.SELF_HOSTED, locator, deleteLocator)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun delete(deleteLocator: String) = withContext(Dispatchers.IO) {
        val connection = open(deleteLocator, "DELETE", credentials.accessToken)
        try { checkSuccess(connection) } finally { connection.disconnect() }
    }
}

private class YandexDiskObjectStore(
    private val credentials: CloudStorageCredentials,
) : CloudObjectStore {
    override suspend fun validate() = withContext(Dispatchers.IO) {
        jsonRequest("$YANDEX_API?fields=user", "GET", credentials.accessToken)
        Unit
    }

    override suspend fun upload(file: File, objectName: String): CloudObjectReference =
        withContext(Dispatchers.IO) {
            val path = "app:/${credentials.profile.folder.trim('/')}/$objectName"
            val uploadEndpoint = Uri.parse("$YANDEX_API/resources/upload").buildUpon()
                .appendQueryParameter("path", path)
                .appendQueryParameter("overwrite", "true")
                .build().toString()
            val href = jsonRequest(uploadEndpoint, "GET", credentials.accessToken).getString("href")
            requireHttps(href)
            open(href, "PUT").also { connection ->
                try {
                    uploadFile(connection, file, "application/octet-stream")
                    checkSuccess(connection)
                } finally {
                    connection.disconnect()
                }
            }
            val publishEndpoint = Uri.parse("$YANDEX_API/resources/publish").buildUpon()
                .appendQueryParameter("path", path)
                .build().toString()
            jsonRequest(publishEndpoint, "PUT", credentials.accessToken, allowEmpty = true)
            val metadataEndpoint = Uri.parse("$YANDEX_API/resources").buildUpon()
                .appendQueryParameter("path", path)
                .appendQueryParameter("fields", "public_url")
                .build().toString()
            val publicUrl = jsonRequest(metadataEndpoint, "GET", credentials.accessToken)
                .getString("public_url")
            requireHttps(publicUrl)
            CloudObjectReference(CloudProvider.YANDEX_DISK, publicUrl, path)
        }

    override suspend fun delete(deleteLocator: String) = withContext(Dispatchers.IO) {
        val endpoint = Uri.parse("$YANDEX_API/resources").buildUpon()
            .appendQueryParameter("path", deleteLocator)
            .appendQueryParameter("permanently", "true")
            .build().toString()
        val connection = open(endpoint, "DELETE", credentials.accessToken)
        try { checkSuccess(connection) } finally { connection.disconnect() }
    }
}

private class GoogleDriveObjectStore(
    private val credentials: CloudStorageCredentials,
) : CloudObjectStore {
    override suspend fun validate() = withContext(Dispatchers.IO) {
        jsonRequest("$GOOGLE_API/files?pageSize=1&fields=files(id)", "GET", credentials.accessToken)
        Unit
    }

    override suspend fun upload(file: File, objectName: String): CloudObjectReference =
        withContext(Dispatchers.IO) {
            val parentId = findOrCreateGoogleFolder(
                credentials.profile.folder,
                credentials.accessToken,
            )
            val existingId = findGoogleFile(objectName, parentId, credentials.accessToken)
            val metadata = JSONObject().put("name", objectName).apply {
                if(existingId == null) put("parents", org.json.JSONArray().put(parentId))
            }
            val sessionEndpoint = if(existingId == null) {
                "$GOOGLE_UPLOAD_API/files?uploadType=resumable&fields=id"
            } else {
                "$GOOGLE_UPLOAD_API/files/${Uri.encode(existingId)}?uploadType=resumable&fields=id"
            }
            val session = open(
                sessionEndpoint,
                if(existingId == null) "POST" else "PATCH",
                credentials.accessToken,
            )
            val uploadUrl = try {
                session.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                session.doOutput = true
                session.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
                checkSuccess(session)
                session.getHeaderField("Location") ?: error("Google Drive did not create an upload session")
            } finally {
                session.disconnect()
            }
            requireHttps(uploadUrl)
            val upload = open(uploadUrl, "PUT", credentials.accessToken)
            val fileId = try {
                uploadFile(upload, file, "application/octet-stream")
                checkSuccess(upload)
                JSONObject(readResponse(upload)).getString("id")
            } finally {
                upload.disconnect()
            }
            ensureGooglePublicReader(fileId, credentials.accessToken)
            CloudObjectReference(
                CloudProvider.GOOGLE_DRIVE,
                "https://drive.google.com/uc?export=download&id=${Uri.encode(fileId)}",
                fileId,
            )
        }

    override suspend fun delete(deleteLocator: String) = withContext(Dispatchers.IO) {
        val connection = open(
            "$GOOGLE_API/files/${Uri.encode(deleteLocator)}",
            "DELETE",
            credentials.accessToken,
        )
        try { checkSuccess(connection) } finally { connection.disconnect() }
    }
}

private fun findOrCreateGoogleFolder(name: String, token: String): String {
    val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
    val query = "mimeType='application/vnd.google-apps.folder' and name='$escaped' and trashed=false"
    val endpoint = Uri.parse("$GOOGLE_API/files").buildUpon()
        .appendQueryParameter("q", query)
        .appendQueryParameter("spaces", "drive")
        .appendQueryParameter("fields", "files(id)")
        .build().toString()
    val files = jsonRequest(endpoint, "GET", token).getJSONArray("files")
    if(files.length() > 0) return files.getJSONObject(0).getString("id")
    return jsonBodyRequest(
        "$GOOGLE_API/files",
        "POST",
        token,
        JSONObject()
            .put("name", name)
            .put("mimeType", "application/vnd.google-apps.folder"),
    ).getString("id")
}

private fun findGoogleFile(name: String, parentId: String, token: String): String? {
    val escapedName = name.replace("\\", "\\\\").replace("'", "\\'")
    val escapedParent = parentId.replace("\\", "\\\\").replace("'", "\\'")
    val query = "name='$escapedName' and '$escapedParent' in parents and trashed=false"
    val endpoint = Uri.parse("$GOOGLE_API/files").buildUpon()
        .appendQueryParameter("q", query)
        .appendQueryParameter("spaces", "drive")
        .appendQueryParameter("fields", "files(id)")
        .build().toString()
    val files = jsonRequest(endpoint, "GET", token).getJSONArray("files")
    return if(files.length() == 0) null else files.getJSONObject(0).getString("id")
}

private fun ensureGooglePublicReader(fileId: String, token: String) {
    val endpoint = "$GOOGLE_API/files/${Uri.encode(fileId)}/permissions"
    val query = Uri.parse(endpoint).buildUpon()
        .appendQueryParameter("fields", "permissions(id,type,role)")
        .appendQueryParameter("pageSize", "100")
        .build().toString()
    val permissions = jsonRequest(query, "GET", token).optJSONArray("permissions")
    val alreadyPublic = permissions != null && (0 until permissions.length()).any { index ->
        permissions.getJSONObject(index).let { permission ->
            permission.optString("type") == "anyone" && permission.optString("role") == "reader"
        }
    }
    if(alreadyPublic) return
    jsonBodyRequest(
        endpoint,
        "POST",
        token,
        JSONObject().put("type", "anyone").put("role", "reader"),
    )
}

private fun jsonRequest(
    url: String,
    method: String,
    token: String,
    allowEmpty: Boolean = false,
): JSONObject {
    val connection = open(url, method, token)
    return try {
        checkSuccess(connection)
        val response = readResponse(connection, allowEmpty)
        if(response.isBlank()) JSONObject() else JSONObject(response)
    } finally {
        connection.disconnect()
    }
}

private fun jsonBodyRequest(
    url: String,
    method: String,
    token: String,
    body: JSONObject,
): JSONObject {
    val connection = open(url, method, token)
    return try {
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        checkSuccess(connection)
        JSONObject(readResponse(connection))
    } finally {
        connection.disconnect()
    }
}

private fun open(url: String, method: String, token: String? = null): HttpURLConnection {
    requireHttps(url)
    return (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 20_000
        readTimeout = 60_000
        // Never forward bearer credentials across redirects. Public downloads
        // carry no Authorization header and may follow provider CDN redirects.
        instanceFollowRedirects = token == null
        useCaches = false
        setRequestProperty("Accept", "application/json")
        token?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
}

private fun uploadFile(connection: HttpURLConnection, file: File, contentType: String) {
    require(file.isFile && file.length() > 0)
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", contentType)
    connection.setFixedLengthStreamingMode(file.length())
    file.inputStream().use { input -> connection.outputStream.use(input::copyTo) }
}

private fun checkSuccess(connection: HttpURLConnection) {
    val code = connection.responseCode
    requireHttps(connection.url.toString())
    if(code !in 200..299) {
        error("Cloud request failed ($code)")
    }
}

private fun readResponse(connection: HttpURLConnection, allowEmpty: Boolean = false): String {
    val text = connection.inputStream?.bufferedReader()?.use(::readBoundedText).orEmpty()
    if(!allowEmpty) require(text.isNotBlank()) { "Cloud service returned an empty response" }
    return text
}

private fun readBoundedText(reader: java.io.BufferedReader): String {
    val output = StringBuilder()
    val buffer = CharArray(4 * 1024)
    while(true) {
        val count = reader.read(buffer)
        if(count < 0) break
        require(output.length + count <= MAX_RESPONSE_BYTES) { "Cloud response is too large" }
        output.append(buffer, 0, count)
    }
    return output.toString()
}

private fun requireHttps(value: String) {
    val uri = Uri.parse(value)
    require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) {
        "Cloud transport requires HTTPS"
    }
    require(uri.userInfo.isNullOrBlank() && uri.fragment.isNullOrBlank()) {
        "Cloud URL must not contain embedded credentials or a fragment"
    }
}

private const val YANDEX_API = "https://cloud-api.yandex.net/v1/disk"
private const val GOOGLE_API = "https://www.googleapis.com/drive/v3"
private const val GOOGLE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
private const val MAX_RESPONSE_BYTES = 64 * 1024
