package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.PasswordGeneratorOptions
import com.doffi4.doffisecure.domain.model.PasswordPreset
import com.doffi4.doffisecure.domain.usecase.GeneratePasswordUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePasswordUseCaseTest {

    private val useCase = GeneratePasswordUseCase()

    @Test
    fun `strong preset produces 16 chars with all four classes`() {
        val password = useCase(PasswordPreset.STRONG.options)

        assertEquals(16, password.length)
        assertTrue("upper", password.any { it.isUpperCase() })
        assertTrue("lower", password.any { it.isLowerCase() })
        assertTrue("digit", password.any { it.isDigit() })
        assertTrue("symbol", password.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `lookalike characters are excluded when requested`() {
        val options = PasswordGeneratorOptions(
            length = 64,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = true,
            excludeLookalikes = true
        )

        val password = useCase(options)

        assertEquals(64, password.length)
        password.forEach { char -> assertFalse("no lookalike $char", char in "0O1lI") }
    }

    @Test
    fun `digits-only options return digits only`() {
        val password = useCase(
            PasswordGeneratorOptions(
                length = 12,
                includeUpper = false,
                includeLower = false,
                includeDigits = true,
                includeSymbols = false
            )
        )

        assertEquals(12, password.length)
        assertTrue(password.all { it.isDigit() })
    }

    @Test
    fun `every selected class appears at least once`() {
        repeat(50) {
            val password = useCase(
                PasswordGeneratorOptions(
                    length = 8,
                    includeUpper = true,
                    includeLower = true,
                    includeDigits = true,
                    includeSymbols = true
                )
            )
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `no charset selected yields empty password`() {
        val password = useCase(
            PasswordGeneratorOptions(
                length = 16,
                includeUpper = false,
                includeLower = false,
                includeDigits = false,
                includeSymbols = false
            )
        )
        assertEquals("", password)
    }

    @Test
    fun `successive generations differ`() {
        val first = useCase(PasswordPreset.STRONG.options)
        val second = useCase(PasswordPreset.STRONG.options)
        assertNotEquals(first, second)
    }
}