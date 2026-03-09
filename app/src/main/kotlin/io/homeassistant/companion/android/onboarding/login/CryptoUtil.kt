package io.homeassistant.companion.android.onboarding.login

import android.util.Base64
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import timber.log.Timber

/**
 * Utility for AES-256-CBC decryption of server credentials stored in Firestore.
 *
 * Encrypted passwords are stored as Base64-encoded ciphertext. This utility decrypts
 * them using the AES key and IV from [MshSecrets].
 */
internal object CryptoUtil {

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    /**
     * Decrypts a Base64-encoded AES-256-CBC ciphertext.
     *
     * @param encryptedBase64 Base64-encoded ciphertext from Firestore
     * @return decrypted plaintext, or null if decryption fails
     */
    fun decrypt(encryptedBase64: String): String? {
        return try {
            val key = SecretKeySpec(MshSecrets.AES_KEY.toByteArray(Charsets.UTF_8), ALGORITHM)
            val iv = IvParameterSpec(MshSecrets.AES_IV.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            val decoded = Base64.decode(encryptedBase64, Base64.DEFAULT)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "Failed to decrypt server credentials")
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid Base64 input for decryption")
            null
        }
    }
}
