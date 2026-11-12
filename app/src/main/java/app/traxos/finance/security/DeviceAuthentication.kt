package app.traxos.finance.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object DeviceAuthentication {
    private const val ALIAS = "traxos.lock.device-auth.v1"

    fun authenticators(sdk: Int = Build.VERSION.SDK_INT) =
        if (sdk >= 30)
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun available(context: Context) =
        BiometricManager.from(context).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Key material never leaves Android Keystore; native auth gates a fresh cipher operation. */
    fun prepare(enabling: Boolean): Cipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (enabling && store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        var key = store.getKey(ALIAS, null) as? SecretKey
        if (key == null) {
            check(enabling) { "Device key unavailable; unlock with your Traxos PIN" }
            val builder =
                KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= 30)
                builder
                    .setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    .setInvalidatedByBiometricEnrollment(false)
            else {
                @Suppress("DEPRECATION")
                builder
                    .setUserAuthenticationValidityDurationSeconds(-1)
                    .setInvalidatedByBiometricEnrollment(true)
            }
            key =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    .apply { init(builder.build()) }
                    .generateKey()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
}
