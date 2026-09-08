package com.doffi4.doffisecure.data.mapper

import com.doffi4.doffisecure.data.local.entities.PasskeyEntity
import com.doffi4.doffisecure.domain.model.Passkey

object PasskeyMapper {
    fun toDomain(entity: PasskeyEntity): Passkey {
        return Passkey(
            id = entity.id,
            credentialId = entity.credentialId,
            rpId = entity.rpId,
            rpName = entity.rpName,
            userId = entity.userId,
            userName = entity.userName,
            userDisplayName = entity.userDisplayName,
            encryptedPrivateKey = entity.encryptedPrivateKey,
            publicKeyCose = entity.publicKeyCose,
            algorithm = entity.algorithm,
            signCount = entity.signCount,
            linkedPasswordId = entity.linkedPasswordId,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(model: Passkey): PasskeyEntity {
        return PasskeyEntity(
            id = model.id,
            credentialId = model.credentialId,
            rpId = model.rpId,
            rpName = model.rpName,
            userId = model.userId,
            userName = model.userName,
            userDisplayName = model.userDisplayName,
            encryptedPrivateKey = model.encryptedPrivateKey,
            publicKeyCose = model.publicKeyCose,
            algorithm = model.algorithm,
            signCount = model.signCount,
            linkedPasswordId = model.linkedPasswordId,
            createdAt = model.createdAt
        )
    }
}
