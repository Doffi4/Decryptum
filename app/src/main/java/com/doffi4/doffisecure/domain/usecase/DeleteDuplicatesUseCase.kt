package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.repository.IPasswordRepository

/**
 * Keeps a single oldest row per (service, username) and deletes the rest.
 * Returns how many rows were removed.
 */
class DeleteDuplicatesUseCase(private val repository: IPasswordRepository) {
    suspend operator fun invoke(): Int = repository.deleteDuplicates()
}