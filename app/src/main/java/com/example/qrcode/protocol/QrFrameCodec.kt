package com.example.qrcode.protocol

object QrFrameCodec {
    fun packetToQrString(packet: QrPacket): String {
        val bytes = PacketCodec1.encode(packet)
        return Base45.encode(bytes)
    }

    fun qrStringToPacket(qrString: String): QrPacket {
        val bytes = Base45.decode(qrString)
        return PacketCodec1.decode(bytes)
    }
}