package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.flow.Flow

class GetPasswordsUseCase(private val repository: IPasswordRepository) {
    operator fun invoke(): Flow<List<Password>> {
        return repository.getAllPasswords()
    }
}
