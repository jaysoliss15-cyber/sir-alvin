package com.example.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object MagicByteValidator {

    enum class ImageFormat {
        PNG,
        JPEG,
        WEBP,
        UNKNOWN
    }

    fun detectFormat(bytes: ByteArray): ImageFormat {
        if (bytes.size < 12) return ImageFormat.UNKNOWN

        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return ImageFormat.PNG
        }

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return ImageFormat.JPEG
        }

        // WebP: RIFF .... WEBP
        if (bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return ImageFormat.WEBP
        }

        return ImageFormat.UNKNOWN
    }

    fun isValidImageBytes(bytes: ByteArray): Boolean {
        return detectFormat(bytes) != ImageFormat.UNKNOWN
    }

    fun validateAndDecode(bytes: ByteArray): Bitmap? {
        if (!isValidImageBytes(bytes)) {
            return null
        }
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Throwable) {
            null
        }
    }
}
