package com.doffi4.doffisecure.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.util.Base64
import androidx.core.content.edit
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts password strings at rest using AES/GCM.
 *
 * **Security Architecture:**
 * - Data Encryption Key (DEK, 256-bit AES) is protected by a Key Encryption Key (KEK)
 *   derived from the Master Password via Argon2id (memory-hard KDF: 32 MB RAM, 2 iterations).
 * - Master password correctness is verified via AES-GCM authentication tag verification
 *   during DEK unwrapping (no password hash needed).
 * - Hardware Biometric slot in Android Keystore with `.setUserAuthenticationRequired(true)`
 *   and `.setInvalidatedByBiometricEnrollment(true)`.
 * - DEK is held in RAM only while vault is unlocked and cleared on lock.
 * - Legacy envelope rows ("2:...") and Keystore rows are supported seamlessly.
 */
class PasswordCrypto(
    private val prefs: SharedPreferences,
    private val kdfDeriver: ((password: String, salt: ByteArray) -> ByteArray)? = null
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val argon2Kt: Argon2Kt by lazy { Argon2Kt() }

    private fun base64Encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun base64Decode(str: String): ByteArray =
        Base64.getDecoder().decode(str.trim())

    companion object {
        const val KEY_ALIAS = "doffisecure_master_key"
        const val BIO_KEY_ALIAS = "doffisecure_bio_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val IV_SIZE_BYTES = 12
        const val DEK_SIZE_BYTES = 32
        const val SALT_SIZE_BYTES = 16

        // Marks DEK-encrypted (fast) payloads inside the repository "enc:" prefix.
        const val NEW_BODY_PREFIX = "2:"

        const val PREFS_NAME = "doffisecure_crypto"
        const val KEY_HAS_DEK = "has_dek"
        const val KEY_DEK_WRAPPED_LEGACY = "dek_wrapped_iv_ct"
        const val KEY_DEK_WRAPPED_KEK = "dek_wrapped_kek"
        const val KEY_ARGON2_SALT = "argon2_salt"
        const val KEY_DEK_WRAPPED_BIO = "dek_wrapped_bio"
        const val KEY_BIO_ENABLED = "bio_enabled"

        const val ARGON2_T_COST = 2
        const val ARGON2_M_COST_KIB = 32768 // 32 MB
        const val ARGON2_PARALLELISM = 2

        @Volatile
        private var cachedKey: SecretKey? = null

        @Volatile
        private var cachedDek: SecretKey? = null

        // AndroidKeyStore is not thread-safe: concurrent load()/getKey() calls
        // throw NullPointerException. Guard all single-time provisioning.
        private val keyLock = Any()
    }

    // ---- Legacy Keystore key (kept for migrating old vaults & decrypting legacy rows) ----

    private fun getOrCreateLegacyKey(): SecretKey {
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

    // ---- Argon2id Key Derivation ----

    fun deriveKek(password: String, salt: ByteArray): SecretKey {
        val raw = if (kdfDeriver != null) {
            kdfDeriver.invoke(password, salt)
        } else {
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = password.toByteArray(Charsets.UTF_8),
                salt = salt,
                tCostInIterations = ARGON2_T_COST,
                mCostInKibibyte = ARGON2_M_COST_KIB,
                parallelism = ARGON2_PARALLELISM
            )
            result.rawHashAsByteArray()
        }
        return SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
    }

    // ---- KEK wrapping and unwrapping ----

    private fun wrapDekWithKek(dek: SecretKey, kek: SecretKey): String {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(dek.encoded)

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return base64Encode(combined)
    }

    private fun unwrapDekWithKek(wrappedB64: String, kek: SecretKey): SecretKey {
        val combined = base64Decode(wrappedB64)
        if (combined.size <= IV_SIZE_BYTES) throw IllegalArgumentException("Wrapped DEK too small")

        val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = combined.copyOfRange(IV_SIZE_BYTES, combined.size)

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            kek,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        val raw = cipher.doFinal(cipherText)
        return SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
    }

    private fun unwrapLegacyDek(): SecretKey? {
        val stored = prefs.getString(KEY_DEK_WRAPPED_LEGACY, null) ?: return null
        val wrapped = base64Decode(stored)
        if (wrapped.size <= IV_SIZE_BYTES) return null

        val iv = wrapped.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = wrapped.copyOfRange(IV_SIZE_BYTES, wrapped.size)

        return try {
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateLegacyKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            val raw = cipher.doFinal(cipherText)
            SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
        } catch (_: Exception) {
            null
        }
    }

    // ---- Vault State & Lifecycle ----

    /** Returns true if a vault has been created (either modern KEK-wrapped or legacy). */
    fun hasVault(): Boolean =
        prefs.contains(KEY_DEK_WRAPPED_KEK) || prefs.contains(KEY_DEK_WRAPPED_LEGACY)

    /** Returns true if the vault is currently unlocked and the DEK is available in memory. */
    fun isUnlocked(): Boolean = cachedDek != null

    /** Clears the cached DEK reference. JVM memory erasure is not guaranteed. */
    fun lock() {
        synchronized(keyLock) {
            cachedDek = null
        }
    }

    /**
     * Initializes a new vault with a fresh random DEK, protected by the Master Password
     * via Argon2id KEK.
     */
    fun setupMasterPassword(password: String): Boolean {
        if (password.isEmpty()) return false
        val raw = ByteArray(DEK_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
        val dek = SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)

        val salt = ByteArray(SALT_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
        val kek = deriveKek(password, salt)
        val wrapped = wrapDekWithKek(dek, kek)

        prefs.edit {
            putString(KEY_DEK_WRAPPED_KEK, wrapped)
            putString(KEY_ARGON2_SALT, base64Encode(salt))
            putBoolean(KEY_HAS_DEK, true)
            remove(KEY_DEK_WRAPPED_LEGACY)
        }
        cachedDek = dek
        try { enrollBiometric() } catch (_: Exception) {}
        return true
    }

    /**
     * Attempts to unlock the vault with the master password.
     * If the vault was created in legacy mode, uses [isLegacyPasswordValid] to verify
     * the password, unwraps the old DEK, and transparently migrates it to Argon2id KEK wrapping.
     */
    fun unlockWithPassword(
        password: String,
        isLegacyPasswordValid: ((String) -> Boolean)? = null
    ): Boolean {
        if (password.isEmpty()) return false

        // Modern path: wrapped by Argon2id KEK
        val wrappedKek = prefs.getString(KEY_DEK_WRAPPED_KEK, null)
        val saltStr = prefs.getString(KEY_ARGON2_SALT, null)

        if ((wrappedKek != null) && (saltStr != null)) {
            return try {
                val salt = base64Decode(saltStr)
                val kek = deriveKek(password, salt)
                val dek = unwrapDekWithKek(wrappedKek, kek)
                cachedDek = dek
                try { enrollBiometric() } catch (_: Exception) {}
                true
            } catch (_: Exception) {
                false // Password incorrect or corrupted ciphertext
            }
        }

        // Legacy migration path: wrapped by unauthenticated Keystore key
        if (prefs.contains(KEY_DEK_WRAPPED_LEGACY)) {
            val legacyValid = isLegacyPasswordValid?.invoke(password) ?: false
            if (!legacyValid) return false

            val legacyDek = unwrapLegacyDek() ?: return false
            // Migrate to modern Argon2id KEK
            val salt = ByteArray(SALT_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
            val kek = deriveKek(password, salt)
            val wrapped = wrapDekWithKek(legacyDek, kek)

            prefs.edit {
                putString(KEY_DEK_WRAPPED_KEK, wrapped)
                putString(KEY_ARGON2_SALT, base64Encode(salt))
                putBoolean(KEY_HAS_DEK, true)
                remove(KEY_DEK_WRAPPED_LEGACY)
            }
            cachedDek = legacyDek
            try { enrollBiometric() } catch (_: Exception) {}
            return true
        }

        return false
    }

    /** Changes the master password while the vault is currently unlocked. */
    fun changeMasterPassword(newPassword: String): Boolean {
        val dek = cachedDek ?: return false
        if (newPassword.isEmpty()) return false

        val salt = ByteArray(SALT_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
        val kek = deriveKek(newPassword, salt)
        val wrapped = wrapDekWithKek(dek, kek)

        prefs.edit {
            putString(KEY_DEK_WRAPPED_KEK, wrapped)
            putString(KEY_ARGON2_SALT, base64Encode(salt))
            putBoolean(KEY_HAS_DEK, true)
        }
        return true
    }

    // ---- Hardware Keystore Biometric Slot (RSA OAEP KeyPair) ----

    fun isBiometricEnabled(): Boolean =
        prefs.getBoolean(KEY_BIO_ENABLED, false) && prefs.contains(KEY_DEK_WRAPPED_BIO)

    /**
     * Enrolls the current DEK into the hardware Keystore biometric slot using an RSA keypair.
     * The Public Key encrypts the DEK without requiring authentication.
     * The Private Key requires biometric user authentication to decrypt.
     */
    fun enrollBiometric(): Boolean {
        val dek = cachedDek ?: return false
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            if (keyStore.containsAlias(BIO_KEY_ALIAS)) {
                try { keyStore.deleteEntry(BIO_KEY_ALIAS) } catch (_: Exception) {}
            }

            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                BIO_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setKeySize(2048)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            builder.setInvalidatedByBiometricEnrollment(true)

            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            val certificate = keyStore.getCertificate(BIO_KEY_ALIAS) ?: return false

            val publicKey = certificate.publicKey
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            val oaepSpec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
            val encryptedDek = cipher.doFinal(dek.encoded)

            prefs.edit {
                putString(KEY_DEK_WRAPPED_BIO, base64Encode(encryptedDek))
                putBoolean(KEY_BIO_ENABLED, true)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PasswordCrypto", "enrollBiometric failed", e)
            false
        }
    }

    /**
     * Prepares an initialized DECRYPT cipher for the biometric private key to pass to
     * BiometricPrompt.CryptoObject.
     * Returns null if biometric is not enabled or key was permanently invalidated.
     */
    fun getBiometricDecryptCipher(): Cipher? {
        if (!isBiometricEnabled()) return null
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKey = keyStore.getKey(BIO_KEY_ALIAS, null) as? PrivateKey
            if (privateKey == null) {
                disableBiometric()
                return null
            }

            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            val oaepSpec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            android.util.Log.w("PasswordCrypto", "Biometric key permanently invalidated", e)
            disableBiometric()
            null
        } catch (e: Exception) {
            android.util.Log.e("PasswordCrypto", "getBiometricDecryptCipher failed, disabling stale biometric slot", e)
            disableBiometric()
            null
        }
    }

    /**
     * Unwraps the DEK using the authenticated DECRYPT cipher after biometric verification succeeds.
     */
    fun unlockWithBiometricCipher(authenticatedCipher: Cipher): Boolean {
        val stored = prefs.getString(KEY_DEK_WRAPPED_BIO, null) ?: return false
        return try {
            val encryptedDek = base64Decode(stored)
            val rawDek = authenticatedCipher.doFinal(encryptedDek)
            cachedDek = SecretKeySpec(rawDek, KeyProperties.KEY_ALGORITHM_AES)
            true
        } catch (e: Exception) {
            android.util.Log.e("PasswordCrypto", "unlockWithBiometricCipher failed, disabling stale biometric slot", e)
            disableBiometric()
            false
        }
    }

    /** Disables biometric unlock and removes the auth-bound key. */
    fun disableBiometric() {
        prefs.edit {
            remove(KEY_DEK_WRAPPED_BIO)
            putBoolean(KEY_BIO_ENABLED, false)
        }
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(BIO_KEY_ALIAS)) {
                keyStore.deleteEntry(BIO_KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    /**
     * Completely resets the vault by wiping all stored wrapped keys, salts, and biometric slots.
     */
    fun resetVault() {
        lock()
        disableBiometric()
        prefs.edit {
            remove(KEY_DEK_WRAPPED_KEK)
            remove(KEY_ARGON2_SALT)
            remove(KEY_HAS_DEK)
            remove(KEY_DEK_WRAPPED_LEGACY)
        }
    }

    // ---- Row-level operations ----

    /**
     * Encrypts a plain-text string into the fast envelope format:
     * `2:` + Base64(iv + cipherText).
     *
     * @throws IllegalStateException if the vault is currently locked.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val dek = cachedDek ?: throw IllegalStateException("Vault is locked: cannot encrypt password")
        return NEW_BODY_PREFIX + encryptWithKey(plainText, dek)
    }

    /**
     * Decrypts a payload. Handles both the fast envelope format ("2:...") and
     * legacy Keystore-encrypted payloads.
     *
     * @throws IllegalStateException if the payload requires DEK and the vault is locked.
     */
    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return if (encoded.startsWith(NEW_BODY_PREFIX)) {
            val dek = cachedDek ?: throw IllegalStateException("Vault is locked: cannot decrypt password")
            try {
                decryptWithKey(encoded.removePrefix(NEW_BODY_PREFIX), dek)
            } catch (_: Exception) {
                throw IllegalStateException("Unable to decrypt vault value")
            }
        } else {
            try {
                decryptWithKey(encoded, getOrCreateLegacyKey())
            } catch (_: Exception) {
                throw IllegalStateException("Unable to decrypt vault value")
            }
        }
    }

    /**
     * Encrypts arbitrary binary data (such as a Passkey private key) using the vault DEK.
     * Output format: [12-byte IV] + [AES-GCM ciphertext with 16-byte auth tag].
     */
    fun encryptBytes(data: ByteArray): ByteArray {
        val dek = cachedDek ?: throw IllegalStateException("Vault is locked: cannot encrypt passkey")
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, dek)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return combined
    }

    /**
     * Decrypts binary data previously encrypted with [encryptBytes].
     */
    fun decryptBytes(payload: ByteArray): ByteArray {
        val dek = cachedDek ?: throw IllegalStateException("Vault is locked: cannot decrypt passkey")
        if (payload.size <= IV_SIZE_BYTES) throw IllegalArgumentException("Payload too small")
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            dek,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(cipherText)
    }

    private fun encryptWithKey(plain: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return base64Encode(combined)
    }

    private fun decryptWithKey(payload: String, key: SecretKey): String {
        val combined = base64Decode(payload)
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
