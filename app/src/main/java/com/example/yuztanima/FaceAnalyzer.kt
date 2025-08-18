package com.example.yuztanima

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FaceAnalyzer(
    private val onFaceDetected: (Face, Bitmap) -> Unit,
    private val onNoFaceDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    private var lastProcessingTimeMs = 0L
    private val processingInterval = 300L // Her 300ms'de bir işlem yap

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Performans için her karede değil, belirli aralıklarla işlem yap
        if (currentTime - lastProcessingTimeMs < processingInterval) {
            imageProxy.close()
            return
        }

        lastProcessingTimeMs = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val bitmap = imageProxyToBitmap(imageProxy)
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        onFaceDetected(faces[0], bitmap)
                    } else {
                        onNoFaceDetected()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Face detection failed", e)
                    onNoFaceDetected()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val uBuffer = imageProxy.planes[1].buffer // U
        val vBuffer = imageProxy.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U ve V değerlerini NV21 formatına uygun şekilde kopyala
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}