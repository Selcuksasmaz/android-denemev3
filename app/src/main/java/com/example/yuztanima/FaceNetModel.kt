package com.example.yuztanima

import android.content.Context
import android.graphics.*
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceNetModel(context: Context) {
    private val interpreter: Interpreter
    private val imgSize = 160  // FaceNet için 160x160 giriş boyutu
    private val embedDim = 512  // FaceNet için 512 boyutunda çıkış vektörü

    init {
        try {
            // TFLite modelini assets klasöründen yükle
            val model = FileUtil.loadMappedFile(context, "facenet.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Daha iyi performans için çoklu iş parçacığı
            }
            interpreter = Interpreter(model, options)
            Log.d("FaceNetModel", "Model başarıyla yüklendi!")
        } catch (e: Exception) {
            Log.e("FaceNetModel", "Model yüklenirken hata: ${e.message}")
            throw RuntimeException("FaceNet modeli yüklenemedi: ${e.message}")
        }
    }

    fun getFaceEmbedding(face: Bitmap): FloatArray {
        try {
            // Yüzü model için uygun boyuta getir
            val scaledFace = Bitmap.createScaledBitmap(face, imgSize, imgSize, false)

            // Görüntüyü TensorFlow'a uygun formata dönüştür
            val imgData = ByteBuffer.allocateDirect(imgSize * imgSize * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Bitmap'i normalize edilmiş float değerlere dönüştür
            val pixels = IntArray(imgSize * imgSize)
            scaledFace.getPixels(pixels, 0, imgSize, 0, 0, imgSize, imgSize)

            for (pixel in pixels) {
                // FaceNet için normalizasyon: (2 * (pixel - 127.5)) / 255
                imgData.putFloat(((pixel shr 16) and 0xFF) / 127.5f - 1.0f)  // R
                imgData.putFloat(((pixel shr 8) and 0xFF) / 127.5f - 1.0f)   // G
                imgData.putFloat((pixel and 0xFF) / 127.5f - 1.0f)           // B
            }

            // Çıkış buffer'ını hazırla
            val embeddings = Array(1) { FloatArray(embedDim) }

            // Model çalıştır
            interpreter.run(imgData, embeddings)

            // Embedding'i normalleştir (L2 normalizasyon)
            val embedding = embeddings[0]
            val norm = normalizeEmbedding(embedding)

            return norm
        } catch (e: Exception) {
            Log.e("FaceNetModel", "Embedding çıkarılırken hata: ${e.message}")
            throw RuntimeException("FaceNet embedding çıkarılamadı: ${e.message}")
        }
    }

    // L2 Normalizasyon
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var sum = 0.0f
        for (value in embedding) {
            sum += value * value
        }

        val norm = Math.sqrt(sum.toDouble()).toFloat()
        val normalizedEmbedding = FloatArray(embedding.size)

        for (i in embedding.indices) {
            normalizedEmbedding[i] = embedding[i] / norm
        }

        return normalizedEmbedding
    }

    // İki yüz vektörü arasındaki benzerliği hesapla (kosinüs benzerliği)
    fun compareFaces(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0.0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        // Normalize edilmiş vektörler için kosinüs benzerliği doğrudan nokta çarpımına eşittir
        return dotProduct
    }

    fun storeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(4 * embedding.size).order(ByteOrder.nativeOrder())
        for (value in embedding) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    fun loadEmbedding(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val embedding = FloatArray(bytes.size / 4)
        for (i in embedding.indices) {
            embedding[i] = buffer.getFloat()
        }
        return embedding
    }
}