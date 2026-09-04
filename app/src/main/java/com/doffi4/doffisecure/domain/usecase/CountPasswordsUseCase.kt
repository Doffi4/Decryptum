package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns the total number of stored passwords as an observable flow.
 * Used by the developer-mode "password count" display.
 */
class CountPasswordsUseCase(private val repository: IPasswordRepository) {
    operator fun invoke(): Flow<Int> = repository.countPasswords()
}