package org.zzc.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.image.BufferedImage

class QrCodeGeneratorTest {

    private val generator = QrCodeGenerator()

    @Test
    fun `plain text round trips`() {
        val text = "https://www.jetbrains.com/help/idea/"
        val code = generator.generate(text, ErrorCorrectionLevel.M, 320)

        assertEquals(text, decode(code.image))
    }

    @Test
    fun `utf8 content round trips`() {
        val text = "你好，DevToolkit！二维码测试 🚀"
        val code = generator.generate(text, ErrorCorrectionLevel.H, 512)

        assertEquals(text, decode(code.image))
    }

    @Test
    fun `long content round trips`() {
        val text = "https://example.com/?q=" + "a".repeat(1000)
        val code = generator.generate(text, ErrorCorrectionLevel.M, 640)

        assertEquals(text, decode(code.image))
        assertTrue(code.moduleCount > 21)
        assertTrue(code.version in 2..40)
    }

    @Test
    fun `max byte capacity decreases with stronger error correction`() {
        val capacities = listOf(ErrorCorrectionLevel.L, ErrorCorrectionLevel.M, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H)
            .map { QrCodeGenerator.maxByteCapacity(it) }

        assertEquals(capacities.sortedDescending(), capacities)
        assertTrue(capacities.last() > 0)
    }

    @Test
    fun `content beyond capacity fails`() {
        try {
            generator.generate("a".repeat(5000), ErrorCorrectionLevel.H, 512)
            fail("Expected QrCodeException")
        } catch (e: QrCodeException) {
            assertEquals(QrCodeError.CONTENT_TOO_LONG, e.error)
        }
    }

    @Test
    fun `empty content fails`() {
        try {
            generator.generate("", ErrorCorrectionLevel.M, 320)
            fail("Expected QrCodeException")
        } catch (e: QrCodeException) {
            assertEquals(QrCodeError.EMPTY_CONTENT, e.error)
        }
    }

    @Test
    fun `higher error correction uses more modules`() {
        val text = "DevToolkit 二维码容错率对比"
        val low = generator.generate(text, ErrorCorrectionLevel.L, 512)
        val high = generator.generate(text, ErrorCorrectionLevel.H, 512)

        assertTrue(high.moduleCount > low.moduleCount)
    }

    @Test
    fun `rendered image respects requested size and module grid`() {
        val code = generator.generate("size check", ErrorCorrectionLevel.M, 400)

        assertTrue(code.image.width <= 400)
        assertEquals(code.image.width, code.image.height)
        assertEquals(0, code.image.width % code.moduleCount)
        assertEquals((code.moduleCount - 25) / 4, code.version)
    }

    private fun decode(image: BufferedImage): String {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return MultiFormatReader().decode(bitmap).text
    }
}
