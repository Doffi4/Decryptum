package com.doffi4.doffisecure.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity

@Database(
    entities = [PasswordDatabaseEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
}
