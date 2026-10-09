package com.doffi4.doffisecure

import androidx.lifecycle.ViewModelStore
import com.doffi4.doffisecure.dev.CpuMonitor
import com.doffi4.doffisecure.dev.TestDataSeedState
import com.doffi4.doffisecure.dev.TestDatasetRequest
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.domain.usecase.*
import com.doffi4.doffisecure.security.*
import com.doffi4.doffisecure.ui.password.DevToolsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class DevToolsSeedingTest {
    private val store = ViewModelStore()
    private lateinit var lock: AppLockManager
    private lateinit var dev: DevModeManager
    private lateinit var vm: DevToolsViewModel
    private val repo = SeedRepository()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val crypto = PasswordCrypto(FakeSharedPreferences()) { password, salt ->
            MessageDigest.getInstance("SHA-256").digest(salt + password.toByteArray())
        }
        lock = AppLockManager(FakeSharedPreferences(), crypto)
        check(lock.setMasterPassword("Synthetic master password for JVM only"))
        dev = DevModeManager(FakeSharedPreferences())
        dev.enableDevMode(true)
        vm = DevToolsViewModel(lock, GetPasswordsUseCase(repo), CountPasswordsUseCase(repo),
            CountEncryptedPasswordsUseCase(repo), CheckEncryptionIntegrityUseCase(repo),
            GetDuplicateGroupsUseCase(repo), DeleteDuplicatesUseCase(repo), ImportPasswordsUseCase(repo),
            DeleteAllPasswordsUseCase(repo), VaultWarmup(repo, crypto), dev, CpuMonitor())
        store.put("dev", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `disabled developer mode cannot write a dataset`() {
        dev.enableDevMode(false)
        vm.insertTestData(TestDatasetRequest.SecurityCheck)
        assertEquals(TestDataSeedState.Disabled, vm.seedState.value)
        assertTrue(repo.rows.isEmpty())
    }

    @Test fun `locked vault cannot write a dataset`() {
        lock.setLocked(true)
        vm.insertTestData(TestDatasetRequest.SecurityCheck)
        assertEquals(TestDataSeedState.Locked, vm.seedState.value)
        assertTrue(repo.rows.isEmpty())
    }

    @Test fun `rapid repeated submissions make one bulk import and report actual partial count`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        repo.insertedCount = 3
        vm.insertTestData(TestDatasetRequest.SecurityCheck)
        vm.insertTestData(TestDatasetRequest.SecurityCheck)
        assertEquals(TestDataSeedState.Running, vm.seedState.value)
        gate.complete(Unit)
        val result = withTimeout(5000) { vm.seedState.first { it is TestDataSeedState.Saved } }
        assertEquals(TestDataSeedState.Saved(3, 4), result)
        assertEquals(4, repo.rows.size)
        assertEquals(1, repo.writes)
    }

    @Test fun `failure exposes safe state without exception secrets and supports another attempt`() = runBlocking {
        repo.fail = true
        vm.insertTestData(TestDatasetRequest.Totp)
        withTimeout(5000) { vm.seedState.first { it == TestDataSeedState.Failed } }
        assertFalse(vm.seedState.value.toString().contains("PRIVATE_EXCEPTION"))
        repo.fail = false
        vm.insertTestData(TestDatasetRequest.Totp)
        val result = withTimeout(5000) { vm.seedState.first { it is TestDataSeedState.Saved } }
        assertEquals(TestDataSeedState.Saved(1, 1), result)
        assertEquals("", repo.rows.single().password)
    }

    private class SeedRepository : IPasswordRepository {
        var rows: List<Password> = emptyList()
        var writes = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        var insertedCount: Int? = null
        override suspend fun addPasswords(passwords: List<Password>): Int {
            gate?.await()
            if (fail) error("PRIVATE_EXCEPTION")
            writes++
            rows = passwords
            return insertedCount ?: passwords.size
        }
        override fun getAllPasswords(): Flow<List<Password>> = flowOf(emptyList())
        override fun getAutofillHeaders(): Flow<List<Password>> = flowOf(emptyList())
        override fun countPasswords(): Flow<Int> = flowOf(0)
        override fun countEncryptedPasswords(): Flow<Int> = flowOf(0)
        override fun getDuplicateGroups(): Flow<List<DuplicateGroup>> = flowOf(emptyList())
        override suspend fun deleteDuplicates() = 0
        override suspend fun checkEncryptionIntegrity() = 0
        override suspend fun getPasswordById(id: Long): Password? = null
        override suspend fun addPassword(password: Password) = Unit
        override suspend fun updatePassword(password: Password) = Unit
        override suspend fun deletePassword(id: Long) = Unit
        override suspend fun deleteAllPasswords() = Unit
        override fun searchPasswords(query: String): Flow<List<Password>> = flowOf(emptyList())
        override suspend fun migrateLegacyEncryption() = 0
    }
}
