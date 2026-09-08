package com.doffi4.doffisecure

import com.doffi4.doffisecure.security.DatabaseKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

class DatabaseKeyManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var testMasterKey: SecretKeySpec
    private lateinit var keyManager: DatabaseKeyManager

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        // Generate a test 256-bit AES master key for unit testing
        val keyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        testMasterKey = SecretKeySpec(keyBytes, "AES")
        keyManager = DatabaseKeyManager(
            prefs = prefs,
            testSecretKey = testMasterKey
        )
    }

    @Test
    fun `getPassphrase returns 32-byte key and persists in prefs`() {
        val passphrase = keyManager.getPassphrase()

        assertNotNull(passphrase)
        assertEquals(32, passphrase.size)

        // Ensure key was encrypted and stored in prefs
        assertTrue("Prefs should contain encrypted key", prefs.contains("encrypted_db_key"))
        assertTrue("Prefs should contain IV", prefs.contains("db_key_iv"))
    }

    @Test
    fun `getPassphrase returns cached identical key on multiple calls`() {
        val first = keyManager.getPassphrase()
        val second = keyManager.getPassphrase()

        assertArrayEquals("Subsequent calls should return identical key bytes", first, second)
    }

    @Test
    fun `new instance with same prefs successfully decrypts and returns existing key`() {
        val originalKey = keyManager.getPassphrase()

        // Create new instance simulating next app launch
        val newManager = DatabaseKeyManager(
            prefs = prefs,
            testSecretKey = testMasterKey
        )
        val reloadedKey = newManager.getPassphrase()

        assertArrayEquals("Reloaded key from SharedPreferences must match original key", originalKey, reloadedKey)
    }
}
