package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.TotpAlgorithm
import com.doffi4.doffisecure.domain.model.TotpConfig
import com.doffi4.doffisecure.security.totp.TotpGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TotpGeneratorTest {

    // RFC 6238 Appendix B: Seed for HMAC-SHA1 is "12345678901234567890" (20 bytes)
    // Base32 representation of "12345678901234567890" is "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val rfcBase32Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `RFC 6238 Appendix B test vectors - 8 digits SHA1`() {
        val config8 = TotpConfig(
            secret = rfcBase32Secret,
            digits = 8,
            periodSeconds = 30,
            algorithm = TotpAlgorithm.SHA1
        )

        assertEquals("94287082", TotpGenerator.generateTotp(config8, 59L))
        assertEquals("07081804", TotpGenerator.generateTotp(config8, 1111111109L))
        assertEquals("14050471", TotpGenerator.generateTotp(config8, 1111111111L))
        assertEquals("89005924", TotpGenerator.generateTotp(config8, 1234567890L))
        assertEquals("69279037", TotpGenerator.generateTotp(config8, 2000000000L))
    }

    @Test
    fun `RFC 6238 test vectors - 6 digits SHA1`() {
        val config6 = TotpConfig(
            secret = rfcBase32Secret,
            digits = 6,
            periodSeconds = 30,
            algorithm = TotpAlgorithm.SHA1
        )

        assertEquals("287082", TotpGenerator.generateTotp(config6, 59L))
        assertEquals("081804", TotpGenerator.generateTotp(config6, 1111111109L))
        assertEquals("050471", TotpGenerator.generateTotp(config6, 1111111111L))
        assertEquals("005924", TotpGenerator.generateTotp(config6, 1234567890L))
        assertEquals("279037", TotpGenerator.generateTotp(config6, 2000000000L))
    }

    @Test
    fun `Base32 decoding matches RFC 4648 test vectors`() {
        // "MZXW6YTB" (40 bits / 5 bytes) -> "fooba"
        val fooba = String(TotpGenerator.decodeBase32("MZXW6YTB"), Charsets.UTF_8)
        assertEquals("fooba", fooba)

        // Case insensitivity and hyphen/whitespace resilience
        val foobaWithNoise = String(TotpGenerator.decodeBase32("mzxw-6ytb"), Charsets.UTF_8)
        assertEquals("fooba", foobaWithNoise)

        val foobaWithSpaces = String(TotpGenerator.decodeBase32("MZXW 6YTB"), Charsets.UTF_8)
        assertEquals("fooba", foobaWithSpaces)

        // "MZXW6YTBOI======" (6 bytes) -> "foobar"
        val foobar = String(TotpGenerator.decodeBase32("MZXW6YTBOI======"), Charsets.UTF_8)
        assertEquals("foobar", foobar)
    }

    @Test
    fun `parseOtpAuth parses standard Google Authenticator URI correctly`() {
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30"
        val config = TotpGenerator.parseOtpAuth(uri)

        assertNotNull(config)
        assertEquals("JBSWY3DPEHPK3PXP", config!!.secret)
        assertEquals("GitHub", config.issuer)
        assertEquals("octocat", config.accountName)
        assertEquals(6, config.digits)
        assertEquals(30, config.periodSeconds)
        assertEquals(TotpAlgorithm.SHA1, config.algorithm)
    }

    @Test
    fun `parseOtpAuth parses 8 digits SHA256 and custom period`() {
        val uri = "otpauth://totp/Acme%20Corp:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Acme%20Corp&algorithm=SHA256&digits=8&period=60"
        val config = TotpGenerator.parseOtpAuth(uri)

        assertNotNull(config)
        assertEquals("JBSWY3DPEHPK3PXP", config!!.secret)
        assertEquals("Acme Corp", config.issuer)
        assertEquals("alice@example.com", config.accountName)
        assertEquals(8, config.digits)
        assertEquals(60, config.periodSeconds)
        assertEquals(TotpAlgorithm.SHA256, config.algorithm)
    }

    @Test
    fun `parseOtpAuth parses raw Base32 secret string`() {
        val raw = "jbsw y3dp ehpk 3pxp"
        val config = TotpGenerator.parseOtpAuth(raw)

        assertNotNull(config)
        assertEquals("JBSWY3DPEHPK3PXP", config!!.secret)
        assertEquals(6, config.digits)
        assertEquals(30, config.periodSeconds)
    }

    @Test
    fun `parseOtpAuth returns null on invalid secret`() {
        assertNull(TotpGenerator.parseOtpAuth("invalid!#$"))
        assertNull(TotpGenerator.parseOtpAuth("123")) // Too short
        assertNull(TotpGenerator.parseOtpAuth(null))
        assertNull(TotpGenerator.parseOtpAuth(""))
    }

    @Test
    fun `formatCode groups numbers with spaces`() {
        assertEquals("123 456", TotpGenerator.formatCode("123456"))
        assertEquals("1234 5678", TotpGenerator.formatCode("12345678"))
    }
}
