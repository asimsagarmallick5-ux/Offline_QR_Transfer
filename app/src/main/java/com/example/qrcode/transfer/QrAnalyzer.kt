package com.example.qrcode.transfer

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class QrAnalyzer(
    private val onQrDecoded: (String) -> Unit
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (imageProxy.format == ImageFormat.YUV_420_888) {
                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                val source = PlanarYUVLuminanceSource(
                    data,
                    imageProxy.width,
                    imageProxy.height,
                    0,
                    0,
                    imageProxy.width,
                    imageProxy.height,
                    false
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                try {
                    val result = reader.decode(binaryBitmap)
                    onQrDecoded(result.text)
                } catch (e: NotFoundException) {
                    // No QR code found in this frame — expected most of the time, ignore
                }
            }
        } finally {
            imageProxy.close()
        }
    }
}