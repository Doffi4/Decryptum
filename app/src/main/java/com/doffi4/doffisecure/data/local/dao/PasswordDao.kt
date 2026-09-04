package com.doffi4.doffisecure.data.local.dao

import androidx.room.*
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity
import com.doffi4.doffisecure.domain.model.DuplicateGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM password_table")
    fun getAllPasswords(): Flow<List<PasswordDatabaseEntity>>

    /** Lightweight count without decrypting any rows (used by dev mode). */
    @Query("SELECT COUNT(*) FROM password_table")
    fun countPasswords(): Flow<Int>

    /** Count of rows whose stored value is encrypted (prefix "enc:"). */
    @Query("SELECT COUNT(*) FROM password_table WHERE password LIKE 'enc:%'")
    fun countEncryptedPasswords(): Flow<Int>

    /** (service, username) groups that occur more than once (dev duplicate detector). */
    @Query(
        "SELECT service || '|' || username AS dup_key, COUNT(*) AS cnt " +
            "FROM password_table " +
            "GROUP BY service, username HAVING cnt > 1"
    )
    fun getDuplicateGroups(): Flow<List<DuplicateGroup>>

    /**
     * Keeps the oldest row per (service, username) and deletes the remaining
     * duplicates in one statement. Returns the number of removed rows.
     */
    @Query(
        "DELETE FROM password_table WHERE id NOT IN (" +
            "SELECT MIN(id) FROM password_table GROUP BY service, username)"
    )
    suspend fun deleteDuplicates(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordDatabaseEntity)

    /** Bulk insert all passwords in a single transaction for fast CSV import. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPasswords(passwords: List<PasswordDatabaseEntity>)

    @Query(
        "SELECT * FROM password_table WHERE " +
            "service LIKE '%' || :searchQuery || '%' COLLATE NOCASE " +
            "OR username LIKE '%' || :searchQuery || '%' COLLATE NOCASE " +
            "OR IFNULL(url, '') LIKE '%' || :searchQuery || '%' COLLATE NOCASE"
    )
    fun searchPasswords(searchQuery: String): Flow<List<PasswordDatabaseEntity>>

    @Query("SELECT * FROM password_table WHERE id = :id")
    suspend fun getPasswordById(id: Long): PasswordDatabaseEntity?

    @Query("DELETE FROM password_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM password_table")
    suspend fun deleteAll()
}
