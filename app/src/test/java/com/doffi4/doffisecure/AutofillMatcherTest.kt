package com.doffi4.doffisecure

import com.doffi4.doffisecure.autofill.AutofillMatcher
import com.doffi4.doffisecure.autofill.AutofillStructureParser
import com.doffi4.doffisecure.domain.model.Password
import org.junit.Assert.*
import org.junit.Test

class AutofillMatcherTest {

    private val samplePasswords = listOf(
        Password(1L, "GitHub", "octocat", "pass123", "https://github.com", 0L),
        Password(2L, "Telegram", "user1", "secret", "https://telegram.org", 0L),
        Password(3L, "Google", "user@gmail.com", "googlePass", "https://accounts.google.com", 0L),
        Password(4L, "VK", "vk_user", "vkPass", "https://vk.com", 0L)
    )

    @Test
    fun testNormalizeDomain() {
        assertEquals("google.com", AutofillStructureParser.normalizeDomain("https://www.google.com/search?q=hello"))
        assertEquals("github.com", AutofillStructureParser.normalizeDomain("http://github.com:8080/login"))
        assertEquals("telegram.org", AutofillStructureParser.normalizeDomain("telegram.org/"))
        assertEquals("sub.example.com", AutofillStructureParser.normalizeDomain("https://sub.example.com"))
    }

    @Test
    fun testExtractAppKeywords() {
        val telegramKeywords = AutofillMatcher.extractAppKeywords("org.telegram.messenger")
        assertTrue(telegramKeywords.contains("telegram"))

        val vkKeywords = AutofillMatcher.extractAppKeywords("com.vkontakte.android")
        assertTrue(vkKeywords.contains("vkontakte"))
        assertTrue(vkKeywords.contains("vk"))

        val youtubeKeywords = AutofillMatcher.extractAppKeywords("com.google.android.youtube")
        assertTrue(youtubeKeywords.contains("youtube"))
    }

    @Test
    fun testFindMatchesByWebDomain() {
        val matches = AutofillMatcher.findMatches(samplePasswords, "github.com", null)
        assertFalse(matches.isEmpty())
        assertEquals("GitHub", matches.first().service)
        assertEquals("octocat", matches.first().username)
    }

    @Test
    fun testFindMatchesBySubdomain() {
        val matches = AutofillMatcher.findMatches(samplePasswords, "accounts.google.com", null)
        assertFalse(matches.isEmpty())
        assertEquals("Google", matches.first().service)
    }

    @Test
    fun testFindMatchesByPackageName() {
        val matches = AutofillMatcher.findMatches(samplePasswords, null, "org.telegram.messenger")
        assertFalse(matches.isEmpty())
        assertEquals("Telegram", matches.first().service)
    }

    @Test
    fun testFindMatchesByVkPackage() {
        val matches = AutofillMatcher.findMatches(samplePasswords, null, "com.vkontakte.android")
        assertFalse(matches.isEmpty())
        assertEquals("VK", matches.first().service)
    }

    @Test
    fun testNoMatchesForUnknownTarget() {
        val matches = AutofillMatcher.findMatches(samplePasswords, "unknownservice12345.com", "com.unknown.app")
        assertTrue(matches.isEmpty())
    }
}
