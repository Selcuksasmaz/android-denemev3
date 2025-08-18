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

    private val SIMILARITY_THRESHOLD = 0.8f  // Benzerlik esigi (kosinüs benzerliği için 1'e yakın olmalı)

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
                withContext(Dispatchers.Main) {
                    if (allEmbeddings.isEmpty()) {
                        showToast("Veritabanında kayıtlı yüz bulunmuyor.")
                    }
                }
            } catch (e: Exception) {
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
            val croppedFace = faceRecognitionHelper.cropFace(bitmap, face, 20)
            val currentEmbedding = faceRecognitionHelper.extractFaceEmbedding(croppedFace)
            findBestMatch(currentEmbedding)
        }
    }

    private fun onNoFaceDetected() {
        runOnUiThread {
            tvRecognitionResult.text = "Yüz bulunamadı"
        }
    }

    private fun findBestMatch(currentEmbedding: FloatArray) {
        if (allEmbeddings.isEmpty()) return

        var bestMatch: FaceEmbedding? = null
        var highestSimilarity = -1f

        for (storedEmbedding in allEmbeddings) {
            val similarity = faceRecognitionHelper.compareFaces(storedEmbedding.embedding, currentEmbedding)
            Log.d("RecognizeActivity", "Comparing with personId: ${storedEmbedding.personId}, similarity: $similarity")
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity
                bestMatch = storedEmbedding
            }
        }

        runOnUiThread {
            if (highestSimilarity >= SIMILARITY_THRESHOLD && bestMatch != null) {
                val personName = personIdToNameMap[bestMatch!!.personId] ?: "Bilinmeyen"
                tvRecognitionResult.text = "Tanınan: $personName\nBenzerlik: ${String.format("%.2f", highestSimilarity)}"
                Log.d("RecognizeActivity", "Best match: $personName, similarity: $highestSimilarity")
            } else {
                tvRecognitionResult.text = "Kişi tanınamadı"
                Log.d("RecognizeActivity", "No match found. Highest similarity: $highestSimilarity")
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
