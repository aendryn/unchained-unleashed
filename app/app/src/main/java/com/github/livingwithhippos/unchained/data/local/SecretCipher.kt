package com.github.livingwithhippos.unchained.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Encrypts/decrypts small secret strings (remote-service passwords and API tokens) so they are never
 * stored as plaintext in the Room database. The AES key lives in the Android Keystore: it is
 * non-exportable and is never included in cloud backup or device transfer, so the backed-up database
 * contains only ciphertext that is useless off the original device.
 *
 * Both methods are tolerant: [decrypt] returns any value that isn't tagged with [PREFIX] unchanged
 * (so rows written by older builds keep working), and [encrypt] leaves null/empty values and
 * already-encrypted values as-is.
 */
@Singleton
class SecretCipher @Inject constructor() {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns a [PREFIX]-tagged, base64 ciphertext. Null/empty/already-encrypted inputs pass through. */
    fun encrypt(plain: String?): String? {
        if (plain.isNullOrEmpty() || plain.startsWith(PREFIX)) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Don't lose the user's data if the Keystore is unavailable; store plaintext as a last
            // resort (rare) rather than crashing or dropping the credential.
            Timber.e(e, "Failed to encrypt secret, storing as-is")
            plain
        }
    }

    /** Reverses [encrypt]. Untagged (legacy plaintext) values pass through unchanged. */
    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty() || !stored.startsWith(PREFIX)) return stored
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val cipherText = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key lost or data corrupt: return empty so the user can re-enter the secret instead of
            // the app crashing on a decrypt failure.
            Timber.e(e, "Failed to decrypt secret")
            ""
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "unchained_remote_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val PREFIX = "enc1:"
    }
}
