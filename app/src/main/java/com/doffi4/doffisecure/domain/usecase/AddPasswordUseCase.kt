package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository

class AddPasswordUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke(
        service: String,
        username: String,
        password: String,
        url: String? = null,
        totpSecret: String? = null
    ) {
        val newPassword = Password(
            id = 0,
            service = service,
            username = username,
            password = password,
            url = url,
            createdAt = System.currentTimeMillis(),
            totpSecret = totpSecret
        )
        repository.addPassword(newPassword)
    }
}
