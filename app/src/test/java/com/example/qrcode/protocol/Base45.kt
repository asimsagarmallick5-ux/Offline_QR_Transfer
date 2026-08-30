package com.example.qrcode.protocol

import java.io.ByteArrayOutputStream

object Base45 {
    private const val CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            val c = n % 45
            val d = (n / 45) % 45
            val e = n / (45 * 45)
            sb.append(CHARSET[c])
            sb.append(CHARSET[d])
            sb.append(CHARSET[e])
            i += 2
        }
        if (i < data.size) {
            val n = data[i].toInt() and 0xFF
            val c = n % 45
            val d = n / 45
            sb.append(CHARSET[c])
            sb.append(CHARSET[d])
        }
        return sb.toString()
    }

    fun decode(input: String): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i + 2 < input.length) {
            val c = CHARSET.indexOf(input[i])
            val d = CHARSET.indexOf(input[i + 1])
            val e = CHARSET.indexOf(input[i + 2])
            val n = c + d * 45 + e * 45 * 45
            out.write((n shr 8) and 0xFF)
            out.write(n and 0xFF)
            i += 3
        }
        if (i < input.length) {
            val c = CHARSET.indexOf(input[i])
            val d = CHARSET.indexOf(input[i + 1])
            val n = c + d * 45
            out.write(n and 0xFF)
        }
        return out.toByteArray()
    }
}
