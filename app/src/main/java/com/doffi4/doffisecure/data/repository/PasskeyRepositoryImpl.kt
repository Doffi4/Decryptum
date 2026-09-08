package com.doffi4.doffisecure.data.repository

import com.doffi4.doffisecure.data.local.dao.PasskeyDao
import com.doffi4.doffisecure.data.mapper.PasskeyMapper
import com.doffi4.doffisecure.domain.model.Passkey
import com.doffi4.doffisecure.domain.repository.IPasskeyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PasskeyRepositoryImpl(
    private val passkeyDao: PasskeyDao,
) : IPasskeyRepository {

    override fun getAllPasskeys(): Flow<List<Passkey>> {
        return passkeyDao.getAllPasskeys().map { list ->
            list.map { PasskeyMapper.toDomain(it) }
        }
    }

    override fun getPasskeysForRpId(rpId: String): Flow<List<Passkey>> {
        return passkeyDao.getPasskeysForRpId(rpId).map { list ->
            list.map { PasskeyMapper.toDomain(it) }
        }
    }

    override suspend fun getPasskeysForRpIdSync(rpId: String): List<Passkey> {
        return passkeyDao.getPasskeysForRpIdSync(rpId).map { PasskeyMapper.toDomain(it) }
    }

    override suspend fun getPasskeyByCredentialId(credentialId: ByteArray): Passkey? {
        return passkeyDao.getPasskeyByCredentialId(credentialId)?.let {
            PasskeyMapper.toDomain(it)
        }
    }

    override fun getPasskeysByLinkedPasswordId(passwordId: Long): Flow<List<Passkey>> {
        return passkeyDao.getPasskeysByLinkedPasswordId(passwordId).map { list ->
            list.map { PasskeyMapper.toDomain(it) }
        }
    }

    override suspend fun getPasskeyById(id: Long): Passkey? {
        return passkeyDao.getPasskeyById(id)?.let { PasskeyMapper.toDomain(it) }
    }

    override suspend fun addPasskey(passkey: Passkey): Long {
        return passkeyDao.insertPasskey(PasskeyMapper.toEntity(passkey))
    }

    override suspend fun addPasskeys(passkeys: List<Passkey>): Int {
        var count = 0
        for (pk in passkeys) {
            passkeyDao.insertPasskey(PasskeyMapper.toEntity(pk))
            count++
        }
        return count
    }

    override suspend fun updateSignCount(id: Long, newCount: Long) {
        passkeyDao.updateSignCount(id, newCount)
    }

    override suspend fun linkToPassword(passkeyId: Long, passwordId: Long?) {
        passkeyDao.linkToPassword(passkeyId, passwordId)
    }

    override suspend fun deletePasskey(id: Long) {
        passkeyDao.deletePasskey(id)
    }

    override suspend fun deleteAllPasskeys() {
        passkeyDao.deleteAllPasskeys()
    }
}
