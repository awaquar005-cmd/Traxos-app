package app.traxos.finance.cloud

import android.util.AtomicFile
import android.util.Base64
import app.traxos.finance.gifts.GiftCipher
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Public Firebase/Google configuration. This is not a secret, password or service-account key. */
data class CloudConfig(val projectId: String, val apiKey: String, val webClientId: String) {
    val authReady
        get() =
            projectId.matches(Regex("[a-z0-9-]{4,80}")) &&
                apiKey.matches(Regex("[A-Za-z0-9_-]{20,100}"))

    val googleReady
        get() =
            authReady &&
                webClientId.matches(
                    Regex("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com")
                ) &&
                webClientId !=
                    "349564032175-ijp6aekrsig6gg24vcmjdft9b92e7r2s.apps.googleusercontent.com"

    companion object {
        fun fromAndroidJson(text: String): CloudConfig {
            require(text.length <= 256_000) { "Configuration file is too large" }
            val root = JSONObject(text)
            require(!root.has("private_key") && !root.has("client_secret")) {
                "Use the public Firebase Android file, not a private key"
            }
            require(root.has("client") && root.has("project_info")) {
                "Use Firebase Android google-services.json, not the OAuth client download"
            }
            val clients = root.getJSONArray("client")
            val client =
                (0 until clients.length())
                    .map { clients.getJSONObject(it) }
                    .firstOrNull {
                        it.optJSONObject("client_info")
                            ?.optJSONObject("android_client_info")
                            ?.optString("package_name") == "app.traxos.finance"
                    } ?: error("Configuration package must be app.traxos.finance")
            val oauth = client.optJSONArray("oauth_client")
            val web =
                oauth
                    ?.let { list ->
                        (0 until list.length())
                            .map { list.getJSONObject(it) }
                            .firstOrNull { it.optInt("client_type") == 3 }
                            ?.getString("client_id")
                    }
                    .orEmpty()
            return CloudConfig(
                    root.getJSONObject("project_info").getString("project_id"),
                    client.getJSONArray("api_key").getJSONObject(0).getString("current_key"),
                    web,
                )
                .also { require(it.authReady) { "Invalid Firebase configuration" } }
        }
    }
}

/** All identity/session/transfer metadata uses a dedicated Keystore envelope outside OS backups. */
class CloudFiles(private val directory: File, private val cipher: GiftCipher) {
    @Synchronized
    fun read(name: String): JSONObject? {
        val file = AtomicFile(File(directory, "cloud-$name.bin"))
        if (!file.baseFile.exists() && !File(directory, "cloud-$name.bin.bak").exists()) return null
        return JSONObject(cipher.open("cloud:$name", file.readFully()).toString(Charsets.UTF_8))
    }

