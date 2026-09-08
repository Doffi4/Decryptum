package com.doffi4.doffisecure.data.local.dao

import androidx.room.*
import com.doffi4.doffisecure.data.local.entities.PasskeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasskeyDao {
    @Query("SELECT * FROM passkey_table")
    fun getAllPasskeys(): Flow<List<PasskeyEntity>>

    @Query("SELECT * FROM passkey_table WHERE rpId = :rpId COLLATE NOCASE")
    fun getPasskeysForRpId(rpId: String): Flow<List<PasskeyEntity>>

    @Query("SELECT * FROM passkey_table WHERE rpId = :rpId COLLATE NOCASE")
    suspend fun getPasskeysForRpIdSync(rpId: String): List<PasskeyEntity>

    @Query("SELECT * FROM passkey_table WHERE credentialId = :credentialId LIMIT 1")
    suspend fun getPasskeyByCredentialId(credentialId: ByteArray): PasskeyEntity?

    @Query("SELECT * FROM passkey_table WHERE linkedPasswordId = :passwordId")
    fun getPasskeysByLinkedPasswordId(passwordId: Long): Flow<List<PasskeyEntity>>

    @Query("SELECT * FROM passkey_table WHERE id = :id LIMIT 1")
    suspend fun getPasskeyById(id: Long): PasskeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasskey(passkey: PasskeyEntity): Long

    @Query("UPDATE passkey_table SET signCount = :newCount WHERE id = :id")
    suspend fun updateSignCount(id: Long, newCount: Long)

    @Query("UPDATE passkey_table SET linkedPasswordId = :passwordId WHERE id = :passkeyId")
    suspend fun linkToPassword(passkeyId: Long, passwordId: Long?)

    @Query("DELETE FROM passkey_table WHERE id = :id")
    suspend fun deletePasskey(id: Long)

    @Query("DELETE FROM passkey_table")
    suspend fun deleteAllPasskeys()
}
