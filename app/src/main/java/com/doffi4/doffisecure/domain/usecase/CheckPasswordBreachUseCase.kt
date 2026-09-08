package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.data.repository.IPwnedPasswordsRepository
import com.doffi4.doffisecure.data.repository.PwnedPasswordsRepository
import com.doffi4.doffisecure.domain.model.BreachCheckResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Use case to check whether a password has appeared in public data breaches.
 * Includes in-memory caching by SHA-1 hash to avoid unnecessary network queries.
 */
class CheckPasswordBreachUseCase(
    private val repository: IPwnedPasswordsRepository
) {
    private val cache = ConcurrentHashMap<String, BreachCheckResult>()

    suspend operator fun invoke(password: String, forceRefresh: Boolean = false): BreachCheckResult {
        if (password.isBlank()) {
            return BreachCheckResult.Clean()
        }

        val hash = PwnedPasswordsRepository.sha1Hex(password)

        if (!forceRefresh) {
            val cached = cache[hash]
            if (cached != null && cached !is BreachCheckResult.Error) {
                return cached
            }
        }

        val result = repository.checkPassword(password)
        if (result is BreachCheckResult.Clean || result is BreachCheckResult.Compromised) {
            cache[hash] = result
        }
        return result
    }

    fun clearCache() {
        cache.clear()
    }
}
