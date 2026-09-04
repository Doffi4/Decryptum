package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.flow.Flow

/** Counts rows whose password value is encrypted (starts with "enc:"). */
class CountEncryptedPasswordsUseCase(private val repository: IPasswordRepository) {
    operator fun invoke(): Flow<Int> = repository.countEncryptedPasswords()
}