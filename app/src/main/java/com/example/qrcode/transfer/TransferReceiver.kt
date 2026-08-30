package com.example.qrcode.transfer

import android.content.Context
import com.example.qrcode.protocol.CryptoUtils
import com.example.qrcode.protocol.PacketCodec1
import com.example.qrcode.protocol.QrFrameCodec
import com.example.qrcode.protocol.QrPacket
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

sealed class ChunkResult {
    object NewChunk : ChunkResult()
    object DuplicateChunk : ChunkResult()
    object InvalidPacket : ChunkResult()
    object ChecksumFailed : ChunkResult()
    object WrongSession : ChunkResult()
}

class TransferReceiver(private val context: Context) {

    private val receivedChunks = mutableMapOf<Int, ByteArray>()
    private var totalChunks: Int? = null
    private var fileId: String? = null
    private var version: Int = 1

    val receivedCount: Int get() = receivedChunks.size
    val isSecure: Boolean get() = version == 2

    /**
     * Feed a raw QR string here. Returns a ChunkResult describing exactly
     * what happened with this frame (new chunk, duplicate, corrupted, etc.)
     */
    fun receiveQrString(qrString: String): ChunkResult {
        val packet: QrPacket = try {
            QrFrameCodec.qrStringToPacket(qrString)
        } catch (e: Exception) {
            return ChunkResult.InvalidPacket // not a valid packet (e.g. random QR code)
        }

        val expectedChecksum = PacketCodec1.computeChecksum(packet.payload)
        if (expectedChecksum != packet.checksum) {
            return ChunkResult.ChecksumFailed // corrupted chunk
        }

        if (fileId == null) {
            fileId = packet.fileId
            version = packet.version
            loadProgress(packet.fileId) // resume if a previous partial transfer exists
        }
        if (packet.fileId != fileId) return ChunkResult.WrongSession // belongs to a different transfer

        totalChunks = packet.totalChunks

        if (receivedChunks.containsKey(packet.chunkIndex)) {
            return ChunkResult.DuplicateChunk // already have this one (normal, since QR loops)
        }

        receivedChunks[packet.chunkIndex] = packet.payload
        saveProgress()
        return ChunkResult.NewChunk
    }

    fun isComplete(): Boolean {
        val total = totalChunks ?: return false
        return receivedChunks.size == total
    }

    fun totalChunksExpected(): Int? = totalChunks

    /**
     * Call only after isComplete() is true.
     * @param password Optional password if the file was encrypted.
     */
    fun reassembleFile(password: String? = null): ByteArray {
        val total = totalChunks ?: throw IllegalStateException("Transfer not complete")
        val ordered = (0 until total).map { index ->
            receivedChunks[index] ?: throw IllegalStateException("Missing chunk $index")
        }
        val data = ordered.reduce { acc, bytes -> acc + bytes }
        
        val result = if (version == 2) {
            if (password.isNullOrBlank()) throw SecurityException("Password required for secure transfer")
            try {
                CryptoUtils.decrypt(data, password)
            } catch (e: Exception) {
                throw SecurityException("Decryption failed: check your password")
            }
        } else {
            data
        }

        clearProgress() // done, remove the cached progress file
        return result
    }

    /**
     * Call when the user explicitly cancels the receive operation.
     */
    fun cancel() {
        clearProgress()
        receivedChunks.clear()
        totalChunks = null
        fileId = null
        version = 1
    }

    // --- Persistence (survives app backgrounding/kill) ---

    private fun progressFile(id: String): File =
        File(context.filesDir, "transfer_progress_$id.dat")

    private fun saveProgress() {
        val id = fileId ?: return
        try {
            ObjectOutputStream(progressFile(id).outputStream()).use { out ->
                out.writeObject(totalChunks)
                out.writeObject(version)
                out.writeObject(HashMap(receivedChunks))
            }
        } catch (e: Exception) {
            e.printStackTrace() // non-fatal — worst case we just don't resume
        }
    }

    private fun loadProgress(id: String) {
        val file = progressFile(id)
        if (!file.exists()) return
        try {
            ObjectInputStream(file.inputStream()).use { input ->
                @Suppress("UNCHECKED_CAST")
                val savedTotal = input.readObject() as Int?
                @Suppress("UNCHECKED_CAST")
                val savedVersion = input.readObject() as Int
                @Suppress("UNCHECKED_CAST")
                val savedChunks = input.readObject() as HashMap<Int, ByteArray>
                totalChunks = savedTotal
                version = savedVersion
                receivedChunks.putAll(savedChunks)
            }
        } catch (e: Exception) {
            e.printStackTrace() // if load fails, just start fresh
        }
    }

    private fun clearProgress() {
        fileId?.let { progressFile(it).delete() }
    }
}
