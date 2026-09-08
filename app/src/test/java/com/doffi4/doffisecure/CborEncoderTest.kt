package com.doffi4.doffisecure

import com.doffi4.doffisecure.security.webauthn.CborEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CborEncoderTest {

    @Test
    fun `encodes unsigned integers according to RFC 8949`() {
        // 0 -> 0x00
        assertArrayEquals(byteArrayOf(0x00), CborEncoder.encode(0))
        // 1 -> 0x01
        assertArrayEquals(byteArrayOf(0x01), CborEncoder.encode(1))
        // 10 -> 0x0A
        assertArrayEquals(byteArrayOf(0x0A), CborEncoder.encode(10))
        // 23 -> 0x17
        assertArrayEquals(byteArrayOf(0x17), CborEncoder.encode(23))
        // 24 -> 0x18, 0x18
        assertArrayEquals(byteArrayOf(0x18.toByte(), 0x18.toByte()), CborEncoder.encode(24))
        // 100 -> 0x18, 0x64
        assertArrayEquals(byteArrayOf(0x18.toByte(), 0x64.toByte()), CborEncoder.encode(100))
    }

    @Test
    fun `encodes negative integers according to RFC 8949`() {
        // -1 -> 0x20 (major type 1, value 0)
        assertArrayEquals(byteArrayOf(0x20), CborEncoder.encode(-1))
        // -7 (ES256 alg) -> 0x26 (major type 1, value 6)
        assertArrayEquals(byteArrayOf(0x26), CborEncoder.encode(-7))
        // -8 (EdDSA alg) -> 0x27 (major type 1, value 7)
        assertArrayEquals(byteArrayOf(0x27), CborEncoder.encode(-8))
    }

    @Test
    fun `encodes text string and byte strings`() {
        // UTF-8 string "none" (fmt = none) -> 0x64 followed by 'n','o','n','e'
        val encodedStr = CborEncoder.encode("none")
        assertEquals(5, encodedStr.size)
        assertEquals(0x64.toByte(), encodedStr[0]) // Major type 3 (text string), len 4
        assertEquals("none", String(encodedStr.copyOfRange(1, 5), Charsets.UTF_8))

        // Byte string of 32 bytes -> 0x58, 0x20 followed by 32 bytes
        val rawBytes = ByteArray(32) { it.toByte() }
        val encodedBytes = CborEncoder.encode(rawBytes)
        assertEquals(34, encodedBytes.size)
        assertEquals(0x58.toByte(), encodedBytes[0]) // Major type 2 (byte string), 1-byte len
        assertEquals(0x20.toByte(), encodedBytes[1]) // len 32
        assertArrayEquals(rawBytes, encodedBytes.copyOfRange(2, 34))
    }

    @Test
    fun `encodes empty map and nested maps`() {
        // Empty map -> 0xA0 (major type 5, length 0)
        assertArrayEquals(byteArrayOf(0xA0.toByte()), CborEncoder.encode(emptyMap<String, Any>()))

        // Map with 1 key-value pair: {"fmt": "none"}
        val map = linkedMapOf("fmt" to "none")
        val encoded = CborEncoder.encode(map)
        assertEquals(0xA1.toByte(), encoded[0]) // Major type 5 (map), len 1
    }

    @Test
    fun `encodes boolean and null`() {
        assertArrayEquals(byteArrayOf(0xF5.toByte()), CborEncoder.encode(true))
        assertArrayEquals(byteArrayOf(0xF4.toByte()), CborEncoder.encode(false))
        assertArrayEquals(byteArrayOf(0xF6.toByte()), CborEncoder.encode(null))
    }
}
