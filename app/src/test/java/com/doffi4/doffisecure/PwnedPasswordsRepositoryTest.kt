package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.repository.IPwnedPasswordsRepository
import com.doffi4.doffisecure.data.repository.PwnedPasswordsRepository
import com.doffi4.doffisecure.domain.model.BreachCheckResult
import com.doffi4.doffisecure.domain.usecase.CheckPasswordBreachUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PwnedPasswordsRepositoryTest {

    @Test
    fun `sha1Hex calculates correct uppercase hash for known password`() {
        // "password" -> SHA-1: 5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8
        val hash = PwnedPasswordsRepository.sha1Hex("password").uppercase(Locale.US)
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
        assertEquals(40, hash.length)

        val prefix = hash.substring(0, 5)
        val suffix = hash.substring(5)
        assertEquals("5BAA6", prefix)
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", suffix)
    }

    @Test
    fun `empty password returns Clean immediately`() = runBlocking {
        val repo = PwnedPasswordsRepository()
        val result = repo.checkPassword("")
        assertTrue("Empty password should be Clean", result is BreachCheckResult.Clean)
    }

    @Test
    fun `usecase returns cached result when forceRefresh is false`() = runBlocking {
        var queryCount = 0
        val fakeRepo = object : IPwnedPasswordsRepository {
            override suspend fun checkPassword(password: String): BreachCheckResult {
                queryCount++
                return BreachCheckResult.Compromised(count = 42)
            }
        }

        val useCase = CheckPasswordBreachUseCase(fakeRepo)

        val firstResult = useCase("testSecret", forceRefresh = false)
        assertTrue(firstResult is BreachCheckResult.Compromised)
        assertEquals(42, (firstResult as BreachCheckResult.Compromised).count)
        assertEquals(1, queryCount)

        // Second call without forceRefresh should use cache
        val secondResult = useCase("testSecret", forceRefresh = false)
        assertTrue(secondResult is BreachCheckResult.Compromised)
        assertEquals(42, (secondResult as BreachCheckResult.Compromised).count)
        assertEquals(1, queryCount)

        // Third call with forceRefresh = true should query repo again
        val thirdResult = useCase("testSecret", forceRefresh = true)
        assertTrue(thirdResult is BreachCheckResult.Compromised)
        assertEquals(2, queryCount)
    }

    @Test
    fun `usecase returns Clean for blank password without querying repository`() = runBlocking {
        var queryCount = 0
        val fakeRepo = object : IPwnedPasswordsRepository {
            override suspend fun checkPassword(password: String): BreachCheckResult {
                queryCount++
                return BreachCheckResult.Compromised(count = 1)
            }
        }

        val useCase = CheckPasswordBreachUseCase(fakeRepo)
        val result = useCase("   ")
        assertTrue(result is BreachCheckResult.Clean)
        assertEquals(0, queryCount)
    }
}
