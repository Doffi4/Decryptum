package com.doffi4.doffisecure.domain.repository

import com.doffi4.doffisecure.domain.model.Passkey
import kotlinx.coroutines.flow.Flow

interface IPasskeyRepository {
    fun getAllPasskeys(): Flow<List<Passkey>>
    fun getPasskeysForRpId(rpId: String): Flow<List<Passkey>>
    suspend fun getPasskeysForRpIdSync(rpId: String): List<Passkey>
    suspend fun getPasskeyByCredentialId(credentialId: ByteArray): Passkey?
    fun getPasskeysByLinkedPasswordId(passwordId: Long): Flow<List<Passkey>>
    suspend fun getPasskeyById(id: Long): Passkey?
    suspend fun addPasskey(passkey: Passkey): Long
    suspend fun addPasskeys(passkeys: List<Passkey>): Int
    suspend fun updateSignCount(id: Long, newCount: Long)
    suspend fun linkToPassword(passkeyId: Long, passwordId: Long?)
    suspend fun deletePasskey(id: Long)
    suspend fun deleteAllPasskeys()
}
