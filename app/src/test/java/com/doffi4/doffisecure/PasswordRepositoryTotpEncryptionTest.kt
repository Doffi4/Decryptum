package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity
import com.doffi4.doffisecure.data.repository.PasswordRepositoryImpl
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.security.PasswordCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class PasswordRepositoryTotpEncryptionTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var crypto: PasswordCrypto
    private lateinit var dao: FakePasswordDao
    private lateinit var repository: PasswordRepositoryImpl

    private val testKdf: (String, ByteArray) -> ByteArray = { password, salt ->
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.digest(password.toByteArray(Charsets.UTF_8))
    }

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        crypto = PasswordCrypto(prefs, testKdf)
        crypto.setupMasterPassword("TestMasterPassword123")
        dao = FakePasswordDao()
        repository = PasswordRepositoryImpl(dao, crypto)
    }

    @Test
    fun `addPassword encrypts totpSecret in database entity`() = runBlocking {
        val originalTotp = "JBSWY3DPEHPK3PXP"
        val password = Password(
            id = 1L,
            service = "Google",
            username = "user@gmail.com",
            password = "SecretPassword123",
            url = null,
            createdAt = 0L,
            totpSecret = originalTotp
        )

        repository.addPassword(password)

        val stored = dao.storedPasswords[1L]
        assertNotNull("Stored entity should not be null", stored)
        assertTrue("Stored password must be encrypted", stored!!.password.startsWith("enc:"))
        assertNotNull("Stored totpSecret should not be null", stored.totpSecret)
        assertTrue("Stored totpSecret must be encrypted with enc: prefix", stored.totpSecret!!.startsWith("enc:"))
        assertTrue("Stored totpSecret must not equal plaintext", stored.totpSecret != originalTotp)
    }

    @Test
    fun `getAllPasswords decrypts encrypted totpSecret`() = runBlocking {
        val originalTotp = "otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        val password = Password(
            id = 2L,
            service = "GitHub",
            username = "alice",
            password = "PassWord456!",
            url = null,
            createdAt = 0L,
            totpSecret = originalTotp
        )

        repository.addPassword(password)
        val loadedList = repository.getAllPasswords().first()

        assertEquals(1, loadedList.size)
        assertEquals("GitHub", loadedList[0].service)
        assertEquals("PassWord456!", loadedList[0].password)
        assertEquals(originalTotp, loadedList[0].totpSecret)
    }

    @Test
    fun `legacy unencrypted totpSecret is preserved when reading`() = runBlocking {
        val legacyPlainTotp = "LEGACY_PLAIN_SECRET"
        val legacyEntity = PasswordDatabaseEntity(
            id = 3L,
            service = "LegacyService",
            username = "legacyUser",
            password = "plainPassword",
            totpSecret = legacyPlainTotp
        )
        dao.insertPassword(legacyEntity)

        val loaded = repository.getPasswordById(3L)
        assertNotNull(loaded)
        assertEquals(legacyPlainTotp, loaded!!.totpSecret)
    }

    @Test
    fun `warmed repository rejects locked list detail and search reads`() = runBlocking {
        repository.addPassword(Password(1, "Synthetic", "alice", "secret", null, 0))
        repository.getAllPasswords().first()
        crypto.lock()
        suspend fun rejects(block: suspend () -> Unit) {
            try { block(); org.junit.Assert.fail("Locked read must fail") }
            catch (_: IllegalStateException) { }
        }
        rejects { repository.getAllPasswords().first() }
        rejects { repository.getPasswordById(1) }
        rejects { repository.searchPasswords("Synthetic").first() }
    }

    @Test
    fun `corrupted encrypted row cannot become a successful credential`() = runBlocking {
        dao.insertPassword(PasswordDatabaseEntity(1, "Synthetic", "alice", "enc:2:broken"))
        try { repository.getAllPasswords().first(); org.junit.Assert.fail("Corrupt read must fail") }
        catch (_: IllegalStateException) { }
    }

    @Test
    fun `reads reflect database changes rather than a retained plaintext snapshot`() = runBlocking {
        dao.insertPassword(PasswordDatabaseEntity(1, "Before", "alice", "legacy-value"))
        assertEquals("Before", repository.getAllPasswords().first().single().service)
        dao.insertPassword(PasswordDatabaseEntity(1, "After", "alice", "legacy-value"))
        assertEquals("After", repository.getAllPasswords().first().single().service)
    }

    @Test
    fun `plaintext password resembling storage prefix round trips exactly`() = runBlocking {
        repository.addPassword(Password(1, "Synthetic", "alice", "enc:literal-user-password", null, 0))
        assertEquals("enc:literal-user-password", repository.getPasswordById(1)!!.password)
    }

    @Test
    fun `empty locked vault must not become successful empty analysis input`() = runBlocking {
        crypto.lock()
        try { repository.getAllPasswords().first(); org.junit.Assert.fail("Locked read must fail") }
        catch (_: IllegalStateException) { }
    }

    @Test
    fun `locked picker headers omit both password and TOTP while detail fails`() = runBlocking {
        repository.addPassword(Password(1, "Synthetic", "alice", "synthetic-secret", null, 0, "synthetic-totp"))
        crypto.lock()
        val header = repository.getAutofillHeaders().first().single()
        assertEquals("alice", header.username)
        assertEquals("", header.password)
        org.junit.Assert.assertNull(header.totpSecret)
        try { repository.getPasswordById(header.id); org.junit.Assert.fail("Locked fill must fail") }
        catch (_: IllegalStateException) { }
    }

    private class FakePasswordDao : PasswordDao {
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
