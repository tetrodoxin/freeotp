package org.fedorahosted.freeotp.ui.scan

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGTAG = "QrCodes"

private val hints = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.ALSO_INVERTED to true,
)

/**
 * Decodes a QR code from an 8-bit luminance image (the Y plane of a camera frame). Rows are
 * [rowStride] bytes apart, which can be more than [width]. Returns null if there is none.
 */
fun decodeQr(luminance: ByteArray, width: Int, height: Int, rowStride: Int = width): String? {
    val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
    return try {
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (e: ReaderException) {
        null
    }
}

/**
 * Looks for a QR code in each camera frame and reports the first one found, exactly once, on the
 * analysis thread.
 */
class QrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val done = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        image.use {
            if (done.get()) return
            val plane = it.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining()).also { b -> buffer.get(b) }
            val text = decodeQr(bytes, it.width, it.height, plane.rowStride) ?: return
            if (done.compareAndSet(false, true)) onDecoded(text)
        }
    }
}

/**
 * Renders [text] as a QR code with one pixel per module (including the quiet zone); scale it up
 * without filtering. Returns a white pixel if it can't be encoded.
 */
fun qrCodeBitmap(text: String): Bitmap {
    val matrix = try {
        // ZXing refuses to encode an empty string.
        if (text.isEmpty()) null else QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
    } catch (e: WriterException) {
        Log.e(LOGTAG, "Exception", e)
        null
    }
    if (matrix == null) return Bitmap.createBitmap(intArrayOf(-1), 1, 1, Bitmap.Config.ARGB_8888)

    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else -1
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
