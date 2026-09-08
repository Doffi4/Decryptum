package com.doffi4.doffisecure.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.doffi4.doffisecure.data.local.dao.PasskeyDao
import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.entities.PasskeyEntity
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity

@Database(
    entities = [PasswordDatabaseEntity::class, PasskeyEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
    abstract fun passkeyDao(): PasskeyDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `passkey_table` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `credentialId` BLOB NOT NULL,
                        `rpId` TEXT NOT NULL,
                        `rpName` TEXT NOT NULL,
                        `userId` BLOB NOT NULL,
                        `userName` TEXT NOT NULL,
                        `userDisplayName` TEXT,
                        `encryptedPrivateKey` BLOB NOT NULL,
                        `publicKeyCose` BLOB NOT NULL,
                        `algorithm` INTEGER NOT NULL,
                        `signCount` INTEGER NOT NULL,
                        `linkedPasswordId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`linkedPasswordId`) REFERENCES `password_table`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_passkey_table_credentialId` ON `passkey_table` (`credentialId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_passkey_table_rpId` ON `passkey_table` (`rpId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_passkey_table_linkedPasswordId` ON `passkey_table` (`linkedPasswordId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `password_table` ADD COLUMN `totpSecret` TEXT DEFAULT NULL")
            }
        }
    }
}
