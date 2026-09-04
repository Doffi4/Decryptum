package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.repository.IPasswordRepository

class DeletePasswordUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke(id: Long) {
        repository.deletePassword(id)
    }
}
    
