package com.doffi4.doffisecure.domain.model

data class Passkey(
    val id: Long,
    val credentialId: ByteArray,
    val rpId: String,
    val rpName: String,
    val userId: ByteArray,
    val userName: String,
    val userDisplayName: String?,
    val encryptedPrivateKey: ByteArray,
    val publicKeyCose: ByteArray,
    val algorithm: Int,
    val signCount: Long,
    val linkedPasswordId: Long?,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Passkey
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
