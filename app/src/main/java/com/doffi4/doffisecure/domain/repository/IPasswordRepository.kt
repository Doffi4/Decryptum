package com.doffi4.doffisecure.domain.repository

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import kotlinx.coroutines.flow.Flow

interface IPasswordRepository {
    fun getAllPasswords(): Flow<List<Password>>
    fun countPasswords(): Flow<Int>
    fun countEncryptedPasswords(): Flow<Int>
    fun getDuplicateGroups(): Flow<List<DuplicateGroup>>
    suspend fun checkEncryptionIntegrity(): Int
    suspend fun deleteDuplicates(): Int

    /**
     * One-time migration of legacy Keystore-encrypted rows ("enc:" payload
     * without the "2:" marker) into the fast envelope format. Returns the
     * number of rows that were re-encrypted.
     */
    suspend fun migrateLegacyEncryption(): Int
    suspend fun getPasswordById(id: Long): Password?
    suspend fun addPassword(password: Password)
    suspend fun addPasswords(passwords: List<Password>): Int
    suspend fun updatePassword(password: Password)
    suspend fun deletePassword(id: Long)
    suspend fun deleteAllPasswords()
    fun searchPasswords(query: String): Flow<List<Password>>
}
