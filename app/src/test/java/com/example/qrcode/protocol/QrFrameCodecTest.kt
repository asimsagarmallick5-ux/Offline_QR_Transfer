package com.example.qrcode.protocol

import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals

class QrFrameCodecTest {

    @Test
    fun roundTrip_singleChunk_reconstructsOriginalBytes() {
        val originalFileBytes = "Hello, this is a small test file!".toByteArray(Charsets.UTF_8)

        val packet = QrPacket(
            version = 1,
            sessionId = "sess0001",
            fileId = "file0001",
            totalChunks = 1,
            chunkIndex = 0,
            payload = originalFileBytes,
            checksum = PacketCodec1.computeChecksum(originalFileBytes)
        )

        // Encode: packet -> QR string
        val qrString = QrFrameCodec.packetToQrString(packet)

        // Decode: QR string -> packet
        val decodedPacket = QrFrameCodec.qrStringToPacket(qrString)

        // Verify every field survived the round trip
        assertEquals(packet.version, decodedPacket.version)
        assertEquals(packet.sessionId, decodedPacket.sessionId)
        assertEquals(packet.fileId, decodedPacket.fileId)
        assertEquals(packet.totalChunks, decodedPacket.totalChunks)
        assertEquals(packet.chunkIndex, decodedPacket.chunkIndex)
        assertArrayEquals(packet.payload, decodedPacket.payload)
        assertEquals(packet.checksum, decodedPacket.checksum)

        // Verify checksum still matches the payload after decode
        val recomputedChecksum = PacketCodec1.computeChecksum(decodedPacket.payload)
        assertEquals(packet.checksum, recomputedChecksum)
    }

    @Test
    fun roundTrip_multipleChunks_reassemblesOriginalFile() {
        val originalFileBytes = "This is a longer test file that will be split into multiple chunks to simulate real file transfer behavior.".toByteArray(Charsets.UTF_8)

        val chunkSize = 20
        val chunks = originalFileBytes.toList().chunked(chunkSize).map { it.toByteArray() }
        val sessionId = "sess0002"
        val fileId = "file0002"

        // Simulate sender: build a QR string for each chunk
        val qrStrings = chunks.mapIndexed { index, chunkBytes ->
            val packet = QrPacket(
                version = 1,
                sessionId = sessionId,
                fileId = fileId,
                totalChunks = chunks.size,
                chunkIndex = index,
                payload = chunkBytes,
                checksum = PacketCodec1.computeChecksum(chunkBytes)
            )
            QrFrameCodec.packetToQrString(packet)
        }

        // Simulate receiver: decode each QR string back into a packet,
        // verify checksum, and place payload into the correct slot
        val receivedChunks = arrayOfNulls<ByteArray>(chunks.size)
        for (qrString in qrStrings) {
            val decodedPacket = QrFrameCodec.qrStringToPacket(qrString)
            val expectedChecksum = PacketCodec1.computeChecksum(decodedPacket.payload)
            assertEquals(
                "Checksum mismatch on chunk ${decodedPacket.chunkIndex}",
                decodedPacket.checksum,
                expectedChecksum
            )
            receivedChunks[decodedPacket.chunkIndex] = decodedPacket.payload
        }

        // Reassemble and compare to original
        val reassembled = receivedChunks.filterNotNull().reduce { acc, bytes -> acc + bytes }
        assertArrayEquals(originalFileBytes, reassembled)
    }
}