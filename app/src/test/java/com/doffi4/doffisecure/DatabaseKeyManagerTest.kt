package com.doffi4.doffisecure

import com.doffi4.doffisecure.security.DatabaseKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import android.content.SharedPreferences
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
    fun `failed key commit blocks this manager even when preferences changed in memory`() {
        val rejectingPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val editor = prefs.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        editor.commit() // Android can update memory even if disk commit fails.
                        return false
                    }
                }
            }
        }
        val manager = DatabaseKeyManager(rejectingPrefs, testSecretKey = testMasterKey)
        repeat(2) { assertThrows(IllegalStateException::class.java) { manager.getPassphrase() } }
    }

    @Test
    fun `partial stored key refuses regeneration`() {
        prefs.edit().putString("encrypted_db_key", "existing blob").commit()
        assertThrows(IllegalStateException::class.java) { keyManager.getPassphrase() }
        assertEquals("existing blob", prefs.getString("encrypted_db_key", null))
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
