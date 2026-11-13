package app.traxos.finance.cloud

import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CloudFailure(val reason: String) : IOException(reason)

data class CloudReply(
    val status: Int,
    val body: ByteArray = byteArrayOf(),
    val headers: Map<String, String> = emptyMap(),
) {
    fun json() = JSONObject(body.toString(Charsets.UTF_8).ifBlank { "{}" })

    fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
}

interface CloudHttp {
    suspend fun request(
        method: String,
        url: String,
        token: String? = null,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
    ): CloudReply

    suspend fun upload(
        url: String,
        token: String,
        file: File,
        offset: Long,
        length: Int,
        headers: Map<String, String>,
    ): CloudReply

    suspend fun download(url: String, token: String, file: File, maxBytes: Long): CloudReply
}

class GoogleHttps(private val context: android.content.Context) : CloudHttp {
    @Suppress("DEPRECATION")
    private fun packageHeaders(): Map<String, String> {
        val pm = context.packageManager
        val signature =
            if (android.os.Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                    .signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
            else
                pm.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SIGNATURES,
                    )
                    .signatures
                    ?.firstOrNull()
        val fingerprint =
            requireNotNull(signature).toByteArray().let {
                java.security.MessageDigest.getInstance("SHA-1").digest(it).joinToString("") { value
                    ->
                    "%02X".format(value)
                }
            }
        return mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to fingerprint)
    }

    private fun open(
        method: String,
        url: String,
        token: String?,
        headers: Map<String, String>,
    ): HttpsURLConnection {
        val parsed = URL(url)
        require(
            parsed.protocol == "https" &&
                parsed.host in
                    setOf(
                        "identitytoolkit.googleapis.com",
                        "securetoken.googleapis.com",
                        "www.googleapis.com",
                    ) &&
                parsed.userInfo == null &&
                parsed.ref == null &&
                parsed.port in setOf(-1, 443)
        ) {
            "Unsupported cloud endpoint"
        }
        return (parsed.openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            if (parsed.host != "www.googleapis.com")
                packageHeaders().forEach { (key, value) -> setRequestProperty(key, value) }
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
    }

    private fun reply(connection: HttpsURLConnection): CloudReply {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes =
            stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 1024 * 1024) { "Cloud response is too large" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            } ?: byteArrayOf()
        return CloudReply(
            code,
            bytes,
            connection.headerFields
                .filterKeys { it != null }
                .mapValues { it.value.firstOrNull().orEmpty() },
        )
    }

    override suspend fun request(
        method: String,
        url: String,
        token: String?,
        body: ByteArray?,
        headers: Map<String, String>,
    ) =
        withContext(Dispatchers.IO) {
            val connection = open(method, url, token, headers)
            try {
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                reply(connection)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun upload(
        url: String,
        token: String,
        file: File,
        offset: Long,
        length: Int,
        headers: Map<String, String>,
    ) =
        withContext(Dispatchers.IO) {
            require(offset >= 0 && length >= 0 && offset + length <= file.length())
            val connection = open("PUT", url, token, headers)
            try {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(length)
                connection.outputStream.use { output ->
                    java.io.RandomAccessFile(file, "r").use { input ->
                        input.seek(offset)
                        val buffer = ByteArray(64 * 1024)
                        var left = length
                        while (left > 0) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer, 0, minOf(left, buffer.size))
                            require(n > 0)
                            output.write(buffer, 0, n)
                            left -= n
                        }
                    }
                }
                reply(connection)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun download(url: String, token: String, file: File, maxBytes: Long) =
        withContext(Dispatchers.IO) {
            val connection = open("GET", url, token, emptyMap())
            try {
                if (connection.responseCode != 200) return@withContext reply(connection)
                require(connection.contentLengthLong <= maxBytes) { "Backup is too large" }
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= maxBytes) { "Backup is too large" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                CloudReply(200)
            } finally {
                connection.disconnect()
            }
        }
}

internal fun CloudReply.success(): CloudReply {
    if (status == 401) throw CloudFailure("AUTH_EXPIRED")
    if (status == 403) throw CloudFailure("DRIVE_DENIED")
    if (status == 429 || status in 500..599) throw CloudFailure("RETRY_LATER")
    if (status !in 200..299) throw CloudFailure("CLOUD_REQUEST_FAILED")
    return this
}
