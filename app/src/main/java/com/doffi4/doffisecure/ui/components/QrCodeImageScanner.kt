package com.doffi4.doffisecure.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "QrCodeImageScanner"

/**
 * Extracts and decodes a QR code from a local image Uri (e.g. gallery photo or screenshot).
 * First attempts decoding using Google ML Kit's on-device vision barcode scanner.
 * If ML Kit fails or finds no QR code (e.g. due to dense Google Authenticator migration QR,
 * high-resolution screenshot downsampling, or release R8 environment differences),
 * it falls back to a multi-strategy ZXing software decoder.
 */
fun scanQrCodeFromUri(
    context: Context,
    uri: Uri,
    onSuccess: (String) -> Unit,
    onNotFound: () -> Unit
) {
    try {
        val inputImage = InputImage.fromFilePath(context, uri)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val foundCode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (foundCode != null) {
                    Log.d(TAG, "ML Kit successfully decoded QR code")
                    onSuccess(foundCode)
                } else {
                    Log.d(TAG, "ML Kit found no barcode, attempting ZXing fallback")
                    fallbackZxing(context, uri, onSuccess, onNotFound)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "ML Kit scanning failed, attempting ZXing fallback", e)
                fallbackZxing(context, uri, onSuccess, onNotFound)
            }
    } catch (e: Exception) {
        Log.w(TAG, "ML Kit initialization failed, attempting ZXing fallback", e)
        fallbackZxing(context, uri, onSuccess, onNotFound)
    }
}

/**
 * Fallback decoder using ZXing with multiple binarizers (Hybrid and Global Histogram)
 * and inverse luminance for dark-themed / dense QR screenshots.
 */
private fun fallbackZxing(
    context: Context,
    uri: Uri,
    onSuccess: (String) -> Unit,
    onNotFound: () -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val bitmap = loadBitmapFromUri(context, uri)
            if (bitmap == null) {
                Log.e(TAG, "Failed to load bitmap from uri for ZXing fallback")
                withContext(Dispatchers.Main) { onNotFound() }
                return@launch
            }

            val decodedText = decodeQrWithZxing(bitmap)
            withContext(Dispatchers.Main) {
                if (!decodedText.isNullOrBlank()) {
                    Log.d(TAG, "ZXing fallback successfully decoded QR code")
                    onSuccess(decodedText)
                } else {
                    Log.w(TAG, "QR code not found by both ML Kit and ZXing")
                    onNotFound()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ZXing fallback scanner", e)
            withContext(Dispatchers.Main) { onNotFound() }
        }
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2560): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        var inSampleSize = 1
        while (boundsOptions.outWidth / inSampleSize > maxDimension ||
            boundsOptions.outHeight / inSampleSize > maxDimension
        ) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decode bitmap from stream", e)
        null
    }
}

private fun decodeQrWithZxing(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val source = RGBLuminanceSource(width, height, pixels)
    val hints = mapOf(
        DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )
    val reader = QRCodeReader()

    // Pass 1: Standard Hybrid Binarizer
    try {
        val result = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
        if (!result.text.isNullOrBlank()) return result.text
    } catch (_: Exception) {
        reader.reset()
    }

    // Pass 2: Global Histogram Binarizer (better for high-contrast screenshots)
    try {
        val result = reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints)
        if (!result.text.isNullOrBlank()) return result.text
    } catch (_: Exception) {
        reader.reset()
    }

    // Pass 3: Inverted luminance (for dark mode QR codes or screenshots)
    try {
        val invertedSource = source.invert()
        val result = reader.decode(BinaryBitmap(HybridBinarizer(invertedSource)), hints)
        if (!result.text.isNullOrBlank()) return result.text
    } catch (_: Exception) {
        reader.reset()
    }

    return null
}
