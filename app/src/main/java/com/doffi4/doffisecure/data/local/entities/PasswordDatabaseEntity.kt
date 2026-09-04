package com.doffi4.doffisecure.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_table")
data class PasswordDatabaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val service: String,
    val username: String,
    val password: String,
    val url: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
