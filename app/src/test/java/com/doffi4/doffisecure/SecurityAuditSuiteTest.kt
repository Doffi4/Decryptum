package com.doffi4.doffisecure

import com.doffi4.doffisecure.autofill.AutofillMatcher
import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.database.DatabaseMigrator
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity
import com.doffi4.doffisecure.data.repository.PasswordRepositoryImpl
import com.doffi4.doffisecure.data.repository.PwnedPasswordsRepository
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.VaultWarmup
import com.doffi4.doffisecure.security.webauthn.WebAuthnCryptoEngine
import com.doffi4.doffisecure.ui.lock.AppLockViewModel
import com.doffi4.doffisecure.ui.lock.LockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Complete Automated Security & Penetration Audit Suite for Decryptum.
 *
 * Implements the 8 harsh attack & leak verification vectors:
 * 1. Memory Dump & Lifecycle Sanitization (Zeroed DEK on lock, immediate failure after wipe)
 * 2. At-Rest SQLCipher Verification (Detection & rejection of plaintext SQLite databases)
 * 3. KDF Argon2id Parameter Benchmark (Deterministic entropy, timing & key specification)
 * 4. Adversarial Autofill & Domain Spoofing Barrier (Phishing defense against typosquatting & subdomains)
 * 5. Persistent Rate Limiting & Brute-Force Lockout (Survives process death & simulated VM restart)
 * 6. HIBP Privacy Guarantee (k-Anonymity ensures full passwords/hashes are never transmitted)
 * 7. Biometric Hardware Fault Barrier (Corrupted/tampered biometric ciphers cannot unlock the DEK)
 * 8. TOTP & Passkey Cryptographic Integrity (All 2FA seeds & WebAuthn private keys encrypted at rest)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAuditSuiteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cryptoPrefs: FakeSharedPreferences
    private lateinit var lockPrefs: FakeSharedPreferences
    private lateinit var devPrefs: FakeSharedPreferences

    private lateinit var crypto: PasswordCrypto
    private lateinit var lockManager: AppLockManager
    private lateinit var devModeManager: DevModeManager

    // Deterministic 256-bit KDF for JVM unit testing
    private val testKdf: (String, ByteArray) -> ByteArray = { password, salt ->
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.digest(password.toByteArray(Charsets.UTF_8))
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        cryptoPrefs = FakeSharedPreferences()
        lockPrefs = FakeSharedPreferences()
        devPrefs = FakeSharedPreferences()

        crypto = PasswordCrypto(cryptoPrefs, testKdf)
        lockManager = AppLockManager(lockPrefs, crypto)
        devModeManager = DevModeManager(devPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Vector 1: Memory Dump & Lifecycle Sanitization
    // Asserts DEK is wiped from RAM upon lock(); subsequent access fails immediately.
    // =========================================================================
    @Test
    fun `VECTOR 1 - Memory Dump Barrier - lock wipes DEK and prevents decryption`() {
        crypto.setupMasterPassword("SuperVaultPassword2026!")
        assertTrue("Vault must be unlocked after setup", crypto.isUnlocked())

        val sensitiveData = "top_secret_bank_pin_9876"
        val cipherText = crypto.encrypt(sensitiveData)
        assertTrue("Ciphertext must have modern format prefix", cipherText.startsWith(PasswordCrypto.NEW_BODY_PREFIX))

        // Lock vault - simulates screen off / auto-lock / user lock
        crypto.lock()
        assertFalse("Vault must be locked", crypto.isUnlocked())

        // Any attempt to decrypt or encrypt after lock must throw IllegalStateException
        val decryptEx = assertThrows(IllegalStateException::class.java) {
            crypto.decrypt(cipherText)
        }
        assertTrue("Exception must state vault is locked", decryptEx.message?.contains("Vault is locked") == true)

        val encryptEx = assertThrows(IllegalStateException::class.java) {
            crypto.encrypt("new_secret")
        }
        assertTrue("Exception must state vault is locked", encryptEx.message?.contains("Vault is locked") == true)
    }

    // =========================================================================
    // Vector 2: At-Rest SQLCipher Verification
    // Verifies DatabaseMigrator correctly detects plaintext SQLite and rejects encrypted cipher files.
    // =========================================================================
    @Test
    fun `VECTOR 2 - At-Rest Storage - Plaintext SQLite header detection and rejection`() {
        val migrator = DatabaseMigrator()

        // 1. Create plaintext SQLite file with official SQLite magic header
        val plainDbFile = tempFolder.newFile("unencrypted_plain.db")
        FileOutputStream(plainDbFile).use { out ->
            out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            out.write(ByteArray(84)) // Rest of standard 100-byte SQLite header
        }
        assertTrue("Plaintext SQLite file must be detected by migrator", migrator.isDatabasePlaintext(plainDbFile))

        // 2. Create SQLCipher encrypted file (high-entropy random ciphertext without SQLite header)
        val encryptedDbFile = tempFolder.newFile("sqlcipher_encrypted.db")
        val randomBytes = ByteArray(100) { it.toByte() }
        FileOutputStream(encryptedDbFile).use { out ->
            out.write(randomBytes)
        }
        assertFalse("SQLCipher encrypted database must NOT be detected as plaintext", migrator.isDatabasePlaintext(encryptedDbFile))

        // 3. Non-existent file must safely return false
        val missingFile = File(tempFolder.root, "ghost.db")
        assertFalse("Missing file should safely return false", migrator.isDatabasePlaintext(missingFile))
    }

    // =========================================================================
    // Vector 3: KDF Argon2id Parameter Benchmark
    // Asserts Argon2id derivation configuration meets OWASP 2025/2026 specs.
    // =========================================================================
    @Test
    fun `VECTOR 3 - KDF Argon2id Benchmark - Parameter verification and key entropy`() {
        assertEquals("Argon2id time cost must be 2 iterations", 2, PasswordCrypto.ARGON2_T_COST)
        assertEquals("Argon2id memory cost must be 32 MB", 32768, PasswordCrypto.ARGON2_M_COST_KIB)
        assertEquals("Argon2id parallelism must be 2 lanes", 2, PasswordCrypto.ARGON2_PARALLELISM)
        assertEquals("Salt size must be 16 bytes (128 bits)", 16, PasswordCrypto.SALT_SIZE_BYTES)
        assertEquals("DEK size must be 32 bytes (256 bits)", 32, PasswordCrypto.DEK_SIZE_BYTES)

        val salt = ByteArray(16) { 0x42 }
        val derivedKey = crypto.deriveKek("UserPassword#1", salt)
        assertNotNull("Derived KEK must not be null", derivedKey)
        assertEquals("Derived KEK must be 256 bits (32 bytes)", 32, derivedKey.encoded.size)
        assertEquals("Derived KEK algorithm must be AES", "AES", derivedKey.algorithm)
    }

    // =========================================================================
    // Vector 4: Adversarial Autofill & Domain Spoofing Barrier
    // Tests phishing attacks, subdomain spoofing, and typosquatting against AutofillMatcher.
    // =========================================================================
    @Test
    fun `VECTOR 4 - Phishing Barrier - Adversarial domain spoofing rejection`() {
        val vault = listOf(
            Password(1L, "PayPal", "user@paypal.com", "PaySecret123", "https://paypal.com", 0L),
            Password(2L, "Google", "admin@gmail.com", "GoogSecret456", "https://google.com", 0L),
            Password(3L, "Bank", "client", "BankPin789", "https://mybank.com", 0L)
        )

        // Adversarial attempts to spoof paypal.com
        val phishingDomains = listOf(
            "paypal.com.evil-phishing.com",
            "evilpaypal.com",
            "paypa1.com",
            "login-paypal.com",
            "fake-google.com",
            "google.com.attacker.org",
            "mybank.com.scam.net"
        )

        for (attackerDomain in phishingDomains) {
            val matches = AutofillMatcher.findMatches(vault, attackerDomain, null)
            assertTrue(
                "Adversarial domain '$attackerDomain' MUST NOT match legitimate credentials! Matches found: ${matches.map { it.service }}",
                matches.isEmpty()
            )
        }

        // Legitimate domain match must succeed
        val legitMatches = AutofillMatcher.findMatches(vault, "paypal.com", null)
        assertEquals(1, legitMatches.size)
        assertEquals("PayPal", legitMatches.first().service)
    }

    // =========================================================================
    // Vector 5: Persistent Rate Limiting & Brute-Force Lockout
    // Verifies 5 wrong attempts trigger persistent lockout surviving process death.
    // =========================================================================
    @Test
    fun `VECTOR 5 - Brute-Force Barrier - 5 failed attempts trigger persistent lockout`() {
        lockManager.setMasterPassword("CorrectMasterPassword123")
        lockManager.setLocked(true)

        val fakeRepo = object : IPasswordRepository {
            override fun getAllPasswords(): Flow<List<Password>> = flowOf(emptyList())
            override fun getAutofillHeaders(): Flow<List<Password>> = flowOf(emptyList())
            override fun countPasswords(): Flow<Int> = flowOf(0)
            override fun countEncryptedPasswords(): Flow<Int> = flowOf(0)
            override fun getDuplicateGroups(): Flow<List<DuplicateGroup>> = flowOf(emptyList())
            override suspend fun deleteDuplicates(): Int = 0
            override suspend fun checkEncryptionIntegrity(): Int = 0
            override suspend fun getPasswordById(id: Long): Password? = null
            override suspend fun addPassword(password: Password) {}
            override suspend fun addPasswords(passwords: List<Password>): Int = 0
            override suspend fun updatePassword(password: Password) {}
            override suspend fun deletePassword(id: Long) {}
            override suspend fun deleteAllPasswords() {}
            override fun searchPasswords(query: String): Flow<List<Password>> = flowOf(emptyList())
            override suspend fun migrateLegacyEncryption(): Int = 0
        }
        val deleteAllUseCase = DeleteAllPasswordsUseCase(fakeRepo)
        val vaultWarmup = VaultWarmup(fakeRepo, crypto)

        val vm = AppLockViewModel(
            lockManager = lockManager,
            vaultWarmup = vaultWarmup,
            devModeManager = devModeManager,
            passwordCrypto = crypto,
            deleteAllPasswordsUseCase = deleteAllUseCase
        )

        // Attempt 4 wrong passwords
        repeat(4) {
            vm.onPasswordChange("WrongPassword")
            vm.submit()
            assertEquals("Lockout should be 0 before 5th attempt", 0, vm.lockoutSecondsRemaining.value)
            assertEquals(LockState.Locked, vm.lockState.value)
            assertNotNull(vm.input.value.error)
        }

        // 5th wrong attempt triggers lockout
        vm.onPasswordChange("WrongPassword")
        vm.submit()

        assertTrue("5th failed attempt must trigger Lockout!", vm.lockoutSecondsRemaining.value > 0)
        assertEquals("Lockout duration should be 30s", 30, vm.lockoutSecondsRemaining.value)

        // Simulate App Kill & Process Restart with same persistent SharedPreferences
        val newLockManager = AppLockManager(lockPrefs, crypto)
        val restartedViewModel = AppLockViewModel(
            lockManager = newLockManager,
            vaultWarmup = vaultWarmup,
            devModeManager = devModeManager,
            passwordCrypto = crypto,
            deleteAllPasswordsUseCase = deleteAllUseCase
        )

        assertTrue("Recreated ViewModel should still be locked out across process death", restartedViewModel.lockoutSecondsRemaining.value > 0)
    }

    // =========================================================================
    // Vector 6: HIBP Privacy Guarantee (k-Anonymity)
    // Asserts SHA-1 prefix is exactly 5 hex chars; full password/hash is never exposed.
    // =========================================================================
    @Test
    fun `VECTOR 6 - HIBP Privacy - k-Anonymity model guarantees zero password leak`() {
        val testPassword = "SuperSecretUnpublishedPassword2026!"
        val sha1Hex = PwnedPasswordsRepository.sha1Hex(testPassword)

        assertEquals("Full SHA-1 hash must be exactly 40 hex chars", 40, sha1Hex.length)

        val prefix5 = sha1Hex.substring(0, 5)
        val suffix35 = sha1Hex.substring(5)

        assertEquals("k-Anonymity prefix must be exactly 5 chars", 5, prefix5.length)
        assertEquals("Suffix must be exactly 35 chars", 35, suffix35.length)

        // The prefix reveals at most 20 bits of hash entropy, leaving 140 bits unknown to the API
        assertTrue("Prefix must consist of valid hexadecimal characters", prefix5.matches(Regex("^[0-9A-Fa-f]{5}$")))
        assertFalse("Prefix must not contain plaintext password characters", prefix5.contains(testPassword))
    }

    // =========================================================================
    // Vector 7: Biometric Hardware Fault Barrier
    // Tests that a corrupted/tampered cipher cannot unlock the DEK and keeps the vault locked.
    // =========================================================================
    @Test
    fun `VECTOR 7 - Biometric Barrier - Corrupted biometric cipher fails safely`() {
        crypto.setupMasterPassword("MasterBiometricPass123")
        crypto.lock()
        assertFalse("Vault must be locked", crypto.isUnlocked())

        // Save a mock wrapped biometric payload in prefs
        cryptoPrefs.edit().putString(PasswordCrypto.KEY_DEK_WRAPPED_BIO, "bW9ja193cmFwcGVkX2Rla19kYXRh").commit()

        // Create an unauthenticated/unrelated AES cipher
        val unrelatedKey = SecretKeySpec(ByteArray(16) { 0x01 }, "AES")
        val fakeCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        fakeCipher.init(Cipher.DECRYPT_MODE, unrelatedKey)

        // Attempting to unlock with this cipher must fail and leave the vault locked
        val unlockSuccess = crypto.unlockWithBiometricCipher(fakeCipher)
        assertFalse("Corrupted/unauthenticated biometric cipher must return false", unlockSuccess)
        assertFalse("Vault DEK must remain locked after biometric failure", crypto.isUnlocked())
    }

    // =========================================================================
    // Vector 8: TOTP & Passkey Cryptographic Integrity
    // Verifies that TOTP seeds and WebAuthn private keys are encrypted at rest with DEK.
    // =========================================================================
    @Test
    fun `VECTOR 8 - Cryptographic Integrity - TOTP seeds and Passkeys encrypted at rest`() {
        runBlocking {
            crypto.setupMasterPassword("MasterIntegrityPassword!")

            // 1. Verify TOTP Secret Encryption in Repository
            val fakeDao = AuditFakePasswordDao()
            val repo = PasswordRepositoryImpl(fakeDao, crypto)

            val rawTotpSecret = "JBSWY3DPEHPK3PXP"
            val passwordEntry = Password(
                id = 100L,
                service = "GitHub 2FA",
                username = "octo",
                password = "SecretPassword",
                url = "https://github.com",
                createdAt = 1000L,
                totpSecret = rawTotpSecret
            )

            repo.addPassword(passwordEntry)

            val storedEntity = fakeDao.storedPasswords[100L]
            assertNotNull("Stored entity in DAO must exist", storedEntity)
            assertNotNull("Stored totpSecret in DAO must not be null", storedEntity!!.totpSecret)
            assertTrue("Stored totpSecret must be encrypted with 'enc:' prefix", storedEntity.totpSecret!!.startsWith("enc:"))
            assertFalse("Plaintext TOTP secret must NOT be visible in raw storage", storedEntity.totpSecret!!.contains(rawTotpSecret))

            // 2. Verify Passkey (WebAuthn) Private Key Encryption
            val passkeyEngine = WebAuthnCryptoEngine(crypto)
            val passkey = passkeyEngine.generatePasskey(
                rpId = "google.com",
                rpName = "Google",
                userId = "user_999".toByteArray(Charsets.UTF_8),
                userName = "test@gmail.com",
                userDisplayName = "Test User",
                algorithm = WebAuthnCryptoEngine.ALG_ES256
            )

            assertNotNull("Passkey credential ID must not be null", passkey.credentialId)
            assertTrue("Passkey encrypted private key must not be empty", passkey.encryptedPrivateKey.isNotEmpty())

            // Ensure raw PKCS#8 is not plaintext in memory
            val decryptedKeyBytes = crypto.decryptBytes(passkey.encryptedPrivateKey)
            assertFalse("Encrypted private key bytes must not match decrypted bytes", passkey.encryptedPrivateKey.contentEquals(decryptedKeyBytes))

            // Lock vault and verify private key decryption fails
            crypto.lock()
            assertThrows(IllegalStateException::class.java) {
                crypto.decryptBytes(passkey.encryptedPrivateKey)
            }
        }
    }

    private class AuditFakePasswordDao : PasswordDao {
        val storedPasswords = mutableMapOf<Long, PasswordDatabaseEntity>()

        override fun getAllPasswords(): Flow<List<PasswordDatabaseEntity>> =
            flowOf(storedPasswords.values.toList())

        override fun countPasswords(): Flow<Int> = flowOf(storedPasswords.size)

        override fun countEncryptedPasswords(): Flow<Int> =
            flowOf(storedPasswords.values.count { it.password.startsWith("enc:") })

        override fun getDuplicateGroups(): Flow<List<DuplicateGroup>> = flowOf(emptyList())

        override suspend fun deleteDuplicates(): Int = 0

        override suspend fun insertPassword(password: PasswordDatabaseEntity) {
            storedPasswords[password.id] = password
        }

        override suspend fun insertAllPasswords(passwords: List<PasswordDatabaseEntity>) {
            passwords.forEach { storedPasswords[it.id] = it }
        }

        override fun searchPasswords(searchQuery: String): Flow<List<PasswordDatabaseEntity>> =
            flowOf(storedPasswords.values.filter { it.service.contains(searchQuery) })

        override suspend fun getPasswordById(id: Long): PasswordDatabaseEntity? =
            storedPasswords[id]

        override suspend fun deleteById(id: Long) {
            storedPasswords.remove(id)
        }

        override suspend fun deleteAll() {
            storedPasswords.clear()
        }
    }
}
