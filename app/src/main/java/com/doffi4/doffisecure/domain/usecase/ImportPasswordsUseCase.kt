package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository

/**
 * Bulk-imports a list of passwords in a single transaction, avoiding the
 * lag and dropped records caused by one-by-one inserts during CSV import.
 * Returns the number of passwords actually stored.
 */
class ImportPasswordsUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke(passwords: List<Password>): Int {
        return repository.addPasswords(passwords)
    }
}