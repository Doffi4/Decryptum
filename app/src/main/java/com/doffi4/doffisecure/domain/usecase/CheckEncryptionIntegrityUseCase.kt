package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.repository.IPasswordRepository

/** Returns how many encrypted rows fail to decrypt (integrity check). */
class CheckEncryptionIntegrityUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke(): Int = repository.checkEncryptionIntegrity()
}