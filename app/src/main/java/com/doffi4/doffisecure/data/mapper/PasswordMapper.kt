package com.doffi4.doffisecure.data.mapper

import com.doffi4.doffisecure.data.local.entities.PasswordDatabaseEntity
import com.doffi4.doffisecure.domain.model.Password

object PasswordMapper {
    /**
     * Converts a Database Entity to a Domain Model for UI consumption.
     */
    fun toDomain(entity: PasswordDatabaseEntity): Password {
        return Password(
            id = entity.id,
            service = entity.service,
            username = entity.username,
            password = entity.password,
            url = entity.url,
            createdAt = entity.createdAt
        )
    }

    /**
     * Converts a Domain Model back to a Database Entity for storage.
     */
    fun toEntity(model: Password): PasswordDatabaseEntity {
        return PasswordDatabaseEntity(
            id = model.id,
            service = model.service,
            username = model.username,
            password = model.password,
            url = model.url,
            createdAt = model.createdAt
        )
    }
}
