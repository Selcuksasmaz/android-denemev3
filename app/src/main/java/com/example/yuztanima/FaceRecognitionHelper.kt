package com.example.yuztanima

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FaceRecognitionHelper(private val context: Context) {
    private val TAG = "FaceRecognitionHelper"
    private val MODEL_FILE = "facenet.tflite"
    private val IMAGE_SIZE = 160 // FaceNet için giriş boyutu

    private var faceNetInterpreter: Interpreter? = null
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    val livenessDetector = LivenessDetector()

    init {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val model = loadModelFile(context, MODEL_FILE)
            faceNetInterpreter = Interpreter(model, options)
            Log.d(TAG, "Model başarıyla yüklendi")
        } catch (e: Exception) {
            Log.e(TAG, "Model yüklenirken hata oluştu: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        return try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()

            val detector = FaceDetection.getClient(options)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val result = detector.process(inputImage).await()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Yüz tespiti sırasında hata: ${e.message}")
            emptyList()
        }
    }

    fun cropFace(bitmap: Bitmap, face: Face, padding: Int): Bitmap {
        val rect = face.boundingBox

        val left = max(0, rect.left - padding)
        val top = max(0, rect.top - padding)
        val right = min(bitmap.width, rect.right + padding)
        val bottom = min(bitmap.height, rect.bottom + padding)

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    fun extractFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        // 1. Görüntüyü yeniden boyutlandır
        val scaledBitmap = Bitmap.createScaledBitmap(
            faceBitmap,
            IMAGE_SIZE,
            IMAGE_SIZE,
            true
        )

        // 2. Piksel değerlerini al
        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaledBitmap.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        // 3. ByteBuffer oluştur (model girişine uygun şekilde)
        val imgData = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        // 4. Normalizasyon: [-1, 1] aralığına getir
        for (pixelValue in intValues) {
            imgData.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f - 0.5f) * 2.0f)
            imgData.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f - 0.5f) * 2.0f)
            imgData.putFloat(((pixelValue and 0xFF) / 255.0f - 0.5f) * 2.0f)
        }
        imgData.rewind()

        // 5. Modelin çıktısı için doğru boyut (ör: 128!)
        val embeddings = Array(1) { FloatArray(512) }  // <--- Model çıktı boyutun 128 olmalı!

        // 6. Modeli çalıştır
        faceNetInterpreter?.run(imgData, embeddings)

        // 7. Normalizasyon (L2 norm)
        val embedding = embeddings[0]
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = Math.sqrt(sum.toDouble()).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] = embedding[i] / norm
            }
        }
        return embedding
    }

    fun storeEmbedding(embedding: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(embedding.size * 4)
            .order(ByteOrder.nativeOrder())
        for (value in embedding) {
            byteBuffer.putFloat(value)
        }
        return byteBuffer.array()
    }

    fun loadEmbedding(bytes: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.nativeOrder())
        val embedding = FloatArray(bytes.size / 4)
        for (i in embedding.indices) {
            embedding[i] = byteBuffer.float
        }
        return embedding
    }

    // Cosine similarity [L2 normalize edilmiş vektörler için nokta çarpımı]
    fun compareFaces(storedEmbedding: FloatArray, currentEmbedding: FloatArray): Float {
        if (storedEmbedding.size != currentEmbedding.size) {
            Log.e(TAG, "Embedding boyutları eşleşmiyor: ${storedEmbedding.size} vs ${currentEmbedding.size}")
            return 0f
        }
        var dotProduct = 0f
        for (i in storedEmbedding.indices) {
            dotProduct += storedEmbedding[i] * currentEmbedding[i]
        }
        val similarity = max(-1f, min(1f, dotProduct))
        return similarity
    }

    fun compareFaces(storedFaceData: ByteArray, currentFaceData: ByteArray): Float {
        val stored = loadEmbedding(storedFaceData)
        val current = loadEmbedding(currentFaceData)
        if (stored.size != current.size) {
            Log.e(TAG, "Embedding boyutları eşleşmiyor: ${stored.size} vs ${current.size}")
            return 0f
        }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in stored.indices) {
            dotProduct += stored[i] * current[i]
            normA += stored[i] * stored[i]
            normB += current[i] * current[i]
        }
        if (normA <= 0 || normB <= 0) {
            return 0f
        }
        val similarity = dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        return if (similarity > 1f) 1f else if (similarity < 0f) 0f else similarity
    }

    fun saveBitmapToFile(bitmap: Bitmap, filename: String): String {
        val dir = File(context.filesDir, "faces")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "$filename.jpg")
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream?.close()
        }
        return file.absolutePath
    }

    fun loadBitmapFromFile(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun resetLivenessDetection() {
        livenessDetector.reset()
    }

    inner class LivenessDetector {
        private var lastBlinkTime = 0L
        private var lastSmileTime = 0L
        private var lastHeadTurnTime = 0L
        private var lastHeadNodTime = 0L

        private var blinkDetected = false
        private var smileDetected = false
        private var headTurnDetected = false
        private var headNodDetected = false
        private var faceMovementDetected = false
        private var faceSizeChangeDetected = false
        private var expressionChangeDetected = false

        private var lastFacePosition = Rect()
        private var lastFaceSize = 0
        private var lastSmilingProbability = 0f
        private var movementDetectionStartTime = 0L

        fun reset() {
            blinkDetected = false
            smileDetected = false
            headTurnDetected = false
            headNodDetected = false
            faceMovementDetected = false
            faceSizeChangeDetected = false
            expressionChangeDetected = false

            lastFacePosition = Rect()
            lastFaceSize = 0
            lastSmilingProbability = 0f
            lastBlinkTime = 0L
            lastSmileTime = 0L
            lastHeadTurnTime = 0L
            lastHeadNodTime = 0L
            movementDetectionStartTime = System.currentTimeMillis()
        }

        fun checkLiveness(face: Face, faceBitmap: Bitmap): LivenessResult {
            val currentTime = System.currentTimeMillis()
            val boundingBox = face.boundingBox
            val faceWidth = boundingBox.width()
            val faceHeight = boundingBox.height()
            val currentFaceSize = faceWidth * faceHeight

            // Göz kırpma kontrolü
            val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
            val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
            val eyesOpen = leftEyeOpenProb > 0.7f && rightEyeOpenProb > 0.7f
            val eyesClosed = leftEyeOpenProb < 0.3f && rightEyeOpenProb < 0.3f

            if (eyesClosed && currentTime - lastBlinkTime > 1000) {
                lastBlinkTime = currentTime
                blinkDetected = true
            }

            // Gülümseme kontrolü
            val smileProb = face.smilingProbability ?: 0f

            if (smileProb > 0.7f && currentTime - lastSmileTime > 1500) {
                lastSmileTime = currentTime
                smileDetected = true
            }

            // İfade değişikliği kontrolü
            if (abs(smileProb - lastSmilingProbability) > 0.3f) {
                expressionChangeDetected = true
            }
            lastSmilingProbability = smileProb

            // Kafa dönüşü kontrolü
            val headEulerAngleY = face.headEulerAngleY // sağa/sola dönme
            if (abs(headEulerAngleY) > 20 && currentTime - lastHeadTurnTime > 1500) {
                lastHeadTurnTime = currentTime
                headTurnDetected = true
            }

            // Kafa eğme kontrolü
            val headEulerAngleX = face.headEulerAngleX // aşağı/yukarı eğme
            if (abs(headEulerAngleX) > 15 && currentTime - lastHeadNodTime > 1500) {
                lastHeadNodTime = currentTime
                headNodDetected = true
            }

            // Yüz hareketi kontrolü
            if (lastFacePosition.width() > 0 && movementDetectionStartTime + 1000 < currentTime) {
                val dx = boundingBox.centerX() - lastFacePosition.centerX()
                val dy = boundingBox.centerY() - lastFacePosition.centerY()
                val movement = Math.sqrt((dx * dx + dy * dy).toDouble())

                if (movement > 20) {
                    faceMovementDetected = true
                }
            }
            lastFacePosition = Rect(boundingBox)

            // Yüz boyutu değişikliği kontrolü
            if (lastFaceSize > 0) {
                val sizeChange = abs(currentFaceSize - lastFaceSize) / lastFaceSize.toFloat()
                if (sizeChange > 0.2f) {
                    faceSizeChangeDetected = true
                }
            }
            lastFaceSize = currentFaceSize

            // Canlılık skoru hesapla (0-7 arası)
            var score = 0
            if (blinkDetected) score++
            if (smileDetected) score++
            if (headTurnDetected) score++
            if (headNodDetected) score++
            if (faceMovementDetected) score++
            if (faceSizeChangeDetected) score++
            if (expressionChangeDetected) score++

            // Belirli bir eşik değerinden büyükse canlı olarak kabul et
            val isLive = score >= 3

            return LivenessResult(isLive, score)
        }
    }

    data class LivenessResult(val isLive: Boolean, val score: Int)

    fun release() {
        faceNetInterpreter?.close()
        executorService.shutdown()
    }
}