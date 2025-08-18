package com.example.yuztanima

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tvRegisteredCount: TextView
    private lateinit var tvEmptyList: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnNewPerson: Button
    private lateinit var btnRecognize: Button
    private lateinit var btnRefresh: ImageButton
    private lateinit var personAdapter: PersonAdapter

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val personDao by lazy { database.personDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View'ları tanımla
        tvRegisteredCount = findViewById(R.id.tvRegisteredCount)
        tvEmptyList = findViewById(R.id.tvEmptyList)
        recyclerView = findViewById(R.id.recyclerView)
        btnNewPerson = findViewById(R.id.btnNewPerson)
        btnRecognize = findViewById(R.id.btnRecognize)
        btnRefresh = findViewById(R.id.btnRefresh)

        // RecyclerView ayarları
        recyclerView.layoutManager = LinearLayoutManager(this)
        personAdapter = PersonAdapter(
            onDeleteClick = { person ->
                deletePerson(person)
            }
        )
        recyclerView.adapter = personAdapter

        // Buton tıklamaları
        btnNewPerson.setOnClickListener {
            checkPermissionsAndOpenRegister()
        }

        btnRecognize.setOnClickListener {
            checkPermissionsAndOpenRecognize()
        }

        btnRefresh.setOnClickListener {
            loadPersons()
        }

        // İzinleri kontrol et
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadPersons()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions.toTypedArray(),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    private fun checkPermissionsAndOpenRegister() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            openRegisterActivity()
        } else {
            checkPermissions()
        }
    }

    private fun checkPermissionsAndOpenRecognize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            openRecognizeActivity()
        } else {
            checkPermissions()
        }
    }

    private fun openRegisterActivity() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun openRecognizeActivity() {
        val intent = Intent(this, RecognizeActivity::class.java)
        startActivity(intent)
    }

    private fun loadPersons() {
        lifecycleScope.launch {
            try {
                // Flow'u collect ederek verileri alın
                personDao.getAllPersons().collect { persons ->
                    personAdapter.submitList(persons)

                    // Kayıtlı kişi sayısını güncelle
                    tvRegisteredCount.text = "Kayıtlı kişi sayısı: ${persons.size}"

                    // Boş liste kontrolü
                    if (persons.isEmpty()) {
                        tvEmptyList.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvEmptyList.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deletePerson(person: Person) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    personDao.deletePerson(person)

                    // Kayıtlı yüz resimlerini de sil
                    person.faceImages.values.forEach { path ->
                        val file = java.io.File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }

                // Listeyi otomatik olarak güncelleyecektir (Flow sayesinde)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
    }
}