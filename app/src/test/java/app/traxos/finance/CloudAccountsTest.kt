package app.traxos.finance

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.cloud.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal class QueueCloudHttp : CloudHttp {
    val replies = java.util.ArrayDeque<CloudReply>()
    val calls = mutableListOf<Triple<String, String, ByteArray?>>()

    fun json(value: JSONObject, status: Int = 200) {
        replies += CloudReply(status, value.toString().toByteArray())
    }

    override suspend fun request(
        method: String,
        url: String,
        token: String?,
        body: ByteArray?,
        headers: Map<String, String>,
    ): CloudReply {
        calls += Triple(method, url, body)
        return replies.removeFirst()
    }

    override suspend fun upload(
        url: String,
        token: String,
        file: File,
        offset: Long,
        length: Int,
        headers: Map<String, String>,
    ) = error("No upload expected")

    override suspend fun download(url: String, token: String, file: File, maxBytes: Long) =
        error("No download expected")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CloudAccountsTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var folder: File
    private lateinit var files: CloudFiles
    private lateinit var http: QueueCloudHttp
    private lateinit var repo: CloudAccounts
    private val config =
        CloudConfig(
            "fixture-project",
            "fixture-public-api-key-123456789",
            "349564032175-fixture.apps.googleusercontent.com",
        )
    private var now = 1_800_000_000_000L

    private fun profile(
        verified: Boolean = true,
        google: Boolean = false,
        uid: String = "fixture-user",
        email: String = "owner@example.test",
    ) =
        JSONObject()
            .put(
                "users",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("localId", uid)
                            .put("email", email)
                            .put("emailVerified", verified)
                            .put(
                                "providerUserInfo",
                                if (google)
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("providerId", "google.com")
                                                .put("rawId", "google-subject")
                                                .put("email", email)
                                        )
                                else JSONArray(),
                            )
                    ),
            )

    private fun session() =
        JSONObject()
            .put("idToken", "fixture-id-token")
            .put("refreshToken", "fixture-refresh-token")
            .put("expiresIn", "3600")

    @Before
    fun setup() =
        runBlocking<Unit> {
            folder = File(context.cacheDir, "auth-test-${UUID.randomUUID()}")
            files = CloudFiles(folder, TestGiftCipher())
            http = QueueCloudHttp()
            repo = CloudAccounts(files, http, { config }, { now })
            repo.load()
        }

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    private suspend fun signin(verified: Boolean = true, google: Boolean = false) {
        http.json(session())
        http.json(profile(verified, google))
        repo.signin("owner@example.test", "Fixture password 123!")
    }

    private suspend fun bind(google: Boolean = false) {
        http.json(profile(google = google))
        repo.bind()
    }

    private fun token(
        email: String = "owner@example.test",
        subject: String = "google-subject",
        nonce: String = "fixture-nonce-123456789012345678901234567890",
    ) =
        "header." +
            Base64.encodeToString(
                JSONObject()
                    .put("email", email)
                    .put("sub", subject)
                    .put("nonce", nonce)
                    .toString()
                    .toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            ) +
            ".signature"

    @Test
    fun tokensAreEncryptedAndSignoutPreservesOneAccountBinding() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            val bytes =
                folder
                    .listFiles()!!
                    .flatMap { it.readBytes().toList() }
                    .toByteArray()
                    .toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains("fixture-refresh-token"))
            assertFalse(bytes.contains("owner@example.test"))
            repo.signout()
            assertNull(repo.state.value.profile)
            assertEquals("fixture-user", repo.state.value.binding!!.uid)
            val reload = CloudAccounts(files, http, { config }, { now })
            reload.load()
            assertNull(reload.state.value.profile)
            assertEquals("google-subject", reload.state.value.binding!!.googleSubject)
        }

    @Test
    fun unverifiedEmailCannotBindExistingLocalProfile() =
        runBlocking<Unit> {
            signin(false)
            http.json(profile(false))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.bind() } }
            assertNull(files.read("binding"))
            http.json(profile(true))
            repo.refreshProfile()
            bind()
            assertEquals("fixture-user", repo.state.value.binding!!.uid)
        }

    @Test
    fun anotherEmailOrGoogleSubjectIsRejectedBeforeLinkRequest() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            repo.signout()
            val count = http.calls.size
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.signin("other@example.test", "Fixture password 123!") }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repo.google(
                        token(subject = "other-google-sub"),
                        "fixture-nonce-123456789012345678901234567890",
                        false,
                    )
                }
            }
            assertEquals(count, http.calls.size)
            assertEquals("google-subject", repo.state.value.binding!!.googleSubject)
        }

    @Test
    fun successfulGoogleLinkKeepsTheSameUidAndUsesCurrentAuthenticatedSession() =
        runBlocking<Unit> {
            signin()
            bind()
            http.json(session())
            http.json(profile(google = true))
            repo.google(token(), "fixture-nonce-123456789012345678901234567890", true)
            val request = http.calls.first { it.second.contains("signInWithIdp") }
            assertEquals(
                "fixture-id-token",
                JSONObject(request.third!!.toString(Charsets.UTF_8)).getString("idToken"),
            )
            assertEquals("fixture-user", repo.state.value.binding!!.uid)
            assertEquals("google-subject", repo.state.value.binding!!.googleSubject)
        }

    @Test
    fun googleCollisionOrNonceFailureNeverRebindsOrOverwritesSession() =
        runBlocking<Unit> {
            signin()
            bind()
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.google(token(), "different-nonce-12345678901234567890", true) }
            }
            http.json(
                JSONObject()
                    .put("error", JSONObject().put("message", "FEDERATED_USER_ID_ALREADY_LINKED")),
                400,
            )
            assertThrows(CloudFailure::class.java) {
                runBlocking {
                    repo.google(token(), "fixture-nonce-123456789012345678901234567890", true)
                }
            }
            assertEquals("", repo.state.value.binding!!.googleSubject)
            assertEquals("fixture-user", repo.state.value.profile!!.uid)
        }

    @Test
    fun passwordAdditionUsesSameUidAndRequiresRecentAuthentication() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            http.json(session())
            http.json(profile(google = true))
            repo.setPassword("New fixture password 456!")
            assertEquals("fixture-user", repo.state.value.profile!!.uid)
            now += 301_000
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.setPassword("New fixture password 789!") }
            }
            assertFalse(
                folder.listFiles()!!.any {
                    it.readBytes().toString(Charsets.ISO_8859_1).contains("New fixture password")
                }
            )
        }

    @Test
    fun revokedRefreshTokenSignsOutWithoutResettingBinding() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            now += 3_600_000
            http.json(
                JSONObject().put("error", JSONObject().put("message", "INVALID_REFRESH_TOKEN")),
                400,
            )
            assertThrows(CloudFailure::class.java) { runBlocking { repo.refreshProfile() } }
            assertNull(repo.state.value.profile)
            assertEquals("fixture-user", repo.state.value.binding!!.uid)
        }

    @Test
    fun wrongUidFromBackendCannotReplaceExistingBoundProfile() =
        runBlocking<Unit> {
            signin()
            bind()
            repo.signout()
            http.json(session())
            http.json(profile(uid = "different-user"))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.signin("owner@example.test", "Fixture password 123!") }
            }
            assertEquals("fixture-user", repo.state.value.binding!!.uid)
            assertNull(repo.state.value.profile)
        }

    @Test
    fun passwordSigninPreservesOriginalGoogleBindingEvenIfProviderNeedsRelinking() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            repo.signout()
            signin(google = false)
            assertEquals("fixture-user", repo.state.value.profile!!.uid)
            assertEquals("google-subject", repo.state.value.binding!!.googleSubject)
            assertEquals("", repo.state.value.profile!!.googleSubject)
        }

    @Test
    fun interruptedAtomicFileWriteRecoversExistingBindingInsteadOfTreatingPhoneAsUnbound() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            repo.signout()
            val base = File(folder, "cloud-binding.bin")
            assertTrue(base.renameTo(File(folder, "cloud-binding.bin.bak")))
            val reload = CloudAccounts(files, http, { config }, { now })
            reload.load()
            assertEquals("fixture-user", reload.state.value.binding!!.uid)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { reload.signin("another@example.test", "Fixture password 123!") }
            }
        }

    @Test
    fun corruptSessionCannotHideOrReplacePersistentAccountBinding() =
        runBlocking<Unit> {
            signin(google = true)
            bind(true)
            val file = File(folder, "cloud-session.bin")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            val reload = CloudAccounts(files, http, { config }, { now })
            reload.load()
            assertTrue(reload.state.value.cacheError)
            assertNull(reload.state.value.profile)
            assertEquals("fixture-user", reload.state.value.binding!!.uid)
            val requests = http.calls.size
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { reload.signup("another@example.test", "Fixture password 123!") }
            }
            assertEquals(requests, http.calls.size)
        }

    @Test
    fun signupCannotReplaceBindingEvenBeforeCacheHasLoaded() =
        runBlocking<Unit> {
            signin()
            bind()
            repo.signout()
            val cold = CloudAccounts(files, http, { config }, { now })
            val requests = http.calls.size
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { cold.signup("another@example.test", "Fixture password 123!") }
            }
            assertEquals(requests, http.calls.size)
            assertEquals(
                "fixture-user",
                CloudProfile.read(files.read("binding")!!.getJSONObject("profile")).uid,
            )
        }

    @Test
    fun publicConfigurationRequiresCorrectAndroidPackageAndFindsWebClient() {
        val client =
            JSONObject()
                .put(
                    "client_info",
                    JSONObject()
                        .put(
                            "android_client_info",
                            JSONObject().put("package_name", "app.traxos.finance"),
                        ),
                )
                .put("api_key", JSONArray().put(JSONObject().put("current_key", config.apiKey)))
                .put(
                    "oauth_client",
                    JSONArray()
                        .put(
                            JSONObject().put("client_type", 3).put("client_id", config.webClientId)
                        ),
                )
        val root =
            JSONObject()
                .put("project_info", JSONObject().put("project_id", config.projectId))
                .put("client", JSONArray().put(client))
        assertTrue(CloudConfig.fromAndroidJson(root.toString()).googleReady)
        client
            .getJSONObject("client_info")
            .getJSONObject("android_client_info")
            .put("package_name", "wrong.package")
        assertThrows(Exception::class.java) { CloudConfig.fromAndroidJson(root.toString()) }
        assertFalse(
            config
                .copy(
                    webClientId =
                        "349564032175-ijp6aekrsig6gg24vcmjdft9b92e7r2s.apps.googleusercontent.com"
                )
                .googleReady
        )
    }
}
