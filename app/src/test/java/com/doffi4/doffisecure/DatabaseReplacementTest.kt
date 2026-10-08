package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.local.database.replaceDatabaseFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DatabaseReplacementTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `failed replacement leaves original and candidate readable`() {
        val source = folder.newFile("password_db").apply { writeText("original with committed records") }
        val target = folder.newFile("encrypted.tmp").apply { writeText("validated encrypted records") }
        assertThrows(IOException::class.java) {
            replaceDatabaseFile(source, target) { _, _ -> throw IOException("rename refused") }
        }
        assertEquals("original with committed records", source.readText())
        assertEquals("validated encrypted records", target.readText())
    }

    @Test fun `original exists until atomic replacement is invoked`() {
        val source = folder.newFile("password_db").apply { writeText("original") }
        val target = folder.newFile("encrypted.tmp").apply { writeText("new") }
        replaceDatabaseFile(source, target) { _, _ -> assertEquals("original", source.readText()) }
    }

    @Test fun `successful replacement installs candidate at original path`() {
        val source = folder.newFile("password_db").apply { writeText("original") }
        val target = folder.newFile("encrypted.tmp").apply { writeText("new encrypted records") }
        replaceDatabaseFile(source, target)
        assertEquals("new encrypted records", source.readText())
        assertFalse(target.exists())
    }
}
