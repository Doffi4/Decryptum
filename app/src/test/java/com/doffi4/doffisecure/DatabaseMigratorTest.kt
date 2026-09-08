package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.local.database.DatabaseMigrator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class DatabaseMigratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val migrator = DatabaseMigrator()

    @Test
    fun `isDatabasePlaintext returns true for valid SQLite header`() {
        val file = tempFolder.newFile("plain.db")
        FileOutputStream(file).use { out ->
            out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            out.write(ByteArray(84)) // Rest of 100-byte SQLite header
        }

        assertTrue("File with SQLite header must be detected as plaintext", migrator.isDatabasePlaintext(file))
    }

    @Test
    fun `isDatabasePlaintext returns false for encrypted or random data`() {
        val file = tempFolder.newFile("encrypted.db")
        FileOutputStream(file).use { out ->
            // Random encrypted bytes, doesn't match SQLite header
            out.write(byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte(), 1, 2, 3, 4, 5, 6, 7, 8))
        }

        assertFalse("Encrypted file should not be detected as plaintext", migrator.isDatabasePlaintext(file))
    }

    @Test
    fun `isDatabasePlaintext returns false for non-existent or small files`() {
        val nonExistent = File(tempFolder.root, "does_not_exist.db")
        assertFalse(migrator.isDatabasePlaintext(nonExistent))

        val smallFile = tempFolder.newFile("small.db")
        FileOutputStream(smallFile).use { out ->
            out.write("SQL".toByteArray())
        }
        assertFalse(migrator.isDatabasePlaintext(smallFile))
    }
}
