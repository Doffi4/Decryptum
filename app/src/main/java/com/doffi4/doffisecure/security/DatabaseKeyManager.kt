package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the generation and secure storage of the 256-bit passphrase used to encrypt
 * the Room SQLite database via SQLCipher.
 *
 * The raw 32-byte key is encrypted at rest using an AES-256-GCM key inside the
 * `AndroidKeyStore` and persisted in private [SharedPreferences]. Hardware backing
 * depends on the device and is not established by this implementation.
 *
 * In-memory caching ensures that the Keystore decryption only occurs once per app process.
 */
class DatabaseKeyManager(
    private val prefs: SharedPreferences,
    private val keyStoreProvider: String = ANDROID_KEYSTORE,
    private val testSecretKey: SecretKey? = null,
) {


    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        const val PREFS_NAME = "doffisecure_db_security"
        const val KEY_ALIAS = "doffisecure_db_master_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BYTES = 32

        private const val PREF_ENCRYPTED_DB_KEY = "encrypted_db_key"
        private const val PREF_DB_KEY_IV = "db_key_iv"

        private val lock = Any()
    }

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    @Volatile
    private var persistenceFailed = false

    /**
     * Returns the 32-byte (256-bit) encryption key for the database.
     * Generates a new random key if one does not already exist.
     */
    fun getPassphrase(): ByteArray {
        check(!persistenceFailed) { "Database key persistence failed; restart before retrying" }
        cachedPassphrase?.let { return it.copyOf() }

        synchronized(lock) {
            check(!persistenceFailed) { "Database key persistence failed; restart before retrying" }
            cachedPassphrase?.let { return it.copyOf() }

            val existingEncrypted = prefs.getString(PREF_ENCRYPTED_DB_KEY, null)
            val existingIv = prefs.getString(PREF_DB_KEY_IV, null)
            check((existingEncrypted == null) == (existingIv == null)) {
                "Incomplete stored database key; refusing to replace it"
            }

            val rawKey = if ((existingEncrypted != null) && (existingIv != null)) {
                try {
                    decryptKey(
                        Base64.getDecoder().decode(existingEncrypted),
                        Base64.getDecoder().decode(existingIv)
                    )
                } catch (e: Exception) {
                    // In the unlikely event of Keystore corruption, throw to avoid silent data loss
                    throw IllegalStateException("Failed to decrypt database encryption key from Keystore", e)
                }
            } else {
                generateAndStoreNewKey()
            }

            cachedPassphrase = rawKey
            return rawKey.copyOf()
        }
    }

    private fun generateAndStoreNewKey(): ByteArray {
        val rawKey = ByteArray(KEY_SIZE_BYTES).apply {
            SecureRandom().nextBytes(this)
        }

        val secretKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedKey = cipher.doFinal(rawKey)

        val persisted = prefs.edit()
            .putString(PREF_ENCRYPTED_DB_KEY, Base64.getEncoder().encodeToString(encryptedKey))
            .putString(PREF_DB_KEY_IV, Base64.getEncoder().encodeToString(iv))
            .commit()
        if (!persisted) {
            // Failed commits may still update SharedPreferences in memory. Never let
            // another request use that non-durable key to encrypt the database.
            persistenceFailed = true
            rawKey.fill(0)
            throw IllegalStateException("Failed to persist database encryption key")
        }

        return rawKey
    }

    private fun decryptKey(encryptedKey: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedKey)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        testSecretKey?.let { return it }
        val keyStore = KeyStore.getInstance(keyStoreProvider).apply { load(null) }

        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            keyStoreProvider
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
        return keyGenerator.generateKey()
    }
}
