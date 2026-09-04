package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository

class UpdatePasswordUseCase(
    private val repository: IPasswordRepository
) {
    suspend operator fun invoke(id: Long, service: String, username: String, password: String) {
        val updatedPassword = Password(
            id = id,
            service = service,
            username = username,
            password = password,
            url = null,
            createdAt = System.currentTimeMillis()
        )
        repository.updatePassword(updatedPassword)
    }
}
