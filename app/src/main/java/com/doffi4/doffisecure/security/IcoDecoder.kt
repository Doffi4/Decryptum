package com.doffi4.doffisecure.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Coil [Decoder] for ICO format (.ico files).
 *
 * Android BitmapFactory and Coil do not natively decode Windows ICO files.
 * This decoder parses the ICO container and extracts either:
 *  - Embedded PNG image (used by most modern websites).
 *  - DIB / BMP icon payload (constructed into standard BMP format).
 */
class IcoDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val bytes = source.source().readByteArray()
        if (bytes.size < 6) return null

        val bitmap = decodeIco(bytes) ?: return null
        return DecodeResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false
        )
    }

    private fun decodeIco(bytes: ByteArray): Bitmap? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val reserved = buffer.short
        val type = buffer.short
        val count = buffer.short.toInt() and 0xFFFF

        // Must be ICO type (1) with at least 1 image
        if (reserved.toInt() != 0 || type.toInt() != 1 || count <= 0) {
            return null
        }

        var bestOffset = 0
        var bestLength = 0
        var bestScore = -1

        for (i in 0 until count) {
            val entryPos = 6 + i * 16
            if (entryPos + 16 > bytes.size) break

            val widthByte = bytes[entryPos].toInt() and 0xFF
            val heightByte = bytes[entryPos + 1].toInt() and 0xFF
            val width = if (widthByte == 0) 256 else widthByte

            val bytesInRes = buffer.getInt(entryPos + 8)
            val imageOffset = buffer.getInt(entryPos + 12)

            if (imageOffset >= 0 && bytesInRes > 0 && imageOffset + bytesInRes <= bytes.size) {
                // Score: prioritize standard favicon sizes (32..128px)
                val score = when {
                    width in 32..128 -> 1000 + width
                    width > 128 -> 500 + width
                    else -> width
                }
                if (score > bestScore) {
                    bestScore = score
                    bestOffset = imageOffset
                    bestLength = bytesInRes
                }
            }
        }

        if (bestOffset == 0 || bestLength <= 0) {
            return null
        }

        // 1. Check for PNG signature: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        if (bestLength >= 8 &&
            bytes[bestOffset] == 0x89.toByte() &&
            bytes[bestOffset + 1] == 'P'.code.toByte() &&
            bytes[bestOffset + 2] == 'N'.code.toByte() &&
            bytes[bestOffset + 3] == 'G'.code.toByte()
        ) {
            return BitmapFactory.decodeByteArray(bytes, bestOffset, bestLength)
        }

        // 2. Fallback to DIB (BMP format inside ICO)
        return decodeDib(bytes, bestOffset, bestLength)
    }

    private fun decodeDib(bytes: ByteArray, offset: Int, length: Int): Bitmap? {
        return try {
            if (length < 40) return null
            val dib = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)
            val biSize = dib.int
            if (biSize != 40) return null // BITMAPINFOHEADER is 40 bytes
            val biWidth = dib.int
            val biHeight = dib.int / 2 // ICO height includes XOR + AND masks, so halve it
            val biPlanes = dib.short
            val biBitCount = dib.short
            val biCompression = dib.int
            val biSizeImage = dib.int
            val biXPelsPerMeter = dib.int
            val biYPelsPerMeter = dib.int
            val biClrUsed = dib.int
            val biClrImportant = dib.int

            val numColors = if (biClrUsed > 0) biClrUsed else if (biBitCount <= 8) 1 shl biBitCount.toInt() else 0
            val paletteSize = numColors * 4
            val bmpHeaderSize = 14
            val dibHeaderSize = 40
            val pixelDataOffset = bmpHeaderSize + dibHeaderSize + paletteSize
            val totalFileSize = bmpHeaderSize + length

            val out = ByteArrayOutputStream(totalFileSize)
            val headerBuf = ByteBuffer.allocate(bmpHeaderSize + dibHeaderSize).order(ByteOrder.LITTLE_ENDIAN)

            // 14-byte standard BMP file header
            headerBuf.put('B'.code.toByte())
            headerBuf.put('M'.code.toByte())
            headerBuf.putInt(totalFileSize)
            headerBuf.putShort(0)
            headerBuf.putShort(0)
            headerBuf.putInt(pixelDataOffset)

            // 40-byte BITMAPINFOHEADER with adjusted height
            headerBuf.putInt(biSize)
            headerBuf.putInt(biWidth)
            headerBuf.putInt(biHeight)
            headerBuf.putShort(biPlanes)
            headerBuf.putShort(biBitCount)
            headerBuf.putInt(biCompression)
            headerBuf.putInt(biSizeImage)
            headerBuf.putInt(biXPelsPerMeter)
            headerBuf.putInt(biYPelsPerMeter)
            headerBuf.putInt(biClrUsed)
            headerBuf.putInt(biClrImportant)

            out.write(headerBuf.array())
            out.write(bytes, offset + 40, length - 40)

            val bmpBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bmpBytes, 0, bmpBytes.size)
        } catch (_: Throwable) {
            null
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val source = result.source.source()
            val isIco = source.rangeEquals(0, ICO_MAGIC)
            return if (isIco) IcoDecoder(result.source, options) else null
        }

        private companion object {
            val ICO_MAGIC = byteArrayOf(0, 0, 1, 0).toByteString()
        }
    }
}
