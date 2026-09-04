package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts password strings at rest using AES/GCM.
 *
 * **Envelope encryption (fast path):**
 * A random 256-bit *data encryption key* (DEK) is generated once per install,
 * wrapped with the Android Keystore master key and stored in SharedPreferences.
 * Each password row is then encrypted with ordinary in-memory AES/GCM under the
 * DEK - there is NO per-row Keystore round-trip. This makes bulk import and
 * full-list decryption orders of magnitude faster (hundreds of rows in a few
 * milliseconds instead of the ~10-20 ms per row that hardware Keystore
 * operations took before - e.g. ~6 seconds for 600 rows).
 *
 * Format versioning:
 * - New rows: payload starts with "2:" (DEK-encrypted), stored by the
 *   repository as `enc:2:<b64>`. One Keystore op (unwrap DEK) per process.
 * - Legacy rows (stored as `enc:<b64>`, encrypted directly with the Keystore
 *   key) are still decrypted for full backward compatibility. The repository
 *   migrates them to the fast format once at app startup.
 */
class PasswordCrypto(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val KEY_ALIAS = "doffisecure_master_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        // 12-byte IV for GCM
        const val IV_SIZE_BYTES = 12
        const val DEK_SIZE_BYTES = 32

        // Marks DEK-encrypted (fast) payloads inside the repository "enc:" prefix.
        const val NEW_BODY_PREFIX = "2:"

        const val PREFS_NAME = "doffisecure_crypto"
        const val KEY_HAS_DEK = "has_dek"
        const val KEY_DEK_WRAPPED = "dek_wrapped_iv_ct"

        @Volatile
        private var cachedKey: SecretKey? = null

        @Volatile
        private var cachedDek: SecretKey? = null

        // AndroidKeyStore is not thread-safe: concurrent load()/getKey() calls
        // throw NullPointerException. Guard all single-time provisioning.
        private val keyLock = Any()
    }

    // ---- Keystore master key (DEK wrapping + legacy rows) ----

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }

        synchronized(keyLock) {
            cachedKey?.let { return it }

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                cachedKey = existing
                return existing
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            val key = keyGenerator.generateKey()
            cachedKey = key
            return key
        }
    }

    // ---- Data encryption key (fast in-memory AES/GCM) ----

    private fun getDek(): SecretKey {
        cachedDek?.let { return it }

        synchronized(keyLock) {
            cachedDek?.let { return it }
            val dek = if (prefs.getBoolean(KEY_HAS_DEK, false)) {
                try {
                    unwrapDek()
                } catch (_: Exception) {
                    createAndStoreDek()
                }
            } else {
                createAndStoreDek()
            }
            cachedDek = dek
            return dek
        }
    }

    /** Generates a fresh random DEK and stores it wrapped with the Keystore key. */
    private fun createAndStoreDek(): SecretKey {
        val raw = ByteArray(DEK_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
        val dek = SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(raw)

        val wrapped = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, wrapped, 0, iv.size)
        System.arraycopy(encrypted, 0, wrapped, iv.size, encrypted.size)

        prefs.edit()
            .putString(KEY_DEK_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putBoolean(KEY_HAS_DEK, true)
            .apply()
        return dek
    }

    /** Unwraps the persisted DEK with the Keystore master key. */
    private fun unwrapDek(): SecretKey {
        val stored = prefs.getString(KEY_DEK_WRAPPED, null) ?: return createAndStoreDek()
        val wrapped = Base64.decode(stored, Base64.NO_WRAP)
        if (wrapped.size <= IV_SIZE_BYTES) return createAndStoreDek()

        val iv = wrapped.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = wrapped.copyOfRange(IV_SIZE_BYTES, wrapped.size)

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        val raw = cipher.doFinal(cipherText)
        return SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
    }

    // ---- Row-level operations ----

    /**
     * Encrypts a plain-text string into the fast envelope format:
     * `2:` + Base64(iv + cipherText).
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return NEW_BODY_PREFIX + encryptWithKey(plainText, getDek())
    }

    /**
     * Decrypts a payload. Handles both the fast envelope format ("2:...") and
     * legacy Keystore-encrypted payloads. For legacy plain-text rows (written
     * before encryption was introduced) returns the raw input unchanged so no
     * data is ever lost on upgrade.
     */
    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return if (encoded.startsWith(NEW_BODY_PREFIX)) {
            try {
                decryptWithKey(encoded.removePrefix(NEW_BODY_PREFIX), getDek())
            } catch (_: Exception) {
                encoded // DEK unavailable/corrupt - never crash on read
            }
        } else {
            try {
                decryptWithKey(encoded, getOrCreateKey())
            } catch (_: Exception) {
                encoded // pre-encryption plain-text fallback
            }
        }
    }

    private fun encryptWithKey(plain: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptWithKey(payload: String, key: SecretKey): String {
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        if (combined.size <= IV_SIZE_BYTES) throw IllegalArgumentException("Payload too small")

        val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = combined.copyOfRange(IV_SIZE_BYTES, combined.size)

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}