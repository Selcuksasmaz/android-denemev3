package com.example.yuztanima

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class RecognizeActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvRecognitionResult: TextView
    private lateinit var tvLiveness: TextView
    private lateinit var playButton: ImageView

    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var recognitionActive = false

    private val faceRecognitionHelper by lazy { FaceRecognitionHelper(this) }
    private val personDao by lazy { AppDatabase.getDatabase(this).personDao() }
    private lateinit var faceAnalyzer: FaceAnalyzer

    // Veritabanindan gelen verileri hafizada tutmak icin
    private var allEmbeddings = listOf<FaceEmbedding>()
    private var personIdToNameMap = mapOf<Long, String>()

    private val SIMILARITY_THRESHOLD = 0.5f  // Geçici olarak düşürüldü - debug için (kosinüs benzerliği için 1'e yakın olmalı)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognize)

        initViews()
        setupListeners()
        loadDataFromDatabase()

        faceAnalyzer = FaceAnalyzer(::onFaceDetected, ::onNoFaceDetected)
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        tvRecognitionResult = findViewById(R.id.tvRecognitionResults) // ID'nin doğru olduğundan emin olun
        tvLiveness = findViewById(R.id.tvLiveness)
        playButton = findViewById(R.id.playButton)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupListeners() {
        playButton.setOnClickListener {
            recognitionActive = !recognitionActive
            if (recognitionActive) {
                startCamera()
                playButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                stopCamera()
                playButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun loadDataFromDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                allEmbeddings = personDao.getAllEmbeddings()
                val persons = personDao.getAllPersons().first() // Flow'dan tek seferlik veri almak için
                personIdToNameMap = persons.associate { it.id to it.name }
                
                // DEBUG: Veritabanından yüklenen veri detayları
                Log.d("RecognizeActivity", "=== VERİTABANI YÜKLEME DEBUG ===")
                Log.d("RecognizeActivity", "Toplam embedding sayısı: ${allEmbeddings.size}")
                Log.d("RecognizeActivity", "Toplam kişi sayısı: ${persons.size}")
                
                if (allEmbeddings.isNotEmpty()) {
                    // İlk embedding'in boyutunu kontrol et
                    val firstEmbeddingSize = allEmbeddings[0].embedding.size
                    Log.d("RecognizeActivity", "İlk embedding boyutu: $firstEmbeddingSize")
                    
                    // Tüm embedding boyutlarını kontrol et
                    val embeddingSizes = allEmbeddings.map { it.embedding.size }.distinct()
                    Log.d("RecognizeActivity", "Farklı embedding boyutları: $embeddingSizes")
                    
                    // Her kişi için embedding sayısını göster
                    allEmbeddings.groupBy { it.personId }.forEach { (personId, embeddings) ->
                        val personName = personIdToNameMap[personId] ?: "Bilinmeyen"
                        Log.d("RecognizeActivity", "Kişi: $personName (ID: $personId) - ${embeddings.size} adet embedding")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (allEmbeddings.isEmpty()) {
                        showToast("Veritabanında kayıtlı yüz bulunmuyor.")
                        Log.w("RecognizeActivity", "Veritabanında hiç embedding bulunamadı!")
                    } else {
                        Log.i("RecognizeActivity", "Veritabanı başarıyla yüklendi: ${allEmbeddings.size} embedding, ${persons.size} kişi")
                    }
                }
            } catch (e: Exception) {
                Log.e("RecognizeActivity", "Veritabanı yükleme hatası: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showToast("Veritabanı okunurken hata: ${e.message}")
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor!!, faceAnalyzer) }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                showToast("Kamera başlatılamadı.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    private fun onFaceDetected(face: com.google.mlkit.vision.face.Face, bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                Log.d("RecognizeActivity", "=== YÜZ TESPIT EDİLDİ DEBUG ===")
                Log.d("RecognizeActivity", "Yüz boundingBox: ${face.boundingBox}")
                
                val croppedFace = faceRecognitionHelper.cropFace(bitmap, face, 20)
                Log.d("RecognizeActivity", "Kesilmiş yüz boyutu: ${croppedFace.width}x${croppedFace.height}")
                
                val currentEmbedding = faceRecognitionHelper.extractFaceEmbedding(croppedFace)
                Log.d("RecognizeActivity", "Çıkarılan embedding boyutu: ${currentEmbedding.size}")
                Log.d("RecognizeActivity", "Embedding örnek değerler (ilk 5): ${currentEmbedding.take(5).joinToString()}")
                
                // Embedding'in normalize edilip edilmediğini kontrol et
                val embeddingNorm = sqrt(currentEmbedding.sumOf { (it * it).toDouble() }).toFloat()
                Log.d("RecognizeActivity", "Embedding norm değeri: $embeddingNorm (1.0'a yakın olmalı)")
                
                findBestMatch(currentEmbedding)
            } catch (e: Exception) {
                Log.e("RecognizeActivity", "onFaceDetected hatası: ${e.message}", e)
                runOnUiThread {
                    tvRecognitionResult.text = "Yüz işleme hatası: ${e.message}"
                }
            }
        }
    }

    private fun onNoFaceDetected() {
        runOnUiThread {
            tvRecognitionResult.text = "Yüz bulunamadı"
        }
    }

    private fun findBestMatch(currentEmbedding: FloatArray) {
        Log.d("RecognizeActivity", "=== EN İYİ EŞLEŞME ARAMA DEBUG ===")
        
        if (allEmbeddings.isEmpty()) {
            Log.w("RecognizeActivity", "Veritabanında hiç embedding yok!")
            runOnUiThread {
                tvRecognitionResult.text = "Veritabanında kayıtlı yüz bulunmuyor"
            }
            return
        }

        Log.d("RecognizeActivity", "Toplam ${allEmbeddings.size} adet embedding ile karşılaştırılacak")
        Log.d("RecognizeActivity", "Mevcut embedding boyutu: ${currentEmbedding.size}")
        Log.d("RecognizeActivity", "Benzerlik eşiği: $SIMILARITY_THRESHOLD")

        var bestMatch: FaceEmbedding? = null
        var highestSimilarity = -1f
        val allSimilarities = mutableListOf<Pair<String, Float>>()

        for ((index, storedEmbedding) in allEmbeddings.withIndex()) {
            val personName = personIdToNameMap[storedEmbedding.personId] ?: "Bilinmeyen"
            
            // Embedding boyut kontrolü
            if (storedEmbedding.embedding.size != currentEmbedding.size) {
                Log.e("RecognizeActivity", "Embedding boyut uyumsuzluğu - Kişi: $personName, Beklenen: ${currentEmbedding.size}, Bulunan: ${storedEmbedding.embedding.size}")
                continue
            }
            
            val similarity = faceRecognitionHelper.compareFaces(storedEmbedding.embedding, currentEmbedding)
            allSimilarities.add("$personName (${storedEmbedding.angle})" to similarity)
            
            Log.d("RecognizeActivity", "[$index] Kişi: $personName (ID: ${storedEmbedding.personId}, Açı: ${storedEmbedding.angle}) - Benzerlik: ${String.format("%.4f", similarity)}")
            
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity
                bestMatch = storedEmbedding
            }
        }

        // Tüm benzerlik skorlarının özetini göster
        Log.d("RecognizeActivity", "=== BENZERLİK SKORLARI ÖZETİ ===")
        allSimilarities.sortedByDescending { it.second }.forEachIndexed { index, (name, score) ->
            Log.d("RecognizeActivity", "${index + 1}. $name: ${String.format("%.4f", score)}")
        }
        
        Log.d("RecognizeActivity", "En yüksek benzerlik: ${String.format("%.4f", highestSimilarity)}")
        Log.d("RecognizeActivity", "Eşik değeri: $SIMILARITY_THRESHOLD")
        Log.d("RecognizeActivity", "Eşiği geçti mi: ${highestSimilarity >= SIMILARITY_THRESHOLD}")

        runOnUiThread {
            if (highestSimilarity >= SIMILARITY_THRESHOLD && bestMatch != null) {
                val personName = personIdToNameMap[bestMatch!!.personId] ?: "Bilinmeyen"
                tvRecognitionResult.text = "Tanınan: $personName\nBenzerlik: ${String.format("%.4f", highestSimilarity)}\nEşik: $SIMILARITY_THRESHOLD"
                Log.i("RecognizeActivity", "BAŞARILI EŞLEŞME - Kişi: $personName, Benzerlik: ${String.format("%.4f", highestSimilarity)}")
            } else {
                tvRecognitionResult.text = "Kişi tanınamadı\nEn yüksek benzerlik: ${String.format("%.4f", highestSimilarity)}\nEşik: $SIMILARITY_THRESHOLD"
                Log.w("RecognizeActivity", "EŞLEŞME BAŞARISIZ - En yüksek benzerlik: ${String.format("%.4f", highestSimilarity)}, Eşik: $SIMILARITY_THRESHOLD")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        stopCamera()
    }
}
