package com.doffi4.doffisecure

import com.doffi4.doffisecure.dev.SyntheticVaultGenerator
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import com.doffi4.doffisecure.domain.security.SecurityFindingType
import com.doffi4.doffisecure.security.CsvManager
import org.junit.Assert.*
import org.junit.Test

class SyntheticVaultGeneratorTest {
    private val generator = SyntheticVaultGenerator

    @Test fun `large dataset covers 200 distinct services with uneven account counts`() {
        val plan = generator.plan(500, 200)
        val rows = generator.generate(plan, "batch-one", 1_800_000_000_000L)
        assertEquals(500, rows.size)
        assertEquals(200, rows.groupBy { it.service }.size)
        val counts = rows.groupBy { it.service }.values.map { it.size }.toSet()
        assertTrue(counts.containsAll(setOf(1, 3, 5, 7)))
        assertEquals(plan.allocations.map { it.accountCount }, rows.groupBy { it.service }.values.map { it.size })
    }

    @Test fun `small and maximum datasets have exact counts without losing services`() {
        for ((entries, services) in listOf(1 to 1, 10 to 10, 100 to 70, 200 to 200, 5000 to 200)) {
            val rows = generator.generate(generator.plan(entries, services), "batch", 1_800_000_000_000L)
            assertEquals(entries, rows.size)
            assertEquals(services, rows.map { it.service }.toSet().size)
            assertEquals(rows.size, rows.map { it.service to it.username }.toSet().size)
            assertTrue(rows.all { it.id == 0L && it.createdAt > 0 && it.createdAt <= 1_800_000_000_000L })
        }
    }

    @Test fun `invalid sizes fail before generating or saving data`() {
        for ((entries, services) in listOf(0 to 1, -1 to 1, 5001 to 200, 10 to 11, 500 to 201, 500 to 0)) {
            assertThrows(IllegalArgumentException::class.java) { generator.plan(entries, services) }
        }
    }

    @Test fun `new batches cannot collide with previous synthetic account identities`() {
        val plan = generator.plan(500, 200)
        val first = generator.generate(plan, "one", 1_800_000_000_000L)
        val second = generator.generate(plan, "two", 1_800_000_000_000L)
        assertTrue(first.map { it.username }.toSet().intersect(second.map { it.username }.toSet()).isEmpty())
        assertTrue((first + second).all { it.username.endsWith("@example.invalid") && it.totpSecret == null })
        assertTrue(first.all { it.password == "password123" || it.password.startsWith("NOT_REAL_") })
    }

    @Test fun `generated rows round trip through the actual CSV parser`() {
        val rows = generator.generate(generator.plan(500, 200), "csv", 1_800_000_000_000L)
        fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
        val csv = "service,username,password,url,createdAt\n" + rows.joinToString("\n") {
            listOf(it.service, it.username, it.password, it.url.orEmpty(), it.createdAt.toString()).joinToString(",", transform = ::quote)
        }
        val parsed = CsvManager.parseStream(csv.byteInputStream())
        assertEquals(rows.map { listOf(it.service, it.username, it.password, it.url) },
            parsed.map { listOf(it.service, it.username, it.password, it.url) })
    }

    @Test fun `tester security fixture reproduces documented counts and omits real recipients`() {
        val rows = generator.securityFixture(1234).mapIndexed { i, p -> p.copy(id = i + 1L) }
        val summary = LocalSecurityAnalyzer().analyze(rows)
        assertEquals(4, summary.passwordEntries)
        assertEquals(2, summary.count(SecurityFindingType.WEAK_PASSWORD))
        assertEquals(2, summary.count(SecurityFindingType.REUSED_PASSWORD))
        assertEquals(2, summary.count(SecurityFindingType.DUPLICATE_CREDENTIAL))
        assertTrue(rows.all { it.url == null && it.totpSecret == null })
    }

    @Test fun `TOTP fixture remains separate from password security counters`() {
        val fixture = generator.totpFixture(1234).copy(id = 5)
        val rows = generator.securityFixture(1234).mapIndexed { i, p -> p.copy(id = i + 1L) } + fixture
        val summary = LocalSecurityAnalyzer().analyze(rows)
        assertEquals(5, summary.totalEntries)
        assertEquals(4, summary.passwordEntries)
        assertEquals("", fixture.password)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", fixture.totpSecret)
        assertNull(fixture.url)
    }
}
