package com.doffi4.doffisecure.data.repository

import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity
import com.doffi4.doffisecure.data.mapper.PasswordMapper
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.PasswordCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PasswordRepositoryImpl(
    private val passwordDao: PasswordDao,
    private val passwordCrypto: PasswordCrypto,
) : IPasswordRepository {

    private fun requireUnlocked() {
        check(passwordCrypto.isUnlocked()) { "Vault is locked" }
    }

    private fun PasswordDatabaseEntity.decryptPassword(): PasswordDatabaseEntity {
        requireUnlocked()
        // Return the plain-text password and totpSecret for UI consumption.
        // New data is stored encrypted (prefix "enc:"), legacy plain-text rows are returned as-is.
        val decPassword = if (password.startsWith(ENC_PREFIX)) {
            passwordCrypto.decrypt(password.removePrefix(ENC_PREFIX))
        } else {
            password
        }
        val decTotp = totpSecret?.let {
            if (it.startsWith(ENC_PREFIX)) {
                passwordCrypto.decrypt(it.removePrefix(ENC_PREFIX))
            } else {
                it
            }
        }
        requireUnlocked()
        return copy(password = decPassword, totpSecret = decTotp)
    }

    private fun encryptForStorage(plain: String): String {
        // Input is a domain plaintext value. A user's literal "enc:" prefix
        // must not bypass encryption or be interpreted as a storage payload.
        return ENC_PREFIX + passwordCrypto.encrypt(plain)
    }

    private fun encryptNullableForStorage(plain: String?): String? {
        if (plain.isNullOrEmpty()) return plain
        return encryptForStorage(plain)
    }

    override fun getAllPasswords(): Flow<List<Password>> {
        return passwordDao.getAllPasswords()
            .map { entities ->
                // Decrypt only for active subscribers. Never retain an extra singleton
                // plaintext snapshot across a lock or serve stale external DB writes.
                requireUnlocked()
                val decrypted = entities.map {
                    currentCoroutineContext().ensureActive()
                    PasswordMapper.toDomain(it.decryptPassword())
                }
                requireUnlocked()
                decrypted
            }
            // Decrypt on a background dispatcher: Keystore operations are slow,
            // and with a large vault doing it on the main thread causes jank.
            .flowOn(Dispatchers.Default)
            // Skip no-op re-emissions: Room re-queries on every DB invalidation,
            // but if the decrypted payload is byte-identical we don't need to
            // push a fresh list (and a fresh recomposition) to the UI.
            .distinctUntilChanged()
    }

    override fun getAutofillHeaders(): Flow<List<Password>> {
        return passwordDao.getAllPasswords()
            .map { entities ->
                entities.map { entity ->
                    Password(
                        id = entity.id,
                        service = entity.service,
                        username = entity.username,
                        password = "",
                        url = entity.url,
                        createdAt = entity.createdAt
                    )
                }
            }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun countPasswords(): Flow<Int> = passwordDao.countPasswords()

    override fun countEncryptedPasswords(): Flow<Int> = passwordDao.countEncryptedPasswords()

    override fun getDuplicateGroups(): Flow<List<DuplicateGroup>> = passwordDao.getDuplicateGroups()

    override suspend fun deleteDuplicates(): Int = withContext(Dispatchers.IO) {
        passwordDao.deleteDuplicates()
    }

    /**
     * Dev-mode integrity check: every row stored with the "enc:" prefix must be
     * decryptable. Returns the number of rows that failed to decrypt (0 = healthy).
     */
    override suspend fun checkEncryptionIntegrity(): Int = withContext(Dispatchers.IO) {
        passwordDao.getAllPasswords().first().count { entity ->
            var failed = false
            if (entity.password.startsWith(ENC_PREFIX)) {
                try {
                    passwordCrypto.decrypt(entity.password.removePrefix(ENC_PREFIX))
                } catch (_: Exception) {
                    failed = true
                }
            }
            if (!failed && entity.totpSecret?.startsWith(ENC_PREFIX) == true) {
                try {
                    passwordCrypto.decrypt(entity.totpSecret.removePrefix(ENC_PREFIX))
                } catch (_: Exception) {
                    failed = true
                }
            }
            failed
        }
    }

    override suspend fun getPasswordById(id: Long): Password? {
        return withContext(Dispatchers.IO) {
            requireUnlocked()
            val result = passwordDao.getPasswordById(id)?.let { entity ->
                PasswordMapper.toDomain(entity.decryptPassword())
            }
            requireUnlocked()
            result
        }
    }

    override suspend fun addPassword(password: Password) {
        withContext(Dispatchers.IO) {
            passwordDao.insertPassword(
                PasswordMapper.toEntity(password).let {
                    it.copy(
                        password = encryptForStorage(it.password),
                        totpSecret = encryptNullableForStorage(it.totpSecret)
                    )
                }
            )
        }
    }

    override suspend fun addPasswords(passwords: List<Password>): Int {
        return withContext(Dispatchers.IO) {
            if (!passwordCrypto.isUnlocked()) {
                throw IllegalStateException("Vault is locked: cannot encrypt and store passwords")
            }
            // Encrypt SEQUENTIALLY: Android Keystore (especially hardware-backed
            // keymaster on devices like OnePlus) throws IllegalBlockSizeException
            // on concurrent GCM operations. Each row is guarded so a single
            // bad record doesn't fail the whole import. The final DB write is
            // still a single bulk transaction.
            val entities = mutableListOf<PasswordDatabaseEntity>()
            for (p in passwords) {
                try {
                    val entity = PasswordMapper.toEntity(p).let {
                        it.copy(
                            password = encryptForStorage(it.password),
                            totpSecret = encryptNullableForStorage(it.totpSecret)
                        )
                    }
                    entities.add(entity)
                } catch (e: Exception) {
                    android.util.Log.w("PasswordRepository", "Record encryption failed; record skipped")
                }
            }
            if (entities.isNotEmpty()) {
                passwordDao.insertAllPasswords(entities)
            }
            entities.size
        }
    }

    override suspend fun updatePassword(password: Password) {
        withContext(Dispatchers.IO) {
            passwordDao.insertPassword(
                PasswordMapper.toEntity(password).let {
                    it.copy(
                        password = encryptForStorage(it.password),
                        totpSecret = encryptNullableForStorage(it.totpSecret)
                    )
                }
            )
        }
    }

    override suspend fun deletePassword(id: Long) {
        withContext(Dispatchers.IO) {
            passwordDao.deleteById(id)
        }
    }

    override suspend fun deleteAllPasswords() {
        withContext(Dispatchers.IO) {
            passwordDao.deleteAll()
        }
    }

    override fun searchPasswords(query: String): Flow<List<Password>> {
        return passwordDao.searchPasswords(query)
            .map { list ->
                requireUnlocked()
                val result = list.map { entity ->
                    currentCoroutineContext().ensureActive()
                    PasswordMapper.toDomain(entity.decryptPassword())
                }
                requireUnlocked()
                result
            }
            .flowOn(Dispatchers.Default)
    }

    private companion object {
        const val ENC_PREFIX = "enc:"
        const val ENC2_PREFIX = "enc:2:"
    }

    /**
     * Re-encrypts rows stored in the slow legacy Keystore format (`enc:` without
     * the fast "2:" marker) into the envelope format. Runs once at app startup
     * so existing vaults become as fast as newly written data.
     */
    override suspend fun migrateLegacyEncryption(): Int = withContext(Dispatchers.IO) {
        val rows = passwordDao.getAllPasswords().first()
            .filter {
                (it.password.startsWith(ENC_PREFIX) && !it.password.startsWith(ENC2_PREFIX)) ||
                (it.totpSecret?.startsWith(ENC_PREFIX) == true && !it.totpSecret.startsWith(ENC2_PREFIX)) ||
                (!it.totpSecret.isNullOrEmpty() && !it.totpSecret.startsWith(ENC_PREFIX))
            }

        val converted = mutableListOf<PasswordDatabaseEntity>()
        for (entity in rows) {
            try {
                var newPassword = entity.password
                if (entity.password.startsWith(ENC_PREFIX) && !entity.password.startsWith(ENC2_PREFIX)) {
                    val plain = passwordCrypto.decrypt(entity.password.removePrefix(ENC_PREFIX))
                    if (plain.isNotEmpty()) {
                        newPassword = ENC_PREFIX + passwordCrypto.encrypt(plain)
                    }
                }

                var newTotp = entity.totpSecret
                val currentTotp = entity.totpSecret
                if (!currentTotp.isNullOrEmpty()) {
                    if (currentTotp.startsWith(ENC_PREFIX) && !currentTotp.startsWith(ENC2_PREFIX)) {
                        val plainTotp = passwordCrypto.decrypt(currentTotp.removePrefix(ENC_PREFIX))
                        if (plainTotp.isNotEmpty()) {
                            newTotp = ENC_PREFIX + passwordCrypto.encrypt(plainTotp)
                        }
                    } else if (!currentTotp.startsWith(ENC_PREFIX)) {
                        newTotp = ENC_PREFIX + passwordCrypto.encrypt(currentTotp)
                    }
                }

                if (newPassword != entity.password || newTotp != entity.totpSecret) {
                    converted += entity.copy(password = newPassword, totpSecret = newTotp)
                }
            } catch (_: Exception) {
                // Untouched - corrupt or unusual payload; keep as-is.
            }
        }
        if (converted.isNotEmpty()) {
            passwordDao.insertAllPasswords(converted)
        }
        converted.size
    }
}
