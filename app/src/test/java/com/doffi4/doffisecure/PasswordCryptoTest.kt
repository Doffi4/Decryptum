package com.doffi4.doffisecure

import android.content.SharedPreferences
import com.doffi4.doffisecure.security.PasswordCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class PasswordCryptoTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var crypto: PasswordCrypto

    // Deterministic 256-bit KDF for JVM unit testing
    private val testKdf: (String, ByteArray) -> ByteArray = { password, salt ->
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.digest(password.toByteArray(Charsets.UTF_8))
    }

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        crypto = PasswordCrypto(prefs, testKdf)
        crypto.lock()
    }

    @Test
    fun `initial state has no vault and is locked`() {
        assertFalse("Should not have vault initially", crypto.hasVault())
        assertFalse("Should be locked initially", crypto.isUnlocked())
    }

    @Test
    fun `setupMasterPassword creates vault, unlocks, and wraps DEK`() {
        val success = crypto.setupMasterPassword("SuperSecretMasterPass!2026")
        assertTrue("Setup should succeed", success)
        assertTrue("Vault should exist", crypto.hasVault())
        assertTrue("Vault should be unlocked immediately after setup", crypto.isUnlocked())

        // Verify modern keys are stored in prefs
        assertTrue(prefs.contains(PasswordCrypto.KEY_DEK_WRAPPED_KEK))
        assertTrue(prefs.contains(PasswordCrypto.KEY_ARGON2_SALT))
        assertTrue(prefs.contains(PasswordCrypto.KEY_HAS_DEK))
        assertFalse(prefs.contains(PasswordCrypto.KEY_DEK_WRAPPED_LEGACY))
    }

    @Test
    fun `encrypt and decrypt roundtrip succeeds when unlocked`() {
        crypto.setupMasterPassword("MasterPass123")

        val originalPassword = "my_bank_password_#987654"
        val encrypted = crypto.encrypt(originalPassword)

        assertTrue("Ciphertext must start with 2: prefix", encrypted.startsWith(PasswordCrypto.NEW_BODY_PREFIX))
        val decrypted = crypto.decrypt(encrypted)
        assertEquals("Decrypted password must match original", originalPassword, decrypted)
    }

    @Test
    fun `lock wipes DEK and prevents encrypt and decrypt`() {
        crypto.setupMasterPassword("MasterPass123")
        val encrypted = crypto.encrypt("secret_token_abc")

        crypto.lock()
        assertFalse("Vault should be locked", crypto.isUnlocked())

        // Encrypt should fail with IllegalStateException
        val encryptException = assertThrows(IllegalStateException::class.java) {
            crypto.encrypt("another_secret")
        }
        assertTrue(encryptException.message?.contains("Vault is locked") == true)

        // Decrypt should fail with IllegalStateException
        val decryptException = assertThrows(IllegalStateException::class.java) {
            crypto.decrypt(encrypted)
        }
        assertTrue(decryptException.message?.contains("Vault is locked") == true)
    }

    @Test
    fun `unlock with wrong password fails and keeps vault locked`() {
        crypto.setupMasterPassword("CorrectPassword123")
        val encrypted = crypto.encrypt("classified_data")
        crypto.lock()

        val unlocked = crypto.unlockWithPassword("WrongPassword456")
        assertFalse("Unlock with wrong password must return false", unlocked)
        assertFalse("Vault must remain locked", crypto.isUnlocked())

        assertThrows(IllegalStateException::class.java) {
            crypto.decrypt(encrypted)
        }
    }

    @Test
    fun `unlock with correct password succeeds and allows decryption`() {
        crypto.setupMasterPassword("CorrectPassword123")
        val encrypted = crypto.encrypt("classified_data")
        crypto.lock()

        val unlocked = crypto.unlockWithPassword("CorrectPassword123")
        assertTrue("Unlock with correct password must return true", unlocked)
        assertTrue("Vault must be unlocked", crypto.isUnlocked())

        val decrypted = crypto.decrypt(encrypted)
        assertEquals("classified_data", decrypted)
    }

    @Test
    fun `changeMasterPassword re-wraps DEK with new password and keeps stored data intact`() {
        crypto.setupMasterPassword("InitialPassword")
        val secret = crypto.encrypt("important_secret_phrase")

        // Change password while unlocked
        val changed = crypto.changeMasterPassword("NewSuperPassword")
        assertTrue("Change master password should return true", changed)

        crypto.lock()

        // Old password must now fail
        assertFalse("Old password should fail", crypto.unlockWithPassword("InitialPassword"))
        assertFalse(crypto.isUnlocked())

        // New password must succeed
        assertTrue("New password should succeed", crypto.unlockWithPassword("NewSuperPassword"))
        assertTrue(crypto.isUnlocked())

        // Previously encrypted data must still decrypt correctly with the same DEK
        val decrypted = crypto.decrypt(secret)
        assertEquals("important_secret_phrase", decrypted)
    }

    @Test
    fun `empty passwords and empty inputs handled safely`() {
        assertFalse("Empty password setup should fail", crypto.setupMasterPassword(""))
        assertEquals("", crypto.encrypt(""))
        assertEquals("", crypto.decrypt(""))
    }

    @Test
    fun `corrupted ciphertext fails closed instead of returning encoded secret`() {
        crypto.setupMasterPassword("MasterPass")
        val corruptedPayload = "2:not_a_valid_base64_payload_at_all"
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { crypto.decrypt(corruptedPayload) }
    }

    @Test
    fun `valid format with tampered authentication tag fails closed`() {
        crypto.setupMasterPassword("MasterPass")
        val encoded = crypto.encrypt("synthetic-secret").removePrefix("2:")
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val tampered = "2:" + java.util.Base64.getEncoder().encodeToString(bytes)
        assertThrows(IllegalStateException::class.java) { crypto.decrypt(tampered) }
    }

    @Test
    fun `fresh verification rejects wrong and empty passwords while already unlocked`() {
        crypto.setupMasterPassword("CorrectPassword123")
        assertFalse(crypto.unlockWithPassword("WrongPassword456"))
        assertFalse(crypto.unlockWithPassword(""))
        assertTrue(crypto.unlockWithPassword("CorrectPassword123"))
        assertTrue(crypto.isUnlocked())
    }

    @Test
    fun `resetVault wipes all keys and locks vault`() {
        crypto.setupMasterPassword("Pass123")
        assertTrue(crypto.hasVault())
        assertTrue(crypto.isUnlocked())

        crypto.resetVault()
        assertFalse("Vault must be locked", crypto.isUnlocked())
        assertFalse("Vault keys must be wiped", crypto.hasVault())
        assertFalse(prefs.contains(PasswordCrypto.KEY_DEK_WRAPPED_KEK))
        assertFalse(prefs.contains(PasswordCrypto.KEY_ARGON2_SALT))
        assertFalse(prefs.contains(PasswordCrypto.KEY_HAS_DEK))
    }
}

/**
 * In-memory thread-safe FakeSharedPreferences for JVM unit testing without Android runtime.
 */
class FakeSharedPreferences : SharedPreferences {

    private val data = mutableMapOf<String, Any>()

    override fun getAll(): Map<String, *> = synchronized(data) { HashMap(data) }

    override fun getString(key: String?, defValue: String?): String? =
        synchronized(data) { (data[key] as? String) ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        synchronized(data) { (data[key] as? Set<String>) ?: defValues }

    override fun getInt(key: String?, defValue: Int): Int =
        synchronized(data) { (data[key] as? Int) ?: defValue }

    override fun getLong(key: String?, defValue: Long): Long =
        synchronized(data) { (data[key] as? Long) ?: defValue }

    override fun getFloat(key: String?, defValue: Float): Float =
        synchronized(data) { (data[key] as? Float) ?: defValue }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        synchronized(data) { (data[key] as? Boolean) ?: defValue }

    override fun contains(key: String?): Boolean =
        synchronized(data) { data.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val parent: FakeSharedPreferences) : SharedPreferences.Editor {
        private val modifications = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = values
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = this
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            synchronized(parent.data) {
                if (clear) parent.data.clear()
                for ((k, v) in modifications) {
                    if (v === this) {
                        parent.data.remove(k)
                    } else if (v != null) {
                        parent.data[k] = v
                    } else {
                        parent.data.remove(k)
                    }
                }
            }
        }
    }
}
