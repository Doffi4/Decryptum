package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.flow.Flow

class SearchPasswordsUseCase(private val repository: IPasswordRepository) {
    operator fun invoke(query: String): Flow<List<Password>> {
        return repository.searchPasswords(query)
    }
}
