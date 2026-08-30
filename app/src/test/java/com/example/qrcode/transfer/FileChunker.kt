package com.example.qrcode.transfer

import android.content.Context
import android.net.Uri
import com.example.qrcode.protocol.PacketCodec1
import com.example.qrcode.protocol.QrPacket
import java.io.InputStream

object FileChunker {

    // Max bytes per chunk — tuned later based on real QR scan testing
    const val CHUNK_PAYLOAD_SIZE = 800

    /**
     * Reads the file at [uri], splits it into chunks, and returns
     * a list of QrPacket ready to be encoded into QR strings.
     */
    fun chunkFile(
        context: Context,
        uri: Uri,
        sessionId: String,
        fileId: String
    ): List<QrPacket> {
        val bytes = readAllBytes(context, uri)
        val chunks = bytes.toList().chunked(CHUNK_PAYLOAD_SIZE).map { it.toByteArray() }

        return chunks.mapIndexed { index, chunkBytes ->
            QrPacket(
                version = 1,
                sessionId = sessionId,
                fileId = fileId,
                totalChunks = chunks.size,
                chunkIndex = index,
                payload = chunkBytes,
                checksum = PacketCodec1.computeChecksum(chunkBytes)
            )
        }
    }

    private fun readAllBytes(context: Context, uri: Uri): ByteArray {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open file at $uri")

        return inputStream.use { it.readBytes() }
    }
}