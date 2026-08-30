package com.example.qrcode.protocol

import java.nio.ByteBuffer
import java.util.zip.CRC32

object PacketCodec1 {

    private const val VERSION_SIZE = 1
    private const val SESSION_ID_SIZE = 8
    private const val FILE_ID_SIZE = 8
    private const val TOTAL_CHUNKS_SIZE = 4
    private const val CHUNK_INDEX_SIZE = 4
    private const val PAYLOAD_LEN_SIZE = 2
    private const val CHECKSUM_SIZE = 4

    fun encode(packet: QrPacket): ByteArray {
        val sessionIdBytes = fixedLength(packet.sessionId.toByteArray(Charsets.UTF_8), SESSION_ID_SIZE)
        val fileIdBytes = fixedLength(packet.fileId.toByteArray(Charsets.UTF_8), FILE_ID_SIZE)

        val buffer = ByteBuffer.allocate(
            VERSION_SIZE + SESSION_ID_SIZE + FILE_ID_SIZE +
                    TOTAL_CHUNKS_SIZE + CHUNK_INDEX_SIZE + PAYLOAD_LEN_SIZE +
                    packet.payload.size + CHECKSUM_SIZE
        )

        buffer.put(packet.version.toByte())
        buffer.put(sessionIdBytes)
        buffer.put(fileIdBytes)
        buffer.putInt(packet.totalChunks)
        buffer.putInt(packet.chunkIndex)
        buffer.putShort(packet.payload.size.toShort())
        buffer.put(packet.payload)
        buffer.putInt(packet.checksum)

        return buffer.array()
    }

    fun decode(bytes: ByteArray): QrPacket {
        val buffer = ByteBuffer.wrap(bytes)

        val version = buffer.get().toInt()

        val sessionIdBytes = ByteArray(SESSION_ID_SIZE)
        buffer.get(sessionIdBytes)
        val sessionId = String(sessionIdBytes, Charsets.UTF_8).trimEnd('\u0000')

        val fileIdBytes = ByteArray(FILE_ID_SIZE)
        buffer.get(fileIdBytes)
        val fileId = String(fileIdBytes, Charsets.UTF_8).trimEnd('\u0000')

        val totalChunks = buffer.int
        val chunkIndex = buffer.int
        val payloadLen = buffer.short.toInt() and 0xFFFF

        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val checksum = buffer.int

        return QrPacket(version, sessionId, fileId, totalChunks, chunkIndex, payload, checksum)
    }

    fun computeChecksum(payload: ByteArray): Int {
        val crc = CRC32()
        crc.update(payload)
        return crc.value.toInt()
    }

    private fun fixedLength(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size == size) return bytes
        val result = ByteArray(size)
        System.arraycopy(bytes, 0, result, 0, minOf(bytes.size, size))
        return result
    }
}