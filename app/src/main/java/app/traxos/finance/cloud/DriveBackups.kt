package app.traxos.finance.cloud

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import app.traxos.finance.backup.PortableBackup
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DRIVE = "https://www.googleapis.com/drive/v3/"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
private const val MAX_BACKUP = 64L * 1024 * 1024

internal fun query(value: String) = URLEncoder.encode(value, "UTF-8")

internal fun validDriveId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,200}"))

internal fun digest(file: File, algorithm: String = "SHA-256"): String =
    file.inputStream().use { input ->
        val hash = MessageDigest.getInstance(algorithm)
        val bytes = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(bytes)
            if (n < 0) break
            hash.update(bytes, 0, n)
        }
        hash.digest().joinToString("") { "%02x".format(it) }
    }

internal fun owner(profile: CloudProfile) =
    MessageDigest.getInstance("SHA-256").digest(profile.uid.toByteArray()).joinToString("") {
        "%02x".format(it)
    }

data class DriveFolder(val id: String, val name: String)

data class DriveSnapshot(
    val id: String,
    val name: String,
    val size: Long,
    val modified: String,
    val md5: String,
    val sha256: String,
)

data class DrivePage(val files: List<DriveSnapshot>, val next: String)

/**
 * Foreground, restart-resumable snapshots. No personal record is ever uploaded without encryption.
 */
class DriveBackups(
    private val context: Context,
    private val portable: PortableBackup,
    private val files: CloudFiles,
    private val http: CloudHttp,
) {
    private val gate = Mutex()
    private val directory = File(context.noBackupFilesDir, "drive-pending").apply { mkdirs() }

    fun pending(): JSONObject? = files.read("upload")

    fun verified(profile: CloudProfile): String =
        files
            .read("drive-verified")
            ?.takeIf { it.optString("uid") == profile.uid }
            ?.optString("time")
            .orEmpty()

    private suspend fun json(
        path: String,
        token: String,
        method: String = "GET",
        body: JSONObject? = null,
    ) =
        http
            .request(
                method,
                DRIVE + path,
                token,
                body?.toString()?.toByteArray(),
                if (body != null) mapOf("Content-Type" to "application/json") else emptyMap(),
            )
            .success()
            .json()

    suspend fun bindIdentity(profile: CloudProfile) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val original = files.read("drive-binding")
                require(
                    original == null ||
                        original.getString("uid") == profile.uid &&
                            original.getString("email").equals(profile.email, true)
                ) {
                    "This phone is already linked to another Google account"
                }
                files.read("binding")?.getJSONObject("profile")?.let {
                    require(it.getString("email").equals(profile.email, true)) {
                        "Use the same verified account email"
                    }
                }
                if (original == null) files.write("drive-binding", profile.json())
            }
        }

    suspend fun identify(token: String): CloudProfile =
        withContext(Dispatchers.IO) {
            val user =
                json("about?fields=user(emailAddress,permissionId,displayName)", token)
                    .getJSONObject("user")
            val id = user.getString("permissionId")
            val email = user.getString("emailAddress")
            require(validDriveId(id) && email.length in 3..254)
            // Drive permissionId is a stable owner identity; it is not a Firebase UID or a Google
            // ID-token subject.
            CloudProfile(
                "drive:$id",
                email,
                user.optString("displayName"),
                true,
                googleEmail = email,
            )
        }

    suspend fun checkAccount(token: String, profile: CloudProfile) {
        val actual = identify(token)
        require(actual.uid == profile.uid && actual.email.equals(profile.email, true)) {
            "Drive account does not match this phone's original binding"
        }
    }

    suspend fun folders(token: String): List<DriveFolder> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<DriveFolder>()
            var next = ""
            do {
                val q =
                    "trashed=false and mimeType='application/vnd.google-apps.folder' and appProperties has { key='traxosFolder' and value='1' }"
                val j =
                    json(
                        "files?q=${query(q)}&pageSize=100&fields=nextPageToken,files(id,name)" +
                            (if (next.isNotBlank()) "&pageToken=${query(next)}" else ""),
                        token,
                    )
                val rows = j.getJSONArray("files")
                repeat(rows.length()) {
                    val row = rows.getJSONObject(it)
                    val id = row.getString("id")
                    require(validDriveId(id))
                    result += DriveFolder(id, row.getString("name"))
                }
                next = j.optString("nextPageToken")
                require(result.size <= 1000) {
                    "Too many backup folders; manage them in Google Drive"
                }
            } while (next.isNotBlank())
            result
        }

    suspend fun createFolder(token: String, name: String): DriveFolder =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val name = name.trim()
                require(name.length in 1..80 && name.none { it.isISOControl() }) {
                    "Use a folder name of 1–80 characters"
                }
                folders(token)
                    .firstOrNull { it.name.equals(name, true) }
                    ?.let {
                        return@withLock it
                    }
                val j =
                    json(
                        "files?fields=id,name",
                        token,
                        "POST",
                        JSONObject()
                            .put("name", name)
                            .put("mimeType", "application/vnd.google-apps.folder")
                            .put("appProperties", JSONObject().put("traxosFolder", "1")),
                    )
                DriveFolder(j.getString("id"), j.getString("name")).also {
                    require(validDriveId(it.id))
                }
            }
        }

    suspend fun list(
        token: String,
        profile: CloudProfile,
        folder: String,
        page: String = "",
    ): DrivePage =
        withContext(Dispatchers.IO) {
            require(validDriveId(folder))
            require(page.length <= 4096)
            val q =
                "trashed=false and '$folder' in parents and appProperties has { key='traxosFormat' and value='1' } and appProperties has { key='owner' and value='${owner(profile)}' }"
            val j =
                json(
                    "files?q=${query(q)}&orderBy=modifiedTime%20desc&pageSize=50&fields=nextPageToken,files(id,name,size,modifiedTime,md5Checksum,appProperties)" +
                        (if (page.isNotBlank()) "&pageToken=${query(page)}" else ""),
                    token,
                )
            val rows = j.getJSONArray("files")
            DrivePage(
                (0 until rows.length()).map {
                    val r = rows.getJSONObject(it)
                    require(validDriveId(r.getString("id")))
                    DriveSnapshot(
                        r.getString("id"),
                        r.getString("name"),
                        r.optString("size", "0").toLong(),
                        r.optString("modifiedTime"),
                        r.optString("md5Checksum"),
                        r.optJSONObject("appProperties")?.optString("sha256").orEmpty(),
                    )
                },
                j.optString("nextPageToken"),
            )
        }

    private fun localFile(pending: JSONObject): File {
        val name = pending.getString("local")
        require(name.matches(Regex("snapshot-[A-Za-z0-9-]+\\.traxos")))
        return File(directory, name)
    }

    private suspend fun mirror(file: File, uri: Uri) {
        val resolver = context.contentResolver
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
        val document =
            DocumentsContract.createDocument(
                resolver,
                parent,
                "application/octet-stream",
                "TRAXOS-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.traxos",
            ) ?: throw CloudFailure("LOCAL_FOLDER_FAILED")
        try {
            requireNotNull(resolver.openOutputStream(document, "w")).use { output ->
                file.inputStream().use { it.copyTo(output, 64 * 1024) }
            }
            val hash =
                requireNotNull(resolver.openInputStream(document)).use { input ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        md.update(buffer, 0, n)
                    }
                    md.digest().joinToString("") { "%02x".format(it) }
                }
            require(hash == digest(file)) { "Local copy verification failed" }
            context.getSharedPreferences("local_folder", 0).edit {
                putString(
                    "verified",
                    "Local snapshot verified ${java.time.LocalDateTime.now().withNano(0)}. Back up newer changes.",
                )
            }
        } catch (e: Exception) {
            try {
                DocumentsContract.deleteDocument(resolver, document)
            } catch (_: Exception) {
                /* New incomplete encrypted copy only; never remove an existing backup. */
            }
            throw e
        }
    }

    suspend fun prepare(profile: CloudProfile, folder: String, password: CharArray) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                require(validDriveId(folder))
                require(pending() == null) {
                    "Resume or discard the pending encrypted snapshot first"
                }
                val file = File(directory, "snapshot-${UUID.randomUUID()}.traxos")
                try {
                    file.outputStream().use { portable.write(it, password) }
                    require(file.length() in 1..MAX_BACKUP) {
                        "Snapshots must be smaller than 64 MiB"
                    }
                    file.inputStream().use { portable.inspect(it, password) }.use {}
                    files.write(
                        "upload",
                        JSONObject()
                            .put("uid", profile.uid)
                            .put("folder", folder)
                            .put("local", file.name)
                            .put("sha256", digest(file))
                            .put("md5", digest(file, "MD5"))
                            .put("size", file.length())
                            .put("id", "")
                            .put("session", "")
                            .put("mirrored", false)
                            .put(
                                "name",
                                "TRAXOS-${java.time.LocalDateTime.now().withNano(0).toString().replace(':','-')}.traxos",
                            ),
                    )
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            }
        }

    private fun validSession(value: String): Boolean =
        runCatching {
                val u = URL(value)
                u.protocol == "https" &&
                    u.host == "www.googleapis.com" &&
                    u.port in setOf(-1, 443) &&
                    u.userInfo == null &&
                    u.ref == null &&
                    u.path == "/upload/drive/v3/files"
            }
            .getOrDefault(false)

    private suspend fun metadata(id: String, token: String): JSONObject? {
        require(validDriveId(id))
        val result =
            http.request("GET", DRIVE + "files/$id?fields=id,size,md5Checksum,appProperties", token)
        if (result.status == 404) return null
        return result.success().json()
    }

    private fun matches(remote: JSONObject, p: JSONObject, profile: CloudProfile): Boolean =
        remote.optString("id") == p.optString("id") &&
            remote.optString("size") == p.getLong("size").toString() &&
            remote.optString("md5Checksum") == p.getString("md5") &&
            remote.optJSONObject("appProperties")?.optString("sha256") == p.getString("sha256") &&
            remote.optJSONObject("appProperties")?.optString("owner") == owner(profile)

    private suspend fun verifyRemote(
        token: String,
        profile: CloudProfile,
        p: JSONObject,
        password: CharArray,
    ) {
        val remote = metadata(p.getString("id"), token)
        require(remote != null && matches(remote, p, profile)) {
            "Drive did not acknowledge the expected encrypted snapshot"
        }
        val file = File(context.cacheDir, "drive-verify-${UUID.randomUUID()}.traxos")
        try {
            http
                .download(DRIVE + "files/${p.getString("id")}?alt=media", token, file, MAX_BACKUP)
                .success()
            require(digest(file) == p.getString("sha256")) { "Downloaded backup did not match" }
            file.inputStream().use { portable.inspect(it, password) }.use {}
        } finally {
            file.delete()
        }
    }

    suspend fun upload(
        token: String,
        profile: CloudProfile,
        password: CharArray,
        localFolder: String?,
        progress: (Int) -> Unit,
    ) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val p = requireNotNull(pending()) { "No pending snapshot" }
                require(p.getString("uid") == profile.uid) {
                    "Pending backup belongs to the original account"
                }
                val file = localFile(p)
                require(
                    file.isFile &&
                        file.length() == p.getLong("size") &&
                        digest(file) == p.getString("sha256")
                ) {
                    "Pending encrypted snapshot is unavailable. Existing ledger data is unchanged."
                }
                file.inputStream().use { portable.inspect(it, password) }.use {}
                if (localFolder != null && p.optString("mirroredUri") != localFolder) {
                    mirror(file, localFolder.toUri())
                    p.put("mirrored", true).put("mirroredUri", localFolder)
                    files.write("upload", p)
                }
                if (p.getString("id").isBlank()) {
                    val id =
                        json("files/generateIds?count=1&space=drive&type=files", token)
                            .getJSONArray("ids")
                            .getString(0)
                    require(validDriveId(id))
                    p.put("id", id)
                    files.write("upload", p)
                }
                var remote = metadata(p.getString("id"), token)
                if (remote != null)
                    require(matches(remote, p, profile)) {
                        "Drive file conflict. No existing backup was overwritten."
                    }
                var restarted = false
                while (remote == null) {
                    currentCoroutineContext().ensureActive()
                    var session = p.getString("session")
                    if (session.isBlank()) {
                        val data =
                            JSONObject()
                                .put("id", p.getString("id"))
                                .put("name", p.getString("name"))
                                .put("mimeType", "application/octet-stream")
                                .put("parents", org.json.JSONArray().put(p.getString("folder")))
                                .put(
                                    "appProperties",
                                    JSONObject()
                                        .put("traxosFormat", "1")
                                        .put("owner", owner(profile))
                                        .put("sha256", p.getString("sha256")),
                                )
                        val result =
                            http.request(
                                "POST",
                                UPLOAD +
                                    "?uploadType=resumable&fields=id,size,md5Checksum,appProperties",
                                token,
                                data.toString().toByteArray(),
                                mapOf(
                                    "Content-Type" to "application/json; charset=UTF-8",
                                    "X-Upload-Content-Type" to "application/octet-stream",
                                    "X-Upload-Content-Length" to file.length().toString(),
                                ),
                            )
                        if (result.status == 409) {
                            remote = metadata(p.getString("id"), token)
                            require(remote != null && matches(remote!!, p, profile))
                            break
                        }
                        result.success()
                        session = requireNotNull(result.header("Location"))
                        require(validSession(session)) { "Unexpected upload destination" }
                        p.put("session", session)
                        files.write("upload", p)
                    }
                    require(validSession(session))
                    var response =
                        http.request(
                            "PUT",
                            session,
                            token,
                            byteArrayOf(),
                            mapOf("Content-Range" to "bytes */${file.length()}"),
                        )
                    if (response.status in setOf(404, 410)) {
                        require(!restarted) { "Upload expired. Retry when connected." }
                        p.put("session", "")
                        files.write("upload", p)
                        restarted = true
                        continue
                    }
                    var previousOffset = -1L
                    while (response.status == 308) {
                        val range = response.header("Range")
                        val offset =
                            if (range == null) 0L
                            else {
                                require(range.matches(Regex("bytes=0-[0-9]+")))
                                range.substringAfterLast('-').toLong() + 1
                            }
                        require(offset > previousOffset) {
                            "Upload made no progress. Retry when connected."
                        }
                        previousOffset = offset
                        require(offset in 0 until file.length()) { "Unexpected upload offset" }
                        val count = minOf(1024 * 1024L, file.length() - offset).toInt()
                        progress((offset * 100 / file.length()).toInt())
                        response =
                            http.upload(
                                session,
                                token,
                                file,
                                offset,
                                count,
                                mapOf(
                                    "Content-Type" to "application/octet-stream",
                                    "Content-Range" to
                                        "bytes $offset-${offset+count-1}/${file.length()}",
                                ),
                            )
                        currentCoroutineContext().ensureActive()
                    }
                    response.success()
                    remote = metadata(p.getString("id"), token)
                    require(remote != null && matches(remote!!, p, profile)) {
                        "Upload acknowledgment is incomplete"
                    }
                }
                verifyRemote(token, profile, p, password)
                files.write(
                    "drive-verified",
                    JSONObject()
                        .put("uid", profile.uid)
                        .put("id", p.getString("id"))
                        .put("time", java.time.LocalDateTime.now().withNano(0).toString())
                        .put("sha256", p.getString("sha256")),
                )
                context.getSharedPreferences("local_folder", 0).edit {
                    putString(
                        "drive_verified_at",
                        java.time.LocalDateTime.now().withNano(0).toString(),
                    )
                }
                files.write("upload", null)
                file.delete()
                progress(100)
            }
        }

    suspend fun inspect(
        token: String,
        profile: CloudProfile,
        snapshot: DriveSnapshot,
        password: CharArray,
    ): PortableBackup.Preview =
        withContext(Dispatchers.IO) {
            require(validDriveId(snapshot.id) && snapshot.size in 1..MAX_BACKUP)
            val data = metadata(snapshot.id, token) ?: throw CloudFailure("BACKUP_NOT_FOUND")
            require(data.optJSONObject("appProperties")?.optString("owner") == owner(profile)) {
                "Backup belongs to a different profile"
            }
            val file = File(context.cacheDir, "drive-restore-${UUID.randomUUID()}.traxos")
            try {
                http
                    .download(DRIVE + "files/${snapshot.id}?alt=media", token, file, MAX_BACKUP)
                    .success()
                require(
                    file.length() == snapshot.size &&
                        digest(file, "MD5") == snapshot.md5 &&
                        digest(file) == snapshot.sha256
                ) {
                    "Backup changed. Refresh the list and retry."
                }
                file.inputStream().use { portable.inspect(it, password) }
            } finally {
                file.delete()
            }
        }

    suspend fun restore(preview: PortableBackup.Preview) = portable.restore(preview)

    suspend fun discard() =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val p = pending() ?: return@withLock
                localFile(p).delete()
                files.write("upload", null)
            }
        }
}
