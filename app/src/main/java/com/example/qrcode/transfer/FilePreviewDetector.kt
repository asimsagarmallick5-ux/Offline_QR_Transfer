package com.example.qrcode.transfer

import android.graphics.Bitmap
import android.graphics.BitmapFactory

sealed class FilePreview {
    data class ImagePreview(val bitmap: Bitmap, val info: FileInfo) : FilePreview()
    data class GifPreview(val bytes: ByteArray, val info: FileInfo) : FilePreview()
    data class VideoPreview(val bytes: ByteArray, val info: FileInfo) : FilePreview()
    data class TextPreview(val snippet: String, val info: FileInfo) : FilePreview()
    data class GenericPreview(val info: FileInfo) : FilePreview()
}

data class FileInfo(
    val sizeBytes: Long,
    val type: String,
    val mimeType: String,
    val extension: String? = null
)

object FilePreviewDetector {

    fun detect(bytes: ByteArray): FilePreview {
        val initialMime = detectMimeType(bytes)
        var info = FileInfo(
            sizeBytes = bytes.size.toLong(),
            type = mapMimeToDisplayType(initialMime),
            mimeType = initialMime
        )

        when {
            initialMime == "image/gif" -> {
                return FilePreview.GifPreview(bytes, info)
            }
            initialMime.startsWith("video/") -> {
                return FilePreview.VideoPreview(bytes, info)
            }
            initialMime.startsWith("image/") -> {
                val bitmap = try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
                if (bitmap != null) {
                    return FilePreview.ImagePreview(bitmap, info)
                }
            }
            looksLikeText(bytes) -> {
                val text = String(bytes, Charsets.UTF_8)
                info = info.copy(type = "Text/Document", mimeType = "text/plain")
                return FilePreview.TextPreview(text.take(1000), info)
            }
        }

        return FilePreview.GenericPreview(info)
    }

    private fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size < 12) return "application/octet-stream"
        
        val hex = bytes.take(12).joinToString("") { "%02x".format(it) }.uppercase()
        
        return when {
            hex.startsWith("89504E47") -> "image/png"
            hex.startsWith("FFD8FF") -> "image/jpeg"
            hex.startsWith("47494638") -> "image/gif"
            hex.startsWith("25504446") -> "application/pdf"
            hex.contains("66747970") -> "video/mp4" // ftyp
            hex.startsWith("504B0304") -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun mapMimeToDisplayType(mime: String): String {
        return when {
            mime.startsWith("image/") -> "Image"
            mime.startsWith("video/") -> "Video"
            mime == "application/pdf" -> "PDF Document"
            mime == "application/zip" -> "Archive"
            else -> "Binary Data"
        }
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sampleSize = minOf(bytes.size, 1000)
        var printable = 0
        for (i in 0 until sampleSize) {
            val c = bytes[i].toInt().toChar()
            if (c == '\n' || c == '\r' || c == '\t' || (c.code in 32..126)) {
                printable++
            }
        }
        return printable.toFloat() / sampleSize > 0.9f
    }
}