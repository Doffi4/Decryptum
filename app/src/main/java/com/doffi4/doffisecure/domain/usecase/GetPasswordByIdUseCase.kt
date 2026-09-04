package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository

class GetPasswordByIdUseCase(
    private val repository: IPasswordRepository
) {
    suspend operator fun invoke(id: Long): Password? {
        return repository.getPasswordById(id)
    }
}
