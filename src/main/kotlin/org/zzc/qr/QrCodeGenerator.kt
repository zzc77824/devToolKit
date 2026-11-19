package org.zzc.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * A rendered QR code.
 *
 * @property text the encoded payload.
 * @property errorCorrection the error correction level used for encoding.
 * @property version QR code version (1..40), derived from [moduleCount].
 * @property moduleCount number of modules per side, quiet zone included.
 * @property image square rendered image, black modules on a white background.
 */
data class QrCode(
    val text: String,
    val errorCorrection: ErrorCorrectionLevel,
    val version: Int,
    val moduleCount: Int,
    val image: BufferedImage,
)

/** Machine-readable reason why a QR code could not be generated. */
enum class QrCodeError {
    /** The input text is empty. */
    EMPTY_CONTENT,

    /** The text does not fit into a QR code at the requested error correction level. */
    CONTENT_TOO_LONG,
}

/** Thrown when a QR code cannot be generated; [error] tells the UI which message to show. */
class QrCodeException(
    val error: QrCodeError,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Encodes arbitrary text into a QR code image.
 *
 * The payload is encoded in UTF-8, so non-ASCII text (including Chinese and emoji) round-trips.
 * The maximum payload length is defined by the QR specification (up to ~2953 bytes at level L);
 * longer content fails with [QrCodeError.CONTENT_TOO_LONG].
 *
 * Rendering uses whole-pixel modules so that the result stays crisp and scannable at any size.
 */
class QrCodeGenerator(
    private val marginModules: Int = DEFAULT_MARGIN_MODULES,
) {

    /**
     * Encodes [text] and renders it at (at most) [preferredSizePx] pixels per side.
     *
     * If the requested size is not an exact multiple of the module count, the image is slightly
     * smaller than [preferredSizePx] instead of blurring the modules; if the code has more modules
     * than requested pixels, module size falls back to one pixel and the image grows accordingly.
     */
    fun generate(
        text: String,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        preferredSizePx: Int = DEFAULT_SIZE_PX,
    ): QrCode {
        if (text.isEmpty()) {
            throw QrCodeException(QrCodeError.EMPTY_CONTENT, "QR code content is empty")
        }
        require(preferredSizePx > 0) { "preferredSizePx must be positive: $preferredSizePx" }

        val matrix = try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
                    EncodeHintType.ERROR_CORRECTION to errorCorrection,
                    EncodeHintType.MARGIN to marginModules,
                ),
            )
        } catch (e: WriterException) {
            // For QR codes the only realistic writing failure is an overlong payload.
            throw QrCodeException(QrCodeError.CONTENT_TOO_LONG, e.message ?: "QR code content is too long", e)
        }

        val moduleCount = matrix.width
        val scale = max(1, preferredSizePx / moduleCount)
        val side = moduleCount * scale
        val image = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, side, side)
            graphics.color = Color.BLACK
            for (y in 0 until moduleCount) {
                for (x in 0 until moduleCount) {
                    if (matrix.get(x, y)) {
                        graphics.fillRect(x * scale, y * scale, scale, scale)
                    }
                }
            }
        } finally {
            graphics.dispose()
        }

        val version = (moduleCount - 2 * marginModules - 17) / 4
        return QrCode(text, errorCorrection, version, moduleCount, image)
    }

    companion object {
        /**
         * Maximum payload in UTF-8 bytes for QR version 40 (the largest QR code) in byte mode.
         * Numeric and alphanumeric payloads can be longer, so this is a lower bound used only
         * to produce a helpful error message.
         */
        fun maxByteCapacity(errorCorrection: ErrorCorrectionLevel): Int = when (errorCorrection) {
            ErrorCorrectionLevel.L -> 2953
            ErrorCorrectionLevel.M -> 2331
            ErrorCorrectionLevel.Q -> 1663
            ErrorCorrectionLevel.H -> 1273
        }

        const val DEFAULT_SIZE_PX = 320
        const val MIN_SIZE_PX = 64
        const val MAX_SIZE_PX = 2048
        const val DEFAULT_MARGIN_MODULES = 4
    }
}
