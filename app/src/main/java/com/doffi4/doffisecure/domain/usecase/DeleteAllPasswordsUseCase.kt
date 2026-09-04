package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.repository.IPasswordRepository

class DeleteAllPasswordsUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke() {
        repository.deleteAllPasswords()
    }
}