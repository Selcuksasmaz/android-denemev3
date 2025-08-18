package com.example.yuztanima

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RegisterActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var ivPreview: ImageView
    private lateinit var etPersonName: EditText
    private lateinit var tvFeatureType: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var rgAngle: RadioGroup
    private lateinit var btnCapture: ImageButton
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var btnNext: Button
    private lateinit var btnFinish: Button
    private lateinit var tvCameraInfo: TextView

    private var cameraExecutor: ExecutorService? = null
    private var imageCapture: ImageCapture? = null
    private var capturedBitmap: Bitmap? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val faceRecognitionHelper by lazy { FaceRecognitionHelper(this) }
    private val personDao by lazy { AppDatabase.getDatabase(this).personDao() }

    // Embeddings ve resim yollarını geçici olarak RAM'de tutmak için
    private val faceEmbeddings = mutableMapOf<String, FloatArray>()
    private val faceImages = mutableMapOf<String, String>()
    private var registeredAnglesCount = 0
    private val totalAngles = 5
    private var currentAngle = "front"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initViews()
        setupListeners()
        startCamera()
        updateProgress()
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        ivPreview = findViewById(R.id.ivPreview)
        etPersonName = findViewById(R.id.etPersonName)
        tvFeatureType = findViewById(R.id.tvFeatureType)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        rgAngle = findViewById(R.id.rgAngle)
        btnCapture = findViewById(R.id.btnCapture)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        btnNext = findViewById(R.id.btnNext)
        btnFinish = findViewById(R.id.btnFinish)
        tvCameraInfo = findViewById(R.id.tvCameraInfo)

        progressBar.max = totalAngles
        tvFeatureType.text = "FaceNet | $registeredAnglesCount/$totalAngles"

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupListeners() {
        rgAngle.setOnCheckedChangeListener { _, checkedId ->
            currentAngle = when (checkedId) {
                R.id.rbFront -> "front"
                R.id.rbLeft -> "left"
                R.id.rbRight -> "right"
                R.id.rbUp -> "up"
                R.id.rbDown -> "down"
                else -> "front"
            }
            resetCaptureState()
        }

        btnCapture.setOnClickListener { takePhoto() }
        btnClear.setOnClickListener { clearCurrentAngle() }
        btnSave.setOnClickListener { saveCurrentAngle() }
        btnNext.setOnClickListener { moveToNextAngle() }
        btnFinish.setOnClickListener { finishRegistration() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                showToast("Kamera başlatılamadı.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        capturedBitmap = previewView.bitmap
        capturedBitmap?.let {
            ivPreview.setImageBitmap(it)
            previewView.visibility = View.GONE
            ivPreview.visibility = View.VISIBLE
            btnSave.visibility = View.VISIBLE
            btnClear.visibility = View.VISIBLE
        }
    }

    private fun saveCurrentAngle() {
        val bitmap = capturedBitmap ?: return
        val name = etPersonName.text.toString().trim()
        if (name.isEmpty()) {
            showToast("Lütfen önce bir isim girin.")
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val faces = faceRecognitionHelper.detectFaces(bitmap)
            if (faces.isNotEmpty()) {
                val croppedFace = faceRecognitionHelper.cropFace(bitmap, faces[0], 20)
                val embedding = faceRecognitionHelper.extractFaceEmbedding(croppedFace)
                val imagePath = faceRecognitionHelper.saveBitmapToFile(croppedFace, "${name}_${currentAngle}.jpg")

                faceEmbeddings[currentAngle] = embedding
                faceImages[currentAngle] = imagePath

                withContext(Dispatchers.Main) {
                    updateProgress()
                    showNextAngleDialog()
                }
            } else {
                withContext(Dispatchers.Main) {
                    showToast("Yüz tespit edilemedi. Lütfen tekrar deneyin.")
                    resetCaptureState()
                }
            }
        }
    }

    private fun finishRegistration() {
        val name = etPersonName.text.toString().trim()
        if (name.isEmpty() || faceEmbeddings.isEmpty()) {
            showToast("Lütfen bir isim girin ve en az bir açıdan yüz kaydedin.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Person nesnesini oluştur ve ID'sini al
                val person = Person(name = name)
                val personId = personDao.insertPerson(person)

                // 2. Her bir embedding için FaceEmbedding nesnesi oluştur ve kaydet
                for ((angle, embedding) in faceEmbeddings) {
                    val faceEmbedding = FaceEmbedding(
                        personId = personId,
                        angle = angle,
                        embedding = embedding
                    )
                    personDao.insertFaceEmbedding(faceEmbedding)
                }

                withContext(Dispatchers.Main) {
                    showToast("Kişi başarıyla kaydedildi!")
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Kayıt sırasında bir hata oluştu: ${e.message}")
                }
            }
        }
    }

    private fun clearCurrentAngle() {
        faceImages[currentAngle]?.let { File(it).delete() }
        faceEmbeddings.remove(currentAngle)
        faceImages.remove(currentAngle)
        resetCaptureState()
        updateProgress()
    }

    private fun resetCaptureState() {
        previewView.visibility = View.VISIBLE
        ivPreview.visibility = View.GONE
        btnSave.visibility = View.GONE
        btnClear.visibility = View.GONE
        capturedBitmap = null
    }

    private fun updateProgress() {
        registeredAnglesCount = faceEmbeddings.size
        progressBar.progress = registeredAnglesCount
        tvProgress.text = "$registeredAnglesCount/$totalAngles Açı Kaydedildi"
        btnFinish.visibility = if (registeredAnglesCount > 0) View.VISIBLE else View.GONE
    }

    private fun moveToNextAngle() {
        val radioButtons = listOf(R.id.rbFront, R.id.rbLeft, R.id.rbRight, R.id.rbUp, R.id.rbDown)
        val currentSelectionId = rgAngle.checkedRadioButtonId
        val currentIndex = radioButtons.indexOf(currentSelectionId)
        val nextIndex = (currentIndex + 1) % radioButtons.size
        rgAngle.check(radioButtons[nextIndex])
    }

    private fun showNextAngleDialog() {
        if (registeredAnglesCount < totalAngles) {
            AlertDialog.Builder(this)
                .setTitle("Açı Kaydedildi")
                .setMessage("Bu açı başarıyla kaydedildi. Sonraki açıya geçmek ister misiniz?")
                .setPositiveButton("Evet") { dialog, _ ->
                    moveToNextAngle()
                    dialog.dismiss()
                }
                .setNegativeButton("Hayır") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }
}
