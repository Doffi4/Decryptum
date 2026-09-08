package com.doffi4.doffisecure.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "passkey_table",
    foreignKeys = [
        ForeignKey(
            entity = PasswordDatabaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedPasswordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["credentialId"], unique = true),
        Index(value = ["rpId"]),
        Index(value = ["linkedPasswordId"])
    ]
)
data class PasskeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val credentialId: ByteArray,
    val rpId: String,
    val rpName: String,
    val userId: ByteArray,
    val userName: String,
    val userDisplayName: String? = null,
    val encryptedPrivateKey: ByteArray,
    val publicKeyCose: ByteArray,
    val algorithm: Int = -7, // Default: ES256 (-7)
    val signCount: Long = 0,
    val linkedPasswordId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasskeyEntity
        if (id != other.id) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (rpId != other.rpId) return false
        if (userName != other.userName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + userName.hashCode()
        return result
    }
}
