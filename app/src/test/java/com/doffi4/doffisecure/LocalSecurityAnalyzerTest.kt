package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import com.doffi4.doffisecure.domain.security.SecurityFindingType
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LocalSecurityAnalyzerTest {
    private val analyzer = LocalSecurityAnalyzer()
    private fun entry(id: Long, secret: String = "x7#M2!qL9@vT6&zR", service: String = "Service$id") =
        Password(id, service, "alice", secret, "https://example$id.test", 0)

    @Test fun `empty vault has no findings`() {
        val result = analyzer.analyze(emptyList())
        assertEquals(0, result.totalEntries)
        assertEquals(0, result.passwordEntries)
        assertTrue(result.findings.isEmpty())
    }

    @Test fun `weak classification is conservative and preserves whitespace`() {
        val result = analyzer.analyze(listOf(
            entry(1, "123456"), entry(2, "Aa1!567890"), entry(3, "AAAAAAAAAAAAAAAAAAAA"),
            entry(4, "Password123!"), entry(5), entry(6, "   ")
        ))
        assertEquals(5, result.count(SecurityFindingType.WEAK_PASSWORD))
        assertEquals(6, result.passwordEntries)
        assertEquals(setOf(1L, 2L, 3L, 4L, 6L), result.findings.first().affectedItems.map { it.id }.toSet())
    }

    @Test fun `reuse compares exact untrimmed case sensitive secrets`() {
        val result = analyzer.analyze(listOf(entry(1), entry(2), entry(3, " x7#M2!qL9@vT6&zR"), entry(4, "X7#M2!qL9@vT6&zR")))
        assertEquals(2, result.count(SecurityFindingType.REUSED_PASSWORD))
        assertEquals(1, result.groupCount(SecurityFindingType.REUSED_PASSWORD))
    }

    @Test fun `duplicate copies are reviewable but alone are not cross account reuse`() {
        val first = entry(1)
        val result = analyzer.analyze(listOf(first, first.copy(id = 2, createdAt = 999)))
        assertEquals(2, result.count(SecurityFindingType.DUPLICATE_CREDENTIAL))
        assertEquals(0, result.count(SecurityFindingType.REUSED_PASSWORD))
    }

    @Test fun `same account with different password url or totp is not a duplicate`() {
        val first = entry(1)
        val result = analyzer.analyze(listOf(first,
            first.copy(id = 2, password = "another-secret"),
            first.copy(id = 3, url = "https://other.test"),
            first.copy(id = 4, totpSecret = "synthetic-totp")
        ))
        assertEquals(0, result.count(SecurityFindingType.DUPLICATE_CREDENTIAL))
    }

    @Test fun `empty passwords do not count as weak reused or duplicate credentials`() {
        val result = analyzer.analyze(listOf(entry(1, "").copy(totpSecret = "synthetic-totp"), entry(2, ""), entry(3)))
        assertEquals(3, result.totalEntries)
        assertEquals(1, result.passwordEntries)
        assertEquals(2, result.passwordlessEntries)
        assertTrue(result.findings.isEmpty())
    }

    @Test fun `overlapping findings and repeated IDs count each entry once`() {
        val first = entry(1, "short")
        val result = analyzer.analyze(listOf(first, first, entry(2, "short"), first.copy(id = 3)))
        assertEquals(3, result.totalEntries)
        assertEquals(3, result.affectedEntries)
        assertEquals(3, result.count(SecurityFindingType.WEAK_PASSWORD))
        assertEquals(3, result.count(SecurityFindingType.REUSED_PASSWORD))
        assertEquals(2, result.count(SecurityFindingType.DUPLICATE_CREDENTIAL))
    }

    @Test fun `analysis does not mutate source or retain secrets in results`() {
        val original = listOf(entry(1, "short").copy(totpSecret = "never-display-this-seed"), entry(2, "short"))
        val before = original.map { it.copy() }
        val result = analyzer.analyze(original)
        assertEquals(before, original)
        assertFalse(result.toString().contains("short"))
        assertFalse(result.toString().contains("never-display-this-seed"))
    }

    @Test fun `large vault aggregates without pairwise comparisons`() {
        val result = analyzer.analyze((1L..10_000L).map { entry(it) })
        assertEquals(10_000, result.count(SecurityFindingType.REUSED_PASSWORD))
        assertEquals(1, result.groupCount(SecurityFindingType.REUSED_PASSWORD))
        assertEquals(0, result.count(SecurityFindingType.DUPLICATE_CREDENTIAL))
    }

    @Test(expected = CancellationException::class)
    fun `analysis cooperatively cancels`() {
        analyzer.analyze(listOf(entry(1))) { throw CancellationException() }
    }
}
