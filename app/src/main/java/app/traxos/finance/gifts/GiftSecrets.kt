package app.traxos.finance.gifts

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

interface GiftCipher {
    fun seal(id: String, bytes: ByteArray): ByteArray

    fun open(id: String, bytes: ByteArray): ByteArray

    fun verifyExisting() {}

    fun fingerprint(code: String): String
}

/** Standard AES-256-GCM, random per-write IV, row-ID AAD, non-exportable Android Keystore keys. */
class KeystoreGiftCipher : GiftCipher {
    private val store
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun key(alias: String, hmac: Boolean = false, create: Boolean = true): SecretKey {
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        check(create) { "Gift encryption key is unavailable" }
        val algorithm =
            if (hmac) KeyProperties.KEY_ALGORITHM_HMAC_SHA256 else KeyProperties.KEY_ALGORITHM_AES
        return KeyGenerator.getInstance(algorithm, "AndroidKeyStore")
            .apply {
                val builder =
                    KeyGenParameterSpec.Builder(
                        alias,
                        if (hmac) KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                        else KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                if (hmac) builder.setDigests(KeyProperties.DIGEST_SHA256)
                else
                    builder
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                init(builder.build())
            }
            .generateKey()
    }

    override fun verifyExisting() {
        key("traxos.gifts.aes.v1", create = false)
        key("traxos.gifts.hmac.v1", true, create = false)
    }

    override fun seal(id: String, bytes: ByteArray) =
        GiftEnvelope.seal(key("traxos.gifts.aes.v1"), id, bytes)

    override fun open(id: String, bytes: ByteArray) =
        GiftEnvelope.open(key("traxos.gifts.aes.v1", create = false), id, bytes)

    override fun fingerprint(code: String): String =
        Mac.getInstance("HmacSHA256").run {
            init(key("traxos.gifts.hmac.v1", true))
            doFinal(GiftRules.normalize(code).toByteArray(Charsets.UTF_8)).joinToString("") {
                "%02x".format(it)
            }
        }
}

data class GiftSecret(
    val code: String,
    val pin: String = "",
    val notes: String = "",
    val image: ByteArray? = null,
) {
    fun bytes(): ByteArray =
        JSONObject()
            .put("v", 1)
            .put("code", code)
            .put("pin", pin)
            .put("notes", notes)
            .put(
                "image",
                image?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
            )
            .toString()
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun read(bytes: ByteArray): GiftSecret =
            JSONObject(bytes.toString(Charsets.UTF_8)).let {
                require(it.getInt("v") == 1)
                GiftSecret(
                    it.getString("code"),
                    it.optString("pin"),
                    it.optString("notes"),
                    if (it.isNull("image")) null
                    else
                        android.util.Base64.decode(
                            it.getString("image"),
                            android.util.Base64.NO_WRAP,
                        ),
                )
            }
    }
}

/** Versioned envelope uses platform JCA primitives; no custom cryptographic algorithm. */
object GiftEnvelope {
    fun seal(key: SecretKey, id: String, bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
    }

    fun open(key: SecretKey, id: String, bytes: ByteArray): ByteArray {
        require(bytes.size >= 29 && bytes[0] == 1.toByte()) { "Unsupported encrypted card format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }
}
