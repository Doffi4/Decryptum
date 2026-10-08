package com.doffi4.doffisecure

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.doffi4.doffisecure.data.local.database.AppDatabase
import com.doffi4.doffisecure.data.local.database.DatabaseMigrator
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Disposable fixtures only: never open password_db or the owner's key preferences. */
class DatabaseMigrationAndroidTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val rawKey get() = "x'${key.joinToString("") { "%02x".format(it) }}'"

    @Test fun legacyV2WithCommittedWalMigratesAndRoomUpgradesToV4() {
        val sourceName = "migration_fixture_${UUID.randomUUID()}"
        val targetName = "migration_target_${UUID.randomUUID()}"
        try {
            val sourceFile = context.getDatabasePath(sourceName)
            sourceFile.parentFile!!.mkdirs()
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(sourceFile, null).use { db ->
                db.execSQL("CREATE TABLE password_table (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, url TEXT, createdAt INTEGER NOT NULL)")
                db.version = 2
                db.enableWriteAheadLogging()
                db.execSQL("INSERT INTO password_table VALUES (1, 'Synthetic service', 'synthetic-user', 'fixture-only-secret', NULL, 0)")
                val wal = File(sourceFile.path + "-wal")
                assertTrue("Fixture must contain committed WAL pages", wal.length() > 0)
                // Copy the complete synthetic on-disk snapshot before closing its writer.
                sourceFile.copyTo(context.getDatabasePath(targetName))
                wal.copyTo(File(context.getDatabasePath(targetName).path + "-wal"))
            }
            val migrator = DatabaseMigrator(targetName)
            migrator.migrateIfNeeded(context, key)
            assertFalse(migrator.isDatabasePlaintext(context.getDatabasePath(targetName)))
            SQLiteDatabase.openDatabase(context.getDatabasePath(targetName).path, rawKey, null, SQLiteDatabase.OPEN_READONLY, null).use { db ->
                assertEquals(2, db.version)
                db.rawQuery("SELECT username, password FROM password_table", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("synthetic-user", cursor.getString(0))
                    assertEquals("fixture-only-secret", cursor.getString(1))
                    assertFalse(cursor.moveToNext())
                }
            }
            val room = Room.databaseBuilder(context, AppDatabase::class.java, targetName)
                .openHelperFactory(SupportOpenHelperFactory(rawKey.toByteArray(Charsets.US_ASCII)))
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()
            try {
                    assertEquals(4, room.openHelper.writableDatabase.version)
                    room.openHelper.writableDatabase.query("SELECT totpSecret FROM password_table").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue(cursor.isNull(0))
                }
            } finally {
                room.close()
            }
        } finally {
            cleanup(sourceName)
            cleanup(targetName)
        }
    }

    @Test fun alreadyEncryptedDatabaseIsUnchangedOnRepeatedMigration() {
        System.loadLibrary("sqlcipher")
        val name = "migration_encrypted_${UUID.randomUUID()}"
        try {
            val file = context.getDatabasePath(name)
            file.parentFile!!.mkdirs()
            SQLiteDatabase.openDatabase(file.path, rawKey, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null).use { db ->
                db.execSQL("CREATE TABLE fixture (value TEXT NOT NULL)")
                db.execSQL("INSERT INTO fixture VALUES ('synthetic')")
                db.version = 4
            }
            val before = file.readBytes()
            DatabaseMigrator(name).migrateIfNeeded(context, key)
            assertArrayEquals(before, file.readBytes())
        } finally { cleanup(name) }
    }

    private fun cleanup(name: String) {
        context.deleteDatabase(name)
        val temp = context.getDatabasePath("${name}_encrypted.tmp")
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(temp.path + suffix).delete()
    }
}
