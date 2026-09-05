package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.PasswordStrength
import com.doffi4.doffisecure.security.AppLocaleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthTest {

    @Test
    fun `empty and blank passwords evaluate to WEAK`() {
        assertEquals(PasswordStrength.WEAK, PasswordStrength.fromPassword(""))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.fromPassword("   "))
    }

    @Test
    fun `short simple password evaluates to WEAK`() {
        assertEquals(PasswordStrength.WEAK, PasswordStrength.fromPassword("12345"))
    }

    @Test
    fun `long complex password evaluates to VERY_STRONG`() {
        val result = PasswordStrength.fromPassword("P@ssw0rd_2026_Secure_SuperLong!")
        assertEquals(PasswordStrength.VERY_STRONG, result)
    }

    @Test
    fun `every strength level has valid label resource`() {
        for (strength in PasswordStrength.entries) {
            assertTrue("labelRes must be non-zero", strength.labelRes != 0)
        }
    }

    @Test
    fun `locale manager returns expected locales`() {
        assertEquals("ru", AppLocaleManager.getLocale("ru")?.language)
        assertEquals("en", AppLocaleManager.getLocale("en")?.language)
        assertNull(AppLocaleManager.getLocale("system"))
        assertNull(AppLocaleManager.getLocale("unknown"))
    }
}
