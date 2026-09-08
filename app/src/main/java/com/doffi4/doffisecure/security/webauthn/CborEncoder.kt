package com.doffi4.doffisecure.security.webauthn

import java.io.ByteArrayOutputStream

/**
 * Lightweight, zero-dependency CBOR (RFC 8949) encoder designed specifically for
 * WebAuthn / FIDO2 data structures (COSE_Key, attestationObject, clientData).
 */
object CborEncoder {

    fun encode(value: Any?): ByteArray {
        val out = ByteArrayOutputStream()
        encodeValue(value, out)
        return out.toByteArray()
    }

    private fun encodeValue(value: Any?, out: ByteArrayOutputStream) {
        when (value) {
            null -> out.write(0xF6) // CBOR null
            is Boolean -> out.write(if (value) 0xF5 else 0xF4)
            is Byte -> encodeInt(value.toLong(), out)
            is Short -> encodeInt(value.toLong(), out)
            is Int -> encodeInt(value.toLong(), out)
            is Long -> encodeInt(value, out)
            is ByteArray -> encodeByteString(value, out)
            is String -> encodeTextString(value, out)
            is List<*> -> encodeList(value, out)
            is Map<*, *> -> encodeMap(value, out)
            else -> throw IllegalArgumentException("Unsupported CBOR type: ${value::class.java.name}")
        }
    }

    private fun encodeInt(value: Long, out: ByteArrayOutputStream) {
        if (value >= 0) {
            writeTypeAndLength(0, value, out)
        } else {
            // Negative integer: -1 - value
            writeTypeAndLength(1, -1 - value, out)
        }
    }

    private fun encodeByteString(bytes: ByteArray, out: ByteArrayOutputStream) {
        writeTypeAndLength(2, bytes.size.toLong(), out)
        out.write(bytes)
    }

    private fun encodeTextString(str: String, out: ByteArrayOutputStream) {
        val utf8 = str.toByteArray(Charsets.UTF_8)
        writeTypeAndLength(3, utf8.size.toLong(), out)
        out.write(utf8)
    }

    private fun encodeList(list: List<*>, out: ByteArrayOutputStream) {
        writeTypeAndLength(4, list.size.toLong(), out)
        for (item in list) {
            encodeValue(item, out)
        }
    }

    private fun encodeMap(map: Map<*, *>, out: ByteArrayOutputStream) {
        writeTypeAndLength(5, map.size.toLong(), out)
        for ((key, value) in map) {
            encodeValue(key, out)
            encodeValue(value, out)
        }
    }

    private fun writeTypeAndLength(majorType: Int, length: Long, out: ByteArrayOutputStream) {
        val typeBits = (majorType and 0x07) shl 5
        when {
            length < 24 -> {
                out.write(typeBits or length.toInt())
            }
            length <= 0xFF -> {
                out.write(typeBits or 24)
                out.write(length.toInt() and 0xFF)
            }
            length <= 0xFFFF -> {
                out.write(typeBits or 25)
                out.write((length ushr 8).toInt() and 0xFF)
                out.write(length.toInt() and 0xFF)
            }
            length <= 0xFFFFFFFFL -> {
                out.write(typeBits or 26)
                out.write((length ushr 24).toInt() and 0xFF)
                out.write((length ushr 16).toInt() and 0xFF)
                out.write((length ushr 8).toInt() and 0xFF)
                out.write(length.toInt() and 0xFF)
            }
            else -> {
                out.write(typeBits or 27)
                for (i in 7 downTo 0) {
                    out.write((length ushr (i * 8)).toInt() and 0xFF)
                }
            }
        }
    }
}
