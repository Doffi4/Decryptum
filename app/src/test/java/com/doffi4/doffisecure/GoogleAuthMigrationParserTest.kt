package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.TotpAlgorithm
import com.doffi4.doffisecure.security.totp.GoogleAuthMigrationParser
import com.doffi4.doffisecure.security.totp.TotpGenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64

class GoogleAuthMigrationParserTest {

    @Test
    fun testBase32EncodeDecodeRoundtrip() {
        val testStrings = listOf(
            "",
            "f",
            "fo",
            "foo",
            "foob",
            "fooba",
            "foobar",
            "Hello, World!",
            "12345678901234567890"
        )

        for (s in testStrings) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            val encoded = TotpGenerator.encodeBase32(bytes)
            val decoded = TotpGenerator.decodeBase32(encoded)
            assertArrayEquals("Mismatch for string: $s", bytes, decoded)
        }
    }

    @Test
    fun testRfc4648TestVectors() {
        assertEquals("", TotpGenerator.encodeBase32("".toByteArray()))
        assertEquals("MY", TotpGenerator.encodeBase32("f".toByteArray()))
        assertEquals("MZXQ", TotpGenerator.encodeBase32("fo".toByteArray()))
        assertEquals("MZXW6", TotpGenerator.encodeBase32("foo".toByteArray()))
        assertEquals("MZXW6YQ", TotpGenerator.encodeBase32("foob".toByteArray()))
        assertEquals("MZXW6YTB", TotpGenerator.encodeBase32("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", TotpGenerator.encodeBase32("foobar".toByteArray()))
    }

    @Test
    fun testIsMigrationUri() {
        assertTrue(GoogleAuthMigrationParser.isMigrationUri("otpauth-migration://offline?data=CjE..."))
        assertTrue(GoogleAuthMigrationParser.isMigrationUri("OTPAUTH-MIGRATION://OFFLINE?data=test"))
        assertFalse(GoogleAuthMigrationParser.isMigrationUri("otpauth://totp/Google:test?secret=JBSWY3DPEHPK3PXP"))
        assertFalse(GoogleAuthMigrationParser.isMigrationUri(null))
        assertFalse(GoogleAuthMigrationParser.isMigrationUri(""))
    }

    @Test
    fun testParseSyntheticGoogleMigrationPayload() {
        // Construct a synthetic protobuf MigrationPayload containing 2 accounts
        val secret1 = "12345678901234567890".toByteArray(Charsets.UTF_8) // 20 bytes
        val otp1Bytes = buildOtpParameters(
            secret = secret1,
            name = "Google:user@example.com",
            issuer = "Google",
            algo = 1, // SHA1
            digits = 1 // 6 digits
        )

        val secret2 = "abcdefghijklmnop".toByteArray(Charsets.UTF_8) // 16 bytes
        val otp2Bytes = buildOtpParameters(
            secret = secret2,
            name = "octocat",
            issuer = "GitHub",
            algo = 2, // SHA256
            digits = 2 // 8 digits
        )

        val payloadStream = ByteArrayOutputStream()
        // Field 1 (otp_parameters): tag (1 << 3) | 2 = 10
        writeVarint(payloadStream, 10)
        writeVarint(payloadStream, otp1Bytes.size.toLong())
        payloadStream.write(otp1Bytes)

        writeVarint(payloadStream, 10)
        writeVarint(payloadStream, otp2Bytes.size.toLong())
        payloadStream.write(otp2Bytes)

        val payloadBytes = payloadStream.toByteArray()
        val base64Payload = Base64.getEncoder().encodeToString(payloadBytes)
        val migrationUri = "otpauth-migration://offline?data=${URLEncoder.encode(base64Payload, "UTF-8")}"

        val accounts = GoogleAuthMigrationParser.parseMigrationUri(migrationUri)
        assertEquals(2, accounts.size)

        // Verify account 1
        val acc1 = accounts[0]
        assertEquals("Google", acc1.issuer)
        assertEquals("user@example.com", acc1.accountName)
        assertEquals(TotpAlgorithm.SHA1, acc1.algorithm)
        assertEquals(6, acc1.digits)
        assertArrayEquals(secret1, TotpGenerator.decodeBase32(acc1.secret))

        // Verify account 2
        val acc2 = accounts[1]
        assertEquals("GitHub", acc2.issuer)
        assertEquals("octocat", acc2.accountName)
        assertEquals(TotpAlgorithm.SHA256, acc2.algorithm)
        assertEquals(8, acc2.digits)
        assertArrayEquals(secret2, TotpGenerator.decodeBase32(acc2.secret))
    }

    private fun buildOtpParameters(
        secret: ByteArray,
        name: String,
        issuer: String,
        algo: Int,
        digits: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // Field 1: bytes secret = 1; tag = 10 (0x0A)
        writeVarint(out, 10)
        writeVarint(out, secret.size.toLong())
        out.write(secret)

        // Field 2: string name = 2; tag = 18 (0x12)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        writeVarint(out, 18)
        writeVarint(out, nameBytes.size.toLong())
        out.write(nameBytes)

        // Field 3: string issuer = 3; tag = 26 (0x1A)
        val issuerBytes = issuer.toByteArray(Charsets.UTF_8)
        writeVarint(out, 26)
        writeVarint(out, issuerBytes.size.toLong())
        out.write(issuerBytes)

        // Field 4: Algorithm algorithm = 4; tag = 32 (0x20)
        writeVarint(out, 32)
        writeVarint(out, algo.toLong())

        // Field 5: DigitCount digits = 5; tag = 40 (0x28)
        writeVarint(out, 40)
        writeVarint(out, digits.toLong())

        return out.toByteArray()
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    @Test
    fun testZxingQrCodeEncodingAndDecoding() {
        val payload = "otpauth-migration://offline?data=CjEKCkhlbGxvV29ybGQSCmdvb2dsZS5jb20aBmdvb2dsZSIBMSoBAjICMAY%3D"
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix.get(x, y)) -0x1000000 else -0x1
            }
        }

        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        val reader = com.google.zxing.qrcode.QRCodeReader()
        val result = reader.decode(binaryBitmap)

        assertEquals(payload, result.text)
    }
}
