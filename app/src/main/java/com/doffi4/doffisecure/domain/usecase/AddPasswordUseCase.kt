package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository

class AddPasswordUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke(service: String, username: String, password: String) {
        val newPassword = Password(
            id = 0,
            service = service,
            username = username,
            password = password,
            url = null,
            createdAt = System.currentTimeMillis()
        )
        repository.addPassword(newPassword)
    }
}
