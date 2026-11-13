package app.traxos.finance.ui

import android.accounts.Account
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.traxos.finance.TraxosApplication
import app.traxos.finance.backup.PortableBackup
import app.traxos.finance.cloud.*
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.*
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

private suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

internal class CloudViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as TraxosApplication
    val accounts = app.cloudAccounts
    val account = accounts.state
    val config = MutableStateFlow(app.cloudConfig())
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("Your ledger works offline. Sign-in and backups are optional.")
    val error = MutableStateFlow(false)
    val resolution = MutableStateFlow<IntentSenderRequest?>(null)
    val folders = MutableStateFlow<List<DriveFolder>>(emptyList())
    val folder = MutableStateFlow<DriveFolder?>(null)
    val snapshots = MutableStateFlow<List<DriveSnapshot>>(emptyList())
    val next = MutableStateFlow("")
    val connected = MutableStateFlow(false)
    val progress = MutableStateFlow<Int?>(null)
    val pending = MutableStateFlow(false)
    val verified = MutableStateFlow("")
    val preview = MutableStateFlow<PortableBackup.Preview?>(null)
    val candidate = MutableStateFlow<CloudProfile?>(null)
    val driveBinding = MutableStateFlow<CloudProfile?>(null)
    private var driveProfile: CloudProfile? = null
    private var token: String? = null
    private var currentJob: Job? = null
    private var waitingGoogle = false
    private var waitingDriveConsent = false
    @Volatile private var cleared = false

    init {
        action {
            accounts.load()
            pending.value = withContext(Dispatchers.IO) { app.driveBackups.pending() != null }
            driveBinding.value =
                withContext(Dispatchers.IO) {
                    app.cloudFiles.read("drive-binding")?.let(CloudProfile::read)
                }
        }
    }

    private suspend fun clearDriveToken(value: String): Boolean {
        return try {
            Identity.getAuthorizationClient(app)
                .clearToken(ClearTokenRequest.builder().setToken(value).build())
                .result()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun message(e: Exception): String =
        when (e) {
            is GetCredentialCancellationException ->
                "Google sign-in cancelled. Your local records are unchanged."
            is NoCredentialException ->
                "No Google credential is available. Add a Google account in Android Settings and check the Web OAuth configuration."
            is com.google.android.gms.common.api.ApiException ->
                when (e.statusCode) {
                    16 -> "Google consent cancelled. No records were uploaded."
                    10 ->
                        "Google rejected the app configuration. Check the Android package, SHA-1, OAuth consent setup and enabled Drive API."
                    else ->
                        "Google authorization could not finish. Check Google Play services and your connection, then retry."
                }
            is CloudFailure ->
                when (e.reason) {
                    "EMAIL_EXISTS" ->
                        "This email already has an account. Sign in or reset its password."
                    "INVALID_LOGIN_CREDENTIALS",
                    "INVALID_PASSWORD",
                    "EMAIL_NOT_FOUND" -> "Email or password was not accepted."
                    "USER_DISABLED" -> "This account is disabled. Local records are unchanged."
                    "WEAK_PASSWORD" ->
                        "Use at least 12 characters and follow the project's password policy."
                    "OPERATION_NOT_ALLOWED" ->
                        "Enable Email/Password and Google providers in Firebase Authentication."
                    "TOO_MANY_ATTEMPTS_TRY_LATER",
                    "RETRY_LATER" ->
                        "Too many requests or a temporary service issue. Wait and retry."
                    "AUTH_EXPIRED" ->
                        "Drive access expired. Authorize Drive again, then resume the pending backup."
                    "SIGN_IN_AGAIN",
                    "TOKEN_EXPIRED",
                    "INVALID_ID_TOKEN",
                    "INVALID_REFRESH_TOKEN" ->
                        "Your account session expired or was revoked. Sign in again; local data is unchanged."
                    "DRIVE_CACHE_UNAVAILABLE" ->
                        "Google could not clear expired cached access. Review Traxos access in your Google Account, then reconnect. Local records are unchanged."
                    "DRIVE_DENIED" ->
                        "Drive denied access. Check consent, Drive API enablement and free storage, then reconnect."
                    "FEDERATED_USER_ID_ALREADY_LINKED" ->
                        "That Google account is already linked elsewhere. Accounts were not merged."
                    "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" ->
                        "Sign in again before changing account security."
                    else ->
                        "The cloud request failed. Check provider setup and your connection, then retry. Your local records are unchanged."
                }
            is IllegalArgumentException -> e.message ?: "Check the entered details."
            else ->
                "Could not complete this operation. Check your connection and configuration. Your ledger and pending encrypted snapshot are unchanged."
        }

    fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = false
        currentJob =
            viewModelScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    waitingDriveConsent = false
                    resolution.value = null
                    error.value = true
                    status.value = message(e)
                    if (e is CloudFailure && e.reason == "AUTH_EXPIRED") {
                        val stale = token
                        token = null
                        connected.value = false
                        if (stale != null && !clearDriveToken(stale))
                            status.value +=
                                " Google could not clear cached access. Review Traxos access in your Google Account before reconnecting."
                    }
                } finally {
                    if (!waitingDriveConsent) busy.value = false
                    progress.value = null
                    if (cleared) {
                        preview.value?.close()
                        preview.value = null
                    }
                }
            }
    }

    fun importConfig(text: String) = action {
        val c = CloudConfig.fromAndroidJson(text)
        require(
            account.value.binding == null ||
                c.projectId == config.value.projectId && c.apiKey == config.value.apiKey
        ) {
            "This phone's account configuration is already bound"
        }
        app.getSharedPreferences("cloud_config", 0).edit {
            putString("project", c.projectId)
            putString("apiKey", c.apiKey)
            putString("web", c.webClientId)
        }
        config.value = c
        status.value =
            if (c.googleReady) "Account configuration imported. Sign in when ready."
            else
                "Email configuration imported. Enable Google in Firebase, then download the updated Android configuration for Google sign-in."
    }

    fun signin(email: String, password: String) = action {
        accounts.signin(email, password)
        status.value = "Signed in. Local data was not uploaded or replaced."
    }

    fun signup(email: String, password: String) = action {
        accounts.signup(email, password)
        status.value =
            "Account created. Verify your email, refresh its status, then link this phone."
    }

    fun bind() = action {
        accounts.bind()
        status.value =
            "This phone is linked to this account. Existing ledger and PIN are unchanged."
    }

    fun verifyEmail() = action {
        accounts.verifyEmail()
        status.value =
            "Verification email requested. Open its link, then refresh your account status."
    }

    fun refresh() = action {
        accounts.refreshProfile()
        status.value = "Account status refreshed."
    }

    fun reset(email: String) = action {
        accounts.resetPassword(email)
        status.value =
            "If an eligible account exists, a password-reset email has been requested. This does not reset the offline app PIN."
    }

    fun password(secret: String) = action {
        accounts.setPassword(secret)
        status.value = "Password saved for the same account. Existing Google binding is unchanged."
    }

    fun google(context: Context) = action {
        require(config.value.googleReady) {
            "Import Firebase Android configuration with a Web OAuth client ID first"
        }
        val nonce =
            android.util.Base64.encodeToString(
                ByteArray(32).also { SecureRandom().nextBytes(it) },
                android.util.Base64.NO_WRAP or
                    android.util.Base64.URL_SAFE or
                    android.util.Base64.NO_PADDING,
            )
        waitingGoogle = true
        try {
            val request =
                GetCredentialRequest.Builder()
                    .addCredentialOption(
                        GetSignInWithGoogleOption.Builder(config.value.webClientId)
                            .setNonce(nonce)
                            .build()
                    )
                    .build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            require(
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                "Unexpected credential provider"
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            accounts.google(
                google.idToken,
                nonce,
                account.value.profile?.googleSubject?.isBlank() == true,
            )
            status.value = "Google sign-in verified. Link this phone before enabling Drive."
        } finally {
            waitingGoogle = false
        }
    }

    fun cancelGoogle() {
        if (waitingGoogle) currentJob?.cancel()
    }

    fun signout(context: Context) = action {
        accounts.signout()
        token = null
        connected.value = false
        folders.value = emptyList()
        folder.value = null
        snapshots.value = emptyList()
        next.value = ""
        verified.value = ""
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status.value =
                "Signed out locally. Android may still remember the previous Google account; choose it explicitly next time."
            return@action
        }
        status.value =
            "Signed out. Local records, app PIN and the original account binding remain on this phone."
    }

    fun authorize(context: Context) = action {
        accounts.state.first { it.ready }
        val saved =
            withContext(Dispatchers.IO) {
                app.cloudFiles.read("drive-binding")?.let(CloudProfile::read)
            }
        val expected = saved?.email ?: account.value.binding?.email ?: account.value.profile?.email
        val request =
            AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        if (!expected.isNullOrBlank()) request.setAccount(Account(expected, "com.google"))
        val result = Identity.getAuthorizationClient(context).authorize(request.build()).result()
        if (result.hasResolution()) {
            waitingDriveConsent = true
            resolution.value =
                IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender)
                    .build()
            status.value = "Waiting for Google's Drive consent."
        } else acceptDrive(result)
    }

    fun consumeResolution() {
        resolution.value = null
    }

    fun completeAuthorization(context: Context, data: Intent?, ok: Boolean) {
        waitingDriveConsent = false
        resolution.value = null
        busy.value = false
        if (!ok || data == null) {
            status.value = "Drive consent cancelled. No records were uploaded."
            return
        }
        action {
            acceptDrive(
                Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            )
        }
    }

    fun launchFailed() {
        waitingDriveConsent = false
        resolution.value = null
        busy.value = false
        error.value = true
        status.value = "Google consent could not open. Update Google Play services and retry."
    }

    private suspend fun acceptDrive(result: AuthorizationResult) {
        require(result.grantedScopes.contains(DRIVE_FILE_SCOPE)) {
            "Drive file access was not granted"
        }
        val access = requireNotNull(result.accessToken) { "Google did not return an access token" }
        val p =
            try {
                app.driveBackups.identify(access)
            } catch (e: CloudFailure) {
                if (e.reason == "AUTH_EXPIRED" && !clearDriveToken(access))
                    throw CloudFailure("DRIVE_CACHE_UNAVAILABLE")
                throw e
            }
        val bound =
            withContext(Dispatchers.IO) {
                app.cloudFiles.read("drive-binding")?.let(CloudProfile::read)
            }
        val email = account.value.binding?.email ?: account.value.profile?.email
        require(email == null || p.email.equals(email, true)) {
            "Choose the same Google email as this phone's account"
        }
        require(bound == null || bound.uid == p.uid && bound.email.equals(p.email, true)) {
            "A different Google account cannot replace this phone's original Drive binding"
        }
        token = access
        if (bound == null) {
            candidate.value = p
            status.value = "Review the Google account before linking this phone."
        } else finishDrive(p)
    }

    fun disconnect() = action {
        val old = token
        token = null
        connected.value = false
        driveProfile = null
        folders.value = emptyList()
        folder.value = null
        snapshots.value = emptyList()
        val removed = old == null || clearDriveToken(old)
        status.value =
            if (removed)
                "Drive disconnected on this device. Its original account binding and completed backups remain."
            else
                "Drive disconnected locally. Google could not clear its cached token; review app access in your Google Account. The original binding remains."
    }

    fun rejectDrive() {
        candidate.value = null
        token = null
        connected.value = false
        status.value = "Account linking cancelled. Nothing was uploaded."
    }

    fun confirmDrive() = action {
        val p = requireNotNull(candidate.value)
        app.driveBackups.bindIdentity(p)
        driveBinding.value = p
        candidate.value = null
        finishDrive(p)
    }

    private suspend fun finishDrive(p: CloudProfile) {
        driveProfile = p
        driveBinding.value = p
        connected.value = true
        folders.value = app.driveBackups.folders(requireNotNull(token))
        val saved = withContext(Dispatchers.IO) { app.cloudFiles.read("drive-folder") }
        folder.value =
            folders.value.firstOrNull {
                saved?.optString("uid") == p.uid && saved.optString("id") == it.id
            }
        pending.value = withContext(Dispatchers.IO) { app.driveBackups.pending() != null }
        verified.value = withContext(Dispatchers.IO) { app.driveBackups.verified(p) }
        status.value =
            "Drive authorized for ${p.email}. Only app-created backup files are accessible."
        loadList(false)
    }

    private suspend fun currentDrive(): CloudProfile {
        val p = requireNotNull(driveProfile) { "Authorize Drive first" }
        app.driveBackups.checkAccount(requireNotNull(token), p)
        return p
    }

    fun createFolder(name: String) = action {
        val p = currentDrive()
        val created = app.driveBackups.createFolder(requireNotNull(token), name)
        folders.value = app.driveBackups.folders(requireNotNull(token))
        selectFolder(created, p)
        status.value = "Drive folder ready."
    }

    fun selectFolder(id: String) = action {
        val p = currentDrive()
        selectFolder(requireNotNull(folders.value.firstOrNull { it.id == id }), p)
        loadList(false)
    }

    private suspend fun selectFolder(value: DriveFolder, p: CloudProfile) {
        folder.value = value
        withContext(Dispatchers.IO) {
            app.cloudFiles.write(
                "drive-folder",
                org.json.JSONObject().put("uid", p.uid).put("id", value.id),
            )
        }
    }

    fun list(more: Boolean = false) = action { loadList(more) }

    private suspend fun loadList(more: Boolean) {
        val f = folder.value ?: return
        val p = currentDrive()
        val page =
            app.driveBackups.list(requireNotNull(token), p, f.id, if (more) next.value else "")
        snapshots.value = (if (more) snapshots.value else emptyList()) + page.files
        next.value = page.next
    }

    fun backup(password: CharArray, keepLocal: Boolean, resume: Boolean) {
        if (busy.value) {
            password.fill('\u0000')
            return
        }
        action {
            try {
                val p = currentDrive()
                val access = requireNotNull(token) { "Authorize Drive first" }
                val local =
                    app.getSharedPreferences("local_folder", 0).getString("tree", "").orEmpty()
                require(!keepLocal || local.isNotBlank()) {
                    "Select a local backup folder first, or explicitly turn off the local copy"
                }
                if (!resume) {
                    app.driveBackups.prepare(
                        p,
                        requireNotNull(folder.value) { "Choose a Drive folder" }.id,
                        password,
                    )
                }
                pending.value = true
                progress.value = 0
                status.value = "Uploading your encrypted snapshot…"
                app.driveBackups.upload(access, p, password, local.takeIf { keepLocal }) {
                    progress.value = it
                }
                pending.value = false
                verified.value = withContext(Dispatchers.IO) { app.driveBackups.verified(p) }
                status.value =
                    "Drive snapshot downloaded and verified. Back up newer changes again."
                loadList(false)
            } finally {
                password.fill('\u0000')
                pending.value =
                    withContext(NonCancellable + Dispatchers.IO) {
                        app.driveBackups.pending() != null
                    }
            }
        }
    }

    fun inspect(snapshot: DriveSnapshot, password: CharArray) {
        if (busy.value) {
            password.fill('\u0000')
            return
        }
        action {
            try {
                val p = currentDrive()
                preview.value?.close()
                preview.value =
                    app.driveBackups.inspect(requireNotNull(token), p, snapshot, password)
                status.value = "Archive verified. Review before adding records."
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun cancelPreview() {
        if (!busy.value) {
            preview.value?.close()
            preview.value = null
        }
    }

    fun restore() = action {
        val staged = requireNotNull(preview.value)
        try {
            val count = app.driveBackups.restore(staged)
            app.giftScheduler.reconcile()
            app.recurringScheduler.start()
            status.value = "Restored $count missing records. Existing records were not overwritten."
        } finally {
            staged.close()
            preview.value = null
        }
    }

    fun discard() = action {
        app.driveBackups.discard()
        pending.value = false
        status.value =
            "Pending local snapshot discarded. Ledger and completed Drive backups are unchanged."
    }

    override fun onCleared() {
        cleared = true
        token = null
        if (!busy.value) {
            preview.value?.close()
            preview.value = null
        }
    }
}
