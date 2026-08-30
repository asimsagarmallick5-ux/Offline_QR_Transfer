package com.example.qrcode.protocol

data class QrPacket(
    val version: Int,          // protocol version, e.g. 1
    val sessionId: String,     // short ID identifying this transfer session
    val fileId: String,        // short ID identifying this specific file
    val totalChunks: Int,      // total number of chunks for this file
    val chunkIndex: Int,       // 0-based index of this chunk
    val payload: ByteArray,    // the actual chunk of file data (encrypted, if enabled)
    val checksum: Int          // CRC32 of payload, for per-chunk integrity check
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrPacket) return false
        return version == other.version &&
                sessionId == other.sessionId &&
                fileId == other.fileId &&
                totalChunks == other.totalChunks &&
                chunkIndex == other.chunkIndex &&
                payload.contentEquals(other.payload) &&
                checksum == other.checksum
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + fileId.hashCode()
        result = 31 * result + totalChunks
        result = 31 * result + chunkIndex
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + checksum
        return result
    }
}