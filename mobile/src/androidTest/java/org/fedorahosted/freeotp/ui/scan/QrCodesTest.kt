package org.fedorahosted.freeotp.ui.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodesTest {
    private val uri = "otpauth://totp/Example:alice?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Example"

    /** A camera-like Y plane: [size]² pixels, rows [rowStride] bytes apart, padding bytes grey. */
    private fun luminance(text: String, size: Int, rowStride: Int, inverted: Boolean = false): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val dark: Byte = if (inverted) -1 else 0
        val light: Byte = if (inverted) 0 else -1
        val bytes = ByteArray(rowStride * size) { 0x80.toByte() }
        for (y in 0 until size) for (x in 0 until size) {
            bytes[y * rowStride + x] = if (matrix[x, y]) dark else light
        }
        return bytes
    }

    @Test
    fun decodesFrame() {
        assertEquals(uri, decodeQr(luminance(uri, 400, 400), 400, 400))
    }

    @Test
    fun decodesFrameWithPaddedRows() {
        // Many cameras pad each row; ignoring the stride skews the image.
        assertEquals(uri, decodeQr(luminance(uri, 400, 448), 400, 400, rowStride = 448))
    }

    @Test
    fun decodesInvertedCode() {
        assertEquals(uri, decodeQr(luminance(uri, 400, 400, inverted = true), 400, 400))
    }

    @Test
    fun noCodeIsNull() {
        assertNull(decodeQr(ByteArray(400 * 400) { 0x80.toByte() }, 400, 400))
    }

    @Test
    fun renderedCodeDecodesBack() {
        val bitmap = qrCodeBitmap(uri)
        val scale = 8
        val size = bitmap.width * scale
        val bytes = ByteArray(size * size) { i ->
            val pixel = bitmap.getPixel(i % size / scale, i / size / scale)
            if (pixel == 0xFF000000.toInt()) 0 else -1
        }
        assertEquals(uri, decodeQr(bytes, size, size))
    }

    @Test
    fun emptyTextRendersBlank() {
        assertEquals(1, qrCodeBitmap("").width)
    }
}
