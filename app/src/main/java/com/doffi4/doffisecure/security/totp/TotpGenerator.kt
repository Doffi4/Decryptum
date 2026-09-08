package com.doffi4.doffisecure.security.totp

import com.doffi4.doffisecure.domain.model.TotpAlgorithm
import com.doffi4.doffisecure.domain.model.TotpConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpGenerator {

    /**
     * Generates a one-time password based on RFC 6238.
     *
     * @param config The TOTP configuration (secret, digits, period, algorithm).
     * @param timestampSeconds Unix timestamp in seconds (defaults to now).
     * @return Formatted numerical string padded with leading zeroes.
     */
    fun generateTotp(
        config: TotpConfig,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
    ): String {
        val keyBytes = decodeBase32(config.secret)
        if (keyBytes.isEmpty()) return "".padStart(config.digits, '0')

        val period = if (config.periodSeconds > 0) config.periodSeconds else 30
        val timeStep = timestampSeconds / period
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()

        val mac = Mac.getInstance(config.algorithm.hmacAlgorithm)
        val keySpec = SecretKeySpec(keyBytes, config.algorithm.hmacAlgorithm)
        mac.init(keySpec)
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulus = 10.0.pow(config.digits.toDouble()).toInt()
        val otp = binary % modulus
        return otp.toString().padStart(config.digits, '0')
    }

    /**
     * Decodes an RFC 4648 Base32 string into raw bytes.
     * Supports lowercase and ignores spaces and hyphens.
     */
    fun decodeBase32(base32: String): ByteArray {
        val clean = base32.uppercase(Locale.US).replace("[^A-Z2-7]".toRegex(), "")
        if (clean.isEmpty()) return ByteArray(0)

        val output = ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val value = when (c) {
                in 'A'..'Z' -> c.code - 'A'.code
                in '2'..'7' -> (c.code - '2'.code) + 26
                else -> continue
            }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.write((buffer shr bitsLeft) and 0xFF)
            }
        }
        return output.toByteArray()
    }

    /**
     * Encodes raw bytes into an RFC 4648 Base32 string (without '=' padding).
     */
    fun encodeBase32(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                val index = (buffer shr bitsLeft) and 0x1F
                sb.append(base32Chars[index])
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(base32Chars[index])
        }
        return sb.toString()
    }

    /**
     * Calculates the remaining seconds before the current code expires.
     */
    fun getRemainingSeconds(
        periodSeconds: Int = 30,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): Int {
        val period = if (periodSeconds > 0) periodSeconds else 30
        val elapsed = (timestampSeconds % period).toInt()
        return period - elapsed
    }

    /**
     * Calculates the fraction (0.0f .. 1.0f) of time remaining in the current window.
     */
    fun getRemainingFraction(
        periodSeconds: Int = 30,
        timestampMillis: Long = System.currentTimeMillis()
    ): Float {
        val periodMs = (if (periodSeconds > 0) periodSeconds else 30) * 1000L
        val elapsedMs = timestampMillis % periodMs
        val remainingMs = periodMs - elapsedMs
        return (remainingMs.toFloat() / periodMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Parses an otpauth://totp URI or plain Base32 secret string.
     */
    fun parseOtpAuth(input: String?): TotpConfig? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        return if (trimmed.startsWith("otpauth://totp/", ignoreCase = true) ||
            trimmed.startsWith("otpauth://hotp/", ignoreCase = true)
        ) {
            try {
                val queryPart = trimmed.substringAfter("?", "")
                val pathPart = trimmed.substringBefore("?")
                    .replaceFirst(Regex("^otpauth://(totp|hotp)/", RegexOption.IGNORE_CASE), "")

                val params = if (queryPart.isNotEmpty()) {
                    queryPart.split("&").mapNotNull { pair ->
                        val idx = pair.indexOf('=')
                        if (idx > 0) {
                            val key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8").lowercase(Locale.US)
                            val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                            Pair(key, value)
                        } else null
                    }.toMap()
                } else emptyMap()

                val rawSecret = params["secret"] ?: return null
                val cleanSecret = rawSecret.replace(" ", "").replace("-", "").uppercase(Locale.US)
                if (decodeBase32(cleanSecret).isEmpty()) return null

                val digits = params["digits"]?.toIntOrNull()?.let { if (it == 8) 8 else 6 } ?: 6
                val period = params["period"]?.toIntOrNull()?.coerceIn(10, 300) ?: 30
                val algorithm = TotpAlgorithm.fromString(params["algorithm"])
                val issuerParam = params["issuer"]

                val decodedPath = try {
                    java.net.URLDecoder.decode(pathPart, "UTF-8")
                } catch (_: Exception) { pathPart }

                val (pathIssuer, pathAccount) = if (decodedPath.contains(":")) {
                    val parts = decodedPath.split(":", limit = 2)
                    Pair(parts[0].trim(), parts[1].trim())
                } else {
                    Pair(null, decodedPath.trim().takeIf { it.isNotBlank() })
                }

                TotpConfig(
                    secret = cleanSecret,
                    digits = digits,
                    periodSeconds = period,
                    algorithm = algorithm,
                    issuer = issuerParam ?: pathIssuer,
                    accountName = pathAccount
                )
            } catch (_: Exception) {
                null
            }
        } else {
            // Plain Base32 string
            val clean = trimmed.replace(" ", "").replace("-", "").uppercase(Locale.US)
            if (clean.length >= 8 && clean.matches(Regex("^[A-Z2-7]+$"))) {
                TotpConfig(secret = clean)
            } else {
                null
            }
        }
    }

    /**
     * Formats code with visual grouping for easy reading (e.g. "123 456" or "1234 5678").
     */
    fun formatCode(code: String): String {
        return when (code.length) {
            6 -> "${code.substring(0, 3)} ${code.substring(3)}"
            8 -> "${code.substring(0, 4)} ${code.substring(4)}"
            else -> code
        }
    }
}
