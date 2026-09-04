package com.doffi4.doffisecure.data.repository

import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity
import com.doffi4.doffisecure.data.mapper.PasswordMapper
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.PasswordCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PasswordRepositoryImpl(
    private val passwordDao: PasswordDao,
    private val passwordCrypto: PasswordCrypto
) : IPasswordRepository {

    // --- Session decryption cache ---
    // The fully-decrypted vault is held in memory for the process lifetime. That
    // matches the existing design (VaultWarmup already decrypts the whole vault
    // and the UI composables hold plaintext anyway) and is never persisted -
    // cleared naturally on process death and discarded when auto-lock swaps the
    // UI back to LockScreen. Every mutation bumps [cacheGeneration] so the flow
    // below rebuilds exactly once instead of re-decrypting all N rows on every
    // unrelated Room invalidation.
    @Volatile
    private var cachedList: List<Password>? = null
    @Volatile
    private var snapshotGen = -1L
    private val cacheGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    private fun invalidateCache() {
        cacheGeneration.incrementAndGet()
    }

    private fun PasswordDatabaseEntity.decryptPassword(): PasswordDatabaseEntity {
        // Return the plain-text password for UI consumption. New data is stored
        // encrypted (prefix "enc:"), legacy plain-text rows are returned as-is.
        return if (password.startsWith(ENC_PREFIX)) {
            copy(password = passwordCrypto.decrypt(password.removePrefix(ENC_PREFIX)))
        } else {
            this
        }
    }

    private fun encryptForStorage(plain: String): String {
        return if (plain.startsWith(ENC_PREFIX)) plain else ENC_PREFIX + passwordCrypto.encrypt(plain)
    }

    override fun getAllPasswords(): Flow<List<Password>> {
        return passwordDao.getAllPasswords()
            .map { entities ->
                // Session cache: the full plain-text list is expensive to rebuild
                // (N decryptions). VaultWarmup already builds it once at startup,
                // and every edit currently triggers a re-emission + re-decrypt of
                // the WHOLE vault. We keep the latest decrypted snapshot for the
                // process and only rebuild it when a write bumps the generation,
                // so unrelated re-emissions reuse the in-memory list instead of
                // decrypting hundreds of rows again.
                val gen = cacheGeneration.get()
                val snapshot = cachedList
                if (snapshot != null && snapshotGen == gen) {
                    snapshot
                } else {
                    val decrypted = entities.map { entity ->
                        PasswordMapper.toDomain(entity.decryptPassword())
                    }
                    cachedList = decrypted
                    snapshotGen = gen
                    decrypted
                }
            }
            // Decrypt on a background dispatcher: Keystore operations are slow,
            // and with a large vault doing it on the main thread causes jank.
            .flowOn(Dispatchers.Default)
            // Skip no-op re-emissions: Room re-queries on every DB invalidation,
            // but if the decrypted payload is byte-identical we don't need to
            // push a fresh list (and a fresh recomposition) to the UI.
            .distinctUntilChanged()
    }

    override fun countPasswords(): Flow<Int> = passwordDao.countPasswords()

    override fun countEncryptedPasswords(): Flow<Int> = passwordDao.countEncryptedPasswords()

    override fun getDuplicateGroups(): Flow<List<DuplicateGroup>> = passwordDao.getDuplicateGroups()

    override suspend fun deleteDuplicates(): Int = withContext(Dispatchers.IO) {
        invalidateCache()
        passwordDao.deleteDuplicates()
    }

    /**
     * Dev-mode integrity check: every row stored with the "enc:" prefix must be
     * decryptable. Returns the number of rows that failed to decrypt (0 = healthy).
     */
    override suspend fun checkEncryptionIntegrity(): Int = withContext(Dispatchers.IO) {
        passwordDao.getAllPasswords().first().count { entity ->
            if (!entity.password.startsWith(ENC_PREFIX)) {
                false // legacy plain-text row - expected
            } else {
                try {
                    passwordCrypto.decrypt(entity.password.removePrefix(ENC_PREFIX))
                    false
                } catch (_: Exception) {
                    true // row exists but cannot be decrypted
                }
            }
        }
    }

    override suspend fun getPasswordById(id: Long): Password? {
        return withContext(Dispatchers.IO) {
            passwordDao.getPasswordById(id)?.let { entity ->
                PasswordMapper.toDomain(entity.decryptPassword())
            }
        }
    }

    override suspend fun addPassword(password: Password) {
        withContext(Dispatchers.IO) {
            invalidateCache()
            passwordDao.insertPassword(
                PasswordMapper.toEntity(password).let { it.copy(password = encryptForStorage(it.password)) }
            )
        }
    }

    override suspend fun addPasswords(passwords: List<Password>): Int {
        return withContext(Dispatchers.IO) {
            invalidateCache()
            // Encrypt SEQUENTIALLY: Android Keystore (especially hardware-backed
            // keymaster on devices like OnePlus) throws IllegalBlockSizeException
            // on concurrent GCM operations. Each row is guarded so a single
            // bad record doesn't fail the whole import. The final DB write is
            // still a single bulk transaction.
            val entities = mutableListOf<PasswordDatabaseEntity>()
            for (p in passwords) {
                try {
                    val entity = PasswordMapper.toEntity(p)
                        .let { it.copy(password = encryptForStorage(it.password)) }
                    entities.add(entity)
                } catch (_: Exception) {
                    // Skip this record - malformed/unsupported payload
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
            invalidateCache()
            passwordDao.insertPassword(
                PasswordMapper.toEntity(password).let { it.copy(password = encryptForStorage(it.password)) }
            )
        }
    }

    override suspend fun deletePassword(id: Long) {
        withContext(Dispatchers.IO) {
            invalidateCache()
            passwordDao.deleteById(id)
        }
    }

    override suspend fun deleteAllPasswords() {
        withContext(Dispatchers.IO) {
            invalidateCache()
            passwordDao.deleteAll()
        }
    }

    override fun searchPasswords(query: String): Flow<List<Password>> {
        return passwordDao.searchPasswords(query)
            .map { list ->
                list.map { entity ->
                    PasswordMapper.toDomain(entity.decryptPassword())
                }
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
        val legacyRows = passwordDao.getAllPasswords().first()
            .filter { it.password.startsWith(ENC_PREFIX) && !it.password.startsWith(ENC2_PREFIX) }

        val converted = mutableListOf<PasswordDatabaseEntity>()
        for (entity in legacyRows) {
            try {
                val plain = passwordCrypto.decrypt(entity.password.removePrefix(ENC_PREFIX))
                if (plain.isNotEmpty()) {
                    converted += entity.copy(password = ENC_PREFIX + passwordCrypto.encrypt(plain))
                }
            } catch (_: Exception) {
                // Untouched - corrupt or unusual payload; keep as-is.
            }
        }
        if (converted.isNotEmpty()) {
            invalidateCache()
            passwordDao.insertAllPasswords(converted)
        }
        converted.size
    }
}
