package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.DuplicateGroup
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import kotlinx.coroutines.flow.Flow

/** Groups of duplicate (service, username) rows, live from the database. */
class GetDuplicateGroupsUseCase(private val repository: IPasswordRepository) {
    operator fun invoke(): Flow<List<DuplicateGroup>> = repository.getDuplicateGroups()
}