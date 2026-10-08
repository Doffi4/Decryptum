package com.doffi4.doffisecure.data.local.database

import android.content.Context
import android.util.Log
import android.system.Os
import android.system.OsConstants
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
     * Preserve the source until a validated, synced export can replace it atomically.
     * Native power-loss/low-space behavior must also be verified on Android devices.
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
        // These are stale candidates, never the active database. Failed cleanup aborts.
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            Files.deleteIfExists(File(encryptedTempFile.path + suffix).toPath())
        }
        // SQLCipher 4.6.1 ATTACH inherits the connection's open flags. Keep
        // CREATE disabled for the source, but provide an existing empty target.
        check(encryptedTempFile.createNewFile()) { "Could not create migration candidate" }

        val hexKey = passphrase.joinToString("") { "%02x".format(it) }

        var plainDb: SQLiteDatabase? = null
        try {
            // Never create a missing source here. SQLite checkpoints committed WAL
            // records before switching to DELETE mode; busy/failed transitions abort.
            plainDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            plainDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Source WAL is busy" }
            }
            plainDb.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("delete", true)) {
                    "Could not leave source WAL mode"
                }
            }
            val sourceVersion = plainDb.version
            val sourceSchema = schema(plainDb)
            val sourceCounts = tableCounts(plainDb)

            // Attach temporary file as encrypted
            val escapedPath = encryptedTempFile.absolutePath.replace("'", "''")
            plainDb.execSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY \"x'$hexKey'\";")

            // Export schema and all data into the encrypted database
            plainDb.rawQuery("SELECT sqlcipher_export('encrypted');", null).use { cursor ->
                check(cursor.moveToFirst()) { "Export did not complete" }
            }
            // sqlcipher_export explicitly does not copy user_version.
            plainDb.execSQL("PRAGMA encrypted.user_version=$sourceVersion")

            // Detach and close
            plainDb.execSQL("DETACH DATABASE encrypted;")
            plainDb.close()
            plainDb = null

            SQLiteDatabase.openDatabase(
                encryptedTempFile.absolutePath, "x'$hexKey'", null,
                SQLiteDatabase.OPEN_READONLY, null,
            ).use { exported ->
                exported.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                        "Export failed integrity validation"
                    }
                }
                check(exported.version == sourceVersion) { "Export changed schema version" }
                check(schema(exported) == sourceSchema) { "Export changed schema" }
                check(tableCounts(exported) == sourceCounts) { "Export changed row counts" }
            }
            check(!isDatabasePlaintext(encryptedTempFile)) { "Export is not encrypted" }
            RandomAccessFile(encryptedTempFile, "rw").use { it.fd.sync() }
            // DELETE-mode source is closed/checkpointed. Do not replay old WAL onto
            // encrypted pages. Failure to remove a sidecar leaves the source intact.
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                Files.deleteIfExists(File(dbFile.path + suffix).toPath())
            }
            syncDirectory(checkNotNull(dbFile.parentFile))
            replaceDatabaseFile(dbFile, encryptedTempFile)
            syncDirectory(checkNotNull(dbFile.parentFile))

            Log.i(TAG, "SQLCipher migration completed successfully for $databaseName.")
        } catch (e: Exception) {
            // SQL exceptions may contain the ATTACH statement/key: no exception dump.
            Log.e(TAG, "Migration to SQLCipher failed; database preserved")
            plainDb?.close()
            throw IllegalStateException("Failed to migrate plaintext database to SQLCipher", e)
        }
    }

    private fun schema(db: SQLiteDatabase): List<Pair<String, String?>> =
        db.rawQuery("SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name", null)
            .use { cursor -> buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            } }

    private fun tableCounts(db: SQLiteDatabase): Map<String, Long> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null)
            .use { cursor -> buildMap {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val quoted = name.replace("\"", "\"\"")
                    db.rawQuery("SELECT count(*) FROM \"$quoted\"", null).use { count ->
                        check(count.moveToFirst())
                        put(name, count.getLong(0))
                    }
                }
            } }

    private fun syncDirectory(directory: File) {
        check(directory.isDirectory)
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}

internal fun replaceDatabaseFile(
    source: File,
    encrypted: File,
    move: (Path, Path) -> Unit = { from, to ->
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
) {
    move(encrypted.toPath(), source.toPath())
}