    @Synchronized
    fun write(name: String, json: JSONObject?) {
        directory.mkdirs()
        val file = AtomicFile(File(directory, "cloud-$name.bin"))
        if (json == null) {
            file.delete()
            check(!file.baseFile.exists() && !File(directory, "cloud-$name.bin.bak").exists()) {
                "Could not clear saved cloud credentials"
            }
            return
        }
        val encrypted = cipher.seal("cloud:$name", json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
            check(file.readFully().contentEquals(encrypted)) {
                "Cloud credential storage could not be verified"
            }
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }
}

data class CloudProfile(
    val uid: String,
    val email: String,
    val name: String,
    val verified: Boolean,
    val googleSubject: String = "",
    val googleEmail: String = "",
) {
    fun json() =
        JSONObject()
            .put("uid", uid)
            .put("email", email)
            .put("name", name)
            .put("verified", verified)
            .put("googleSubject", googleSubject)
            .put("googleEmail", googleEmail)

    companion object {
        fun read(j: JSONObject) =
            CloudProfile(
                j.getString("uid"),
                j.getString("email"),
                j.optString("name"),
                j.getBoolean("verified"),
                j.optString("googleSubject"),
                j.optString("googleEmail"),
            )
    }
}

data class AccountState(
    val ready: Boolean = false,
    val profile: CloudProfile? = null,
    val binding: CloudProfile? = null,
    val cacheError: Boolean = false,
)

class CloudAccounts(
    private val files: CloudFiles,
    private val http: CloudHttp,
    private val config: () -> CloudConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val state = MutableStateFlow(AccountState())
    private val gate = Mutex()
    private var session: JSONObject? = null

    private fun savedBinding(): CloudProfile? =
        files.read("binding")?.let { CloudProfile.read(it.getJSONObject("profile")) }

    suspend fun load() =
        withContext(Dispatchers.IO) {
            gate.withLock {
                var bound: CloudProfile? = null
                try {
                    bound = savedBinding()
                    session = files.read("session")
                    val profile = session?.getJSONObject("profile")?.let(CloudProfile::read)
                    if (profile != null) checkBinding(profile)
                    state.value = AccountState(true, profile, bound)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    session = null
                    state.value = AccountState(ready = true, binding = bound, cacheError = true)
                }
            }
        }

    private fun checkConfiguration(): CloudConfig {
        val c = config()
        require(c.authReady) { "Import the public Firebase Android configuration first" }
        files.read("binding")?.let {
            require(it.getString("project") == c.projectId && it.getString("apiKey") == c.apiKey) {
                "This phone is bound to another Firebase configuration"
            }
        }
        return c
    }

    private suspend fun api(method: String, body: JSONObject): JSONObject {
        val c = checkConfiguration()
        val response =
            http.request(
                "POST",
                "https://identitytoolkit.googleapis.com/v1/accounts:$method?key=${URLEncoder.encode(c.apiKey,"UTF-8")}",
                body = body.toString().toByteArray(),
                headers = mapOf("Content-Type" to "application/json"),
            )
        if (response.status !in 200..299) {
            val code =
                runCatching {
                        response
                            .json()
                            .getJSONObject("error")
                            .getString("message")
                            .substringBefore(" : ")
                            .substringBefore(':')
                            .trim()
                    }
                    .getOrDefault("AUTH_FAILED")
            throw CloudFailure(
                if (
                    code in
                        setOf(
                            "EMAIL_EXISTS",
                            "INVALID_LOGIN_CREDENTIALS",
                            "INVALID_PASSWORD",
                            "EMAIL_NOT_FOUND",
                            "USER_DISABLED",
                            "WEAK_PASSWORD",
                            "TOO_MANY_ATTEMPTS_TRY_LATER",
                            "OPERATION_NOT_ALLOWED",
                            "INVALID_IDP_RESPONSE",
                            "FEDERATED_USER_ID_ALREADY_LINKED",
                            "CREDENTIAL_TOO_OLD_LOGIN_AGAIN",
                            "TOKEN_EXPIRED",
                            "INVALID_ID_TOKEN",
                            "INVALID_REFRESH_TOKEN",
                        )
                )
                    code
                else "AUTH_FAILED"
            )
        }
        return response.json()
    }

    private suspend fun profile(token: String): CloudProfile {
        val row =
            api("lookup", JSONObject().put("idToken", token)).getJSONArray("users").getJSONObject(0)
        val providers = row.optJSONArray("providerUserInfo")
        val google =
            providers?.let { list ->
                (0 until list.length())
                    .map { list.getJSONObject(it) }
                    .firstOrNull { it.optString("providerId") == "google.com" }
            }
        return CloudProfile(
            row.getString("localId"),
            row.getString("email"),
            row.optString("displayName"),
            row.optBoolean("emailVerified"),
            google?.optString("rawId").orEmpty(),
            google?.optString("email").orEmpty(),
        )
    }

    private fun checkBinding(p: CloudProfile) {
        files.read("drive-binding")?.let { drive ->
            require(p.email.equals(drive.getString("email"), true)) {
                "Use the same email as this phone's linked Google Drive account"
            }
        }
        (savedBinding() ?: state.value.binding)?.let { bound ->
            require(
                p.uid == bound.uid &&
                    p.email.equals(bound.email, true) &&
                    (bound.googleSubject.isBlank() ||
                        p.googleSubject.isBlank() ||
                        p.googleSubject == bound.googleSubject)
            ) {
                "This phone is linked to ${bound.email}. Use that same account; local data was not changed."
            }
        }
    }

    private suspend fun accept(response: JSONObject, authenticated: Boolean) {
        val p = profile(response.getString("idToken"))
        checkBinding(p)
        val next =
            JSONObject()
                .put("idToken", response.getString("idToken"))
                .put("refreshToken", response.getString("refreshToken"))
                .put(
                    "expires",
                    clock() +
                        response.optString("expiresIn", "3600").toLong().coerceIn(60, 86400) * 1000,
                )
                .put(
                    "authenticated",
                    if (authenticated) clock() else session?.optLong("authenticated", 0) ?: 0,
                )
                .put("profile", p.json())
        files.write("session", next)
        session = next
        state.value = state.value.copy(profile = p, cacheError = false)
        state.value.binding?.let {
            if (it.googleSubject.isBlank() && p.googleSubject.isNotBlank()) {
                files.write(
                    "binding",
                    JSONObject()
                        .put("project", config().projectId)
                        .put("apiKey", config().apiKey)
                        .put("profile", p.json()),
                )
                state.value = state.value.copy(binding = p)
            }
        }
    }

    private fun email(value: String): String =
        value.trim().also {
            require(
                it.length in 3..254 && android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches()
            ) {
                "Enter a valid email address"
            }
        }

    private fun password(value: String) {
        require(value.length in 12..128) { "Use a password of 12–128 characters" }
    }

    suspend fun signup(address: String, secret: String) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                require(savedBinding() == null && state.value.binding == null) {
                    "This local profile is already linked. Sign in to its existing account."
                }
                password(secret)
                files.read("drive-binding")?.let {
                    require(email(address).equals(it.getString("email"), true)) {
                        "Use this phone's linked Google email"
                    }
                }
                accept(
                    api(
                        "signUp",
                        JSONObject()
                            .put("email", email(address))
                            .put("password", secret)
                            .put("returnSecureToken", true),
                    ),
                    true,
                )
            }
        }

    suspend fun signin(address: String, secret: String) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val address = email(address)
                state.value.binding?.let {
                    require(address.equals(it.email, true)) { "Use this profile's linked email" }
                }
                require(secret.isNotEmpty() && secret.length <= 128) { "Enter your password" }
                accept(
                    api(
                        "signInWithPassword",
                        JSONObject()
                            .put("email", address)
                            .put("password", secret)
                            .put("returnSecureToken", true),
                    ),
                    true,
                )
            }
        }

    private suspend fun tokenLocked(): String {
        val saved = requireNotNull(session) { "Sign in first" }
        if (saved.getLong("expires") > clock() + 60_000) return saved.getString("idToken")
        val c = checkConfiguration()
        val body =
            "grant_type=refresh_token&refresh_token=${URLEncoder.encode(saved.getString("refreshToken"),"UTF-8")}"
                .toByteArray()
        val response =
            http.request(
                "POST",
                "https://securetoken.googleapis.com/v1/token?key=${URLEncoder.encode(c.apiKey,"UTF-8")}",
                body = body,
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            )
        if (response.status !in 200..299) {
            if (response.status in setOf(400, 401)) {
                files.write("session", null)
                session = null
                state.value = state.value.copy(profile = null)
            }
            throw CloudFailure("SIGN_IN_AGAIN")
        }
        val j = response.json()
        accept(
            JSONObject()
                .put("idToken", j.getString("id_token"))
                .put("refreshToken", j.getString("refresh_token"))
                .put("expiresIn", j.getString("expires_in")),
            false,
        )
        return requireNotNull(session).getString("idToken")
    }

    suspend fun refreshProfile() =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val p = profile(tokenLocked())
                checkBinding(p)
                val next = requireNotNull(session).put("profile", p.json())
                files.write("session", next)
                state.value = state.value.copy(profile = p)
            }
        }

    suspend fun bind() =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val p = profile(tokenLocked())
                require(p.verified) { "Verify your email, then refresh your account status" }
                checkBinding(p)
                files.write(
                    "binding",
                    JSONObject()
                        .put("project", config().projectId)
                        .put("apiKey", config().apiKey)
                        .put("profile", p.json()),
                )
                state.value = state.value.copy(binding = p, profile = p)
            }
        }

    suspend fun google(idToken: String, nonce: String, link: Boolean) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                require(config().googleReady) {
                    "A Web OAuth client ID is required for Google sign-in"
                }
                require(idToken.length <= 32_000)
                // This is only an early nonce/account filter. Firebase verifies Google's
                // signature/audience/issuer before any identity is accepted.
                val parts = idToken.split('.')
                require(parts.size == 3)
                val claims =
                    JSONObject(
                        Base64.decode(
                                parts[1],
                                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                            )
                            .toString(Charsets.UTF_8)
                    )
                require(claims.optString("nonce") == nonce && nonce.length >= 32) {
                    "Google sign-in challenge did not match. Retry."
                }
                files.read("drive-binding")?.let {
                    require(claims.optString("email").equals(it.getString("email"), true)) {
                        "Choose this phone's original Google account"
                    }
                }
                (savedBinding() ?: state.value.binding)?.let {
                    require(
                        claims.optString("email").equals(it.email, true) &&
                            (it.googleSubject.isBlank() ||
                                claims.optString("sub") == it.googleSubject)
                    ) {
                        "Choose this profile's original Google account"
                    }
                }
                val body =
                    JSONObject()
                        .put("requestUri", "https://localhost")
                        .put("returnSecureToken", true)
                        .put(
                            "postBody",
                            "id_token=${URLEncoder.encode(idToken,"UTF-8")}&providerId=google.com",
                        )
                if (link) {
                    val p = requireNotNull(state.value.profile)
                    require(p.verified && p.email.equals(claims.optString("email"), true)) {
                        "Use the same verified email for Google linking"
                    }
                    require(clock() - (session?.optLong("authenticated", 0) ?: 0) < 300_000) {
                        "Sign in again before linking Google"
                    }
                    body.put("idToken", tokenLocked())
                }
                accept(api("signInWithIdp", body), true)
            }
        }

    suspend fun verifyEmail() =
        withContext(Dispatchers.IO) {
            gate.withLock {
                api(
                    "sendOobCode",
                    JSONObject().put("requestType", "VERIFY_EMAIL").put("idToken", tokenLocked()),
                )
                Unit
            }
        }

    suspend fun resetPassword(address: String) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val address = email(address)
                state.value.binding?.let {
                    require(address.equals(it.email, true)) { "Use this profile's linked email" }
                }
                try {
                    api(
                        "sendOobCode",
                        JSONObject().put("requestType", "PASSWORD_RESET").put("email", address),
                    )
                } catch (e: CloudFailure) {
                    if (e.reason != "EMAIL_NOT_FOUND") throw e
                }
                Unit
            }
        }

    suspend fun setPassword(secret: String) =
        withContext(Dispatchers.IO) {
            gate.withLock {
                password(secret)
                require(requireNotNull(state.value.profile).verified)
                require(clock() - (session?.optLong("authenticated", 0) ?: 0) < 300_000) {
                    "Sign in again before changing your password"
                }
                accept(
                    api(
                        "update",
                        JSONObject()
                            .put("idToken", tokenLocked())
                            .put("password", secret)
                            .put("returnSecureToken", true),
                    ),
                    true,
                )
            }
        }

    suspend fun signout() =
        withContext(Dispatchers.IO) {
            gate.withLock {
                files.write("session", null)
                session = null
                state.value = state.value.copy(profile = null)
            }
        }

    suspend fun driveIdentity(): CloudProfile =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val p = profile(tokenLocked())
                checkBinding(p)
                require(
                    state.value.binding?.uid == p.uid &&
                        p.verified &&
                        p.googleSubject.isNotBlank() &&
                        p.googleEmail.equals(p.email, true)
                ) {
                    "Bind this phone and link the same verified Google email first"
                }
                p
            }
        }
}
