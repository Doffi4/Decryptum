package com.doffi4.doffisecure.security.totp

import com.doffi4.doffisecure.domain.model.TotpAlgorithm
import com.doffi4.doffisecure.domain.model.TotpConfig
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.Base64

/**
 * Parser for Google Authenticator export URIs (`otpauth-migration://offline?data=...`).
 * Decodes the binary Protocol Buffers [MigrationPayload] without external protobuf dependencies.
 */
object GoogleAuthMigrationParser {

    /**
     * Checks if the given input string is a Google Authenticator migration URI.
     */
    fun isMigrationUri(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        return trimmed.startsWith("otpauth-migration://offline", ignoreCase = true)
    }

    /**
     * Parses an `otpauth-migration://offline?data=...` URI into a list of [TotpConfig] objects.
     */
    fun parseMigrationUri(uriString: String): List<TotpConfig> {
        val trimmed = uriString.trim()
        if (!isMigrationUri(trimmed)) return emptyList()

        val query = trimmed.substringAfter("?", "")
        if (query.isEmpty()) return emptyList()

        val dataParam = query.split("&")
            .firstOrNull { it.startsWith("data=", ignoreCase = true) }
            ?.substringAfter("=") ?: return emptyList()

        val urlDecoded = try {
            URLDecoder.decode(dataParam, "UTF-8")
        } catch (_: Exception) {
            dataParam
        }

        val protoBytes = try {
            Base64.getDecoder().decode(urlDecoded)
        } catch (_: Exception) {
            try {
                Base64.getUrlDecoder().decode(urlDecoded)
            } catch (_: Exception) {
                return emptyList()
            }
        }

        return parseMigrationPayload(protoBytes)
    }

    /**
     * Parses raw protobuf bytes of a `MigrationPayload` message.
     */
    fun parseMigrationPayload(bytes: ByteArray): List<TotpConfig> {
        val buffer = ByteBuffer.wrap(bytes)
        val result = mutableListOf<TotpConfig>()

        while (buffer.hasRemaining()) {
            val tagAndType = readVarint(buffer) ?: break
            val wireType = (tagAndType and 0x07L).toInt()
            val fieldNumber = (tagAndType ushr 3).toInt()

            if (fieldNumber == 1 && wireType == 2) {
                // Field 1: repeated OtpParameters otp_parameters = 1
                val length = readVarint(buffer)?.toInt() ?: break
                if (length < 0 || length > buffer.remaining()) break

                val otpSlice = ByteArray(length)
                buffer.get(otpSlice)
                val config = parseOtpParameters(otpSlice)
                if (config != null) {
                    result.add(config)
                }
            } else {
                skipField(buffer, wireType)
            }
        }

        return result
    }

