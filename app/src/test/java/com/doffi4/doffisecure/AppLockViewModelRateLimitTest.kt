package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.VaultWarmup
import com.doffi4.doffisecure.ui.lock.AppLockViewModel
import com.doffi4.doffisecure.ui.lock.LockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelRateLimitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cryptoPrefs: FakeSharedPreferences
    private lateinit var lockPrefs: FakeSharedPreferences
    private lateinit var devPrefs: FakeSharedPreferences

    private lateinit var crypto: PasswordCrypto
    private lateinit var lockManager: AppLockManager
    private lateinit var devModeManager: DevModeManager
    private lateinit var vaultWarmup: VaultWarmup
    private lateinit var viewModel: AppLockViewModel

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

        vaultWarmup = VaultWarmup(fakeRepo, crypto)
        val deleteUseCase = DeleteAllPasswordsUseCase(fakeRepo)

        // Setup master password
        lockManager.setMasterPassword("CorrectMasterPassword123")
        lockManager.setLocked(true)

        viewModel = AppLockViewModel(
            lockManager = lockManager,
            vaultWarmup = vaultWarmup,
            devModeManager = devModeManager,
            passwordCrypto = crypto,
            deleteAllPasswordsUseCase = deleteUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is locked and not rate limited`() {
        assertEquals(LockState.Locked, viewModel.lockState.value)
        assertEquals(0, viewModel.lockoutSecondsRemaining.value)
    }

    @Test
    fun `entering wrong password 4 times does not trigger lockout`() {
        repeat(4) {
            viewModel.onPasswordChange("WrongPassword")
            viewModel.submit()
            assertEquals(0, viewModel.lockoutSecondsRemaining.value)
            assertEquals(LockState.Locked, viewModel.lockState.value)
            assertNotNull(viewModel.input.value.error)
        }
    }

    @Test
    fun `entering wrong password 5 times triggers 30 second lockout`() {
        repeat(5) {
            viewModel.onPasswordChange("WrongPassword")
            viewModel.submit()
        }

        assertTrue("Should be locked out after 5 failures", viewModel.lockoutSecondsRemaining.value > 0)
        assertEquals(30, viewModel.lockoutSecondsRemaining.value)
        assertNotNull(viewModel.input.value.error)

        // Attempting to submit while locked out is rejected
        viewModel.onPasswordChange("CorrectMasterPassword123")
        viewModel.submit()
        assertEquals(LockState.Locked, viewModel.lockState.value)
        assertTrue("Still locked out", viewModel.lockoutSecondsRemaining.value > 0)
    }

    @Test
    fun `successful unlock with correct password resets failed attempts`() {
        // 2 wrong attempts
        repeat(2) {
            viewModel.onPasswordChange("WrongPassword")
            viewModel.submit()
        }
        assertEquals(0, viewModel.lockoutSecondsRemaining.value)

        // Correct password
        viewModel.onPasswordChange("CorrectMasterPassword123")
        viewModel.submit()

        assertEquals(LockState.Unlocked, viewModel.lockState.value)
        assertEquals(0, viewModel.lockoutSecondsRemaining.value)
    }

    @Test
    fun `lockout persists across ViewModel recreation (simulating app restart)`() {
        // Trigger 30 second lockout
        repeat(5) {
            viewModel.onPasswordChange("WrongPassword")
            viewModel.submit()
        }
        assertTrue("Should be locked out", viewModel.lockoutSecondsRemaining.value > 0)

        // Simulate app restart / ViewModel recreation with the same dependencies/prefs
        val restartedViewModel = AppLockViewModel(
            lockManager = lockManager,
            vaultWarmup = vaultWarmup,
            devModeManager = devModeManager,
            passwordCrypto = crypto,
            deleteAllPasswordsUseCase = DeleteAllPasswordsUseCase(object : IPasswordRepository {
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
            })
        )

        // The new ViewModel instance must immediately be in lockout state!
        assertTrue("Recreated ViewModel should still be locked out", restartedViewModel.lockoutSecondsRemaining.value > 0)
        assertNotNull(restartedViewModel.input.value.error)
    }

    @Test
    fun `failed attempts count persists across ViewModel recreation (simulating app restart)`() {
        // 4 wrong attempts
        repeat(4) {
            viewModel.onPasswordChange("WrongPassword")
            viewModel.submit()
        }
        assertEquals(0, viewModel.lockoutSecondsRemaining.value)

        // Simulate app restart before 5th attempt
        val restartedViewModel = AppLockViewModel(
            lockManager = lockManager,
            vaultWarmup = vaultWarmup,
            devModeManager = devModeManager,
            passwordCrypto = crypto,
            deleteAllPasswordsUseCase = DeleteAllPasswordsUseCase(object : IPasswordRepository {
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
            })
        )

        // 1 more wrong attempt on restarted VM should trigger lockout
        restartedViewModel.onPasswordChange("WrongPassword")
        restartedViewModel.submit()

        assertTrue("5th attempt across restarts must trigger lockout", restartedViewModel.lockoutSecondsRemaining.value > 0)
    }

    @Test
    fun `when lockout timer naturally finishes, failed attempts counter is reset allowing new attempts`() {
        // Trigger 30-sec lockout
        repeat(5) {
            viewModel.onPasswordChange("WrongPassword")
            viewModel.submit()
        }
        assertEquals(30, viewModel.lockoutSecondsRemaining.value)

        // Advance dispatcher time past the lockout duration
        testDispatcher.scheduler.advanceTimeBy(31_000)

        // Lockout should now be finished and failed attempts reset to 0
        assertEquals(0, viewModel.lockoutSecondsRemaining.value)
        assertEquals(0, lockManager.getFailedAttempts())

        // 1 wrong attempt should NOT trigger lockout immediately
        viewModel.onPasswordChange("WrongPasswordAgain")
        viewModel.submit()
        assertEquals("Single failure after lockout expiration should not re-trigger lockout", 0, viewModel.lockoutSecondsRemaining.value)
    }

    @Test
    fun `restarting app after lockout period has elapsed clears lockout and resets failed attempts`() {
        // Set expired lockout timestamp in SharedPreferences
        lockManager.setLockoutUntilTimestamp(System.currentTimeMillis() - 5000L)
        lockManager.setFailedAttempts(5)

        val restartedViewModel = AppLockViewModel(
            lockManager = lockManager,
            vaultWarmup = vaultWarmup,
            devModeManager = devModeManager,
            passwordCrypto = crypto,
            deleteAllPasswordsUseCase = DeleteAllPasswordsUseCase(object : IPasswordRepository {
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
            })
        )

        assertEquals(0, restartedViewModel.lockoutSecondsRemaining.value)
        assertEquals(0, lockManager.getFailedAttempts())
        assertEquals(0L, lockManager.getLockoutUntilTimestamp())
    }
}
