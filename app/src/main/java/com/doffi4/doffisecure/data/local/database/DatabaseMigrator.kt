package com.doffi4.doffisecure.data.local.database

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * Handles automatic migration of legacy unencrypted SQLite databases to SQLCipher.
 *
 * When an existing user updates the app, their database file (password_db) may still be
 * a standard unencrypted SQLite file. Attempting to open it directly with a SQLCipher
 * passphrase would cause a `SQLiteDatabaseCorruptException`.
 *
 * This migrator detects whether the database has the unencrypted SQLite header ("SQLite format 3")
 * and, if so, re-encrypts the entire database using SQLCipher's built-in `sqlcipher_export`.
 */
class DatabaseMigrator(
    private val databaseName: String = "password_db",
) {

    companion object {
        private const val TAG = "DatabaseMigrator"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        init {
            try {
                System.loadLibrary("sqlcipher")
            } catch (_: Throwable) {}
        }
    }

    /**
     * Checks whether the given file is an unencrypted SQLite database by examining
     * the first 16 bytes (the magic header).
     */
    fun isDatabasePlaintext(file: File): Boolean {
        if (!file.exists() || (file.length() < 16)) return false
        return try {
            FileInputStream(file).use { stream ->
                val buffer = ByteArray(16)
                val read = stream.read(buffer)
                (read == 16) && buffer.contentEquals(SQLITE_HEADER)
            }
        } catch (_: Exception) {
            false
        }

    }

    /**
     * Checks if migration is necessary and performs encryption if the database is plaintext.
     * Safe against crashes: writes to a temporary file first and only replaces the original
     * database after successful completion.
     */
    fun migrateIfNeeded(context: Context, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(databaseName)
        if (!dbFile.exists()) {
            // Fresh install: Room will create the encrypted database directly.
            return
        }

        if (!isDatabasePlaintext(dbFile)) {
            // Already encrypted with SQLCipher or not an unencrypted SQLite file.
            return
        }

        Log.i(TAG, "Plaintext SQLite database detected for $databaseName. Starting SQLCipher migration...")

        val encryptedTempFile = File(dbFile.parentFile, "${databaseName}_encrypted.tmp")
        if (encryptedTempFile.exists()) {
            encryptedTempFile.delete()
        }
        encryptedTempFile.createNewFile()

        val hexKey = passphrase.joinToString("") { "%02x".format(it) }

        var plainDb: SQLiteDatabase? = null
        try {
            // Open the unencrypted plaintext database with CREATE_IF_NECESSARY so attached databases can be created/opened
            plainDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
            )

            // Attach temporary file as encrypted
            val escapedPath = encryptedTempFile.absolutePath.replace("'", "''")
            plainDb.execSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY \"x'$hexKey'\";")

            // Export schema and all data into the encrypted database
            plainDb.rawQuery("SELECT sqlcipher_export('encrypted');", null).use { cursor ->
                cursor.moveToFirst()
            }

            // Detach and close
            plainDb.execSQL("DETACH DATABASE encrypted;")
            plainDb.close()
            plainDb = null

            // Delete existing unencrypted database and WAL files
            val walFile = File(dbFile.parentFile, "$databaseName-wal")
            val shmFile = File(dbFile.parentFile, "$databaseName-shm")
            dbFile.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Rename encrypted file into the original database path
            if (!encryptedTempFile.renameTo(dbFile)) {
                throw IllegalStateException("Failed to rename encrypted temporary file to $databaseName")
            }

            Log.i(TAG, "SQLCipher migration completed successfully for $databaseName.")
        } catch (e: Exception) {
            Log.e(TAG, "Migration to SQLCipher failed", e)
            plainDb?.close()
            if (encryptedTempFile.exists()) {
                encryptedTempFile.delete()
            }
            throw IllegalStateException("Failed to migrate plaintext database to SQLCipher", e)
        }
    }
}