    private fun parseOtpParameters(bytes: ByteArray): TotpConfig? {
        val buffer = ByteBuffer.wrap(bytes)
        var secretBytes: ByteArray? = null
        var name: String? = null
        var issuer: String? = null
        var algorithm = TotpAlgorithm.SHA1
        var digits = 6

        while (buffer.hasRemaining()) {
            val tagAndType = readVarint(buffer) ?: break
            val wireType = (tagAndType and 0x07L).toInt()
            val fieldNumber = (tagAndType ushr 3).toInt()

            when {
                fieldNumber == 1 && wireType == 2 -> {
                    // bytes secret = 1;
                    val length = readVarint(buffer)?.toInt() ?: break
                    if (length < 0 || length > buffer.remaining()) break
                    secretBytes = ByteArray(length)
                    buffer.get(secretBytes)
                }
                fieldNumber == 2 && wireType == 2 -> {
                    // string name = 2;
                    val length = readVarint(buffer)?.toInt() ?: break
                    if (length < 0 || length > buffer.remaining()) break
                    val nameBytes = ByteArray(length)
                    buffer.get(nameBytes)
                    name = String(nameBytes, Charsets.UTF_8)
                }
                fieldNumber == 3 && wireType == 2 -> {
                    // string issuer = 3;
                    val length = readVarint(buffer)?.toInt() ?: break
                    if (length < 0 || length > buffer.remaining()) break
                    val issuerBytes = ByteArray(length)
                    buffer.get(issuerBytes)
                    issuer = String(issuerBytes, Charsets.UTF_8)
                }
                fieldNumber == 4 && wireType == 0 -> {
                    // Algorithm algorithm = 4;
                    val algoVal = readVarint(buffer)?.toInt() ?: 1
                    algorithm = when (algoVal) {
                        2 -> TotpAlgorithm.SHA256
                        3 -> TotpAlgorithm.SHA512
                        else -> TotpAlgorithm.SHA1
                    }
                }
                fieldNumber == 5 && wireType == 0 -> {
                    // DigitCount digits = 5; (1 = 6 digits, 2 = 8 digits)
                    val digitsVal = readVarint(buffer)?.toInt() ?: 1
                    digits = if (digitsVal == 2) 8 else 6
                }
                fieldNumber == 6 && wireType == 0 -> {
                    // OtpType type = 6; (1 = HOTP, 2 = TOTP)
                    readVarint(buffer)
                }
                fieldNumber == 7 && wireType == 0 -> {
                    // int64 counter = 7;
                    readVarint(buffer)
                }
                else -> skipField(buffer, wireType)
            }
        }

        if (secretBytes == null || secretBytes.isEmpty()) return null

        val base32Secret = TotpGenerator.encodeBase32(secretBytes)
        if (base32Secret.isBlank()) return null

        val (finalIssuer, finalAccount) = resolveIssuerAndAccount(name, issuer)

        return TotpConfig(
            secret = base32Secret,
            digits = digits,
            periodSeconds = 30,
            algorithm = algorithm,
            issuer = finalIssuer,
            accountName = finalAccount
        )
    }

    private fun resolveIssuerAndAccount(name: String?, issuer: String?): Pair<String?, String?> {
        val cleanIssuer = issuer?.trim()?.takeIf { it.isNotBlank() }
        val cleanName = name?.trim()?.takeIf { it.isNotBlank() }

        return when {
            cleanName != null && cleanName.contains(":") -> {
                val parts = cleanName.split(":", limit = 2)
                val derivedIssuer = parts[0].trim().takeIf { it.isNotBlank() } ?: cleanIssuer
                val derivedAccount = parts[1].trim().takeIf { it.isNotBlank() }
                Pair(cleanIssuer ?: derivedIssuer, derivedAccount)
            }
            cleanIssuer != null && cleanName != null -> {
                if (cleanName.startsWith("$cleanIssuer:", ignoreCase = true)) {
                    Pair(cleanIssuer, cleanName.substring(cleanIssuer.length + 1).trim())
                } else {
                    Pair(cleanIssuer, cleanName)
                }
            }
            else -> Pair(cleanIssuer, cleanName)
        }
    }

    private fun readVarint(buffer: ByteBuffer): Long? {
        var result = 0L
        var shift = 0
        while (buffer.hasRemaining()) {
            val b = buffer.get().toLong()
            result = result or ((b and 0x7FL) shl shift)
            if ((b and 0x80L) == 0L) return result
            shift += 7
            if (shift >= 64) return null
        }
        return null
    }

    private fun skipField(buffer: ByteBuffer, wireType: Int) {
        when (wireType) {
            0 -> readVarint(buffer)
            1 -> if (buffer.remaining() >= 8) buffer.position(buffer.position() + 8)
            2 -> {
                val len = readVarint(buffer)?.toInt() ?: return
                if (len in 0..buffer.remaining()) {
                    buffer.position(buffer.position() + len)
                }
            }
            5 -> if (buffer.remaining() >= 4) buffer.position(buffer.position() + 4)
        }
    }
}
