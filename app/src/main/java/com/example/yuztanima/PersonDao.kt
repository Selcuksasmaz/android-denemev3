package com.example.yuztanima

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert
    suspend fun insertPerson(person: Person): Long

    @Insert
    suspend fun insertFaceEmbedding(embedding: FaceEmbedding)

    @Query("SELECT * FROM persons")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM face_embeddings WHERE personId = :personId")
    suspend fun getEmbeddingsForPerson(personId: Long): List<FaceEmbedding>

    // Tanıma işlemi için tüm embeddingleri çekmek daha verimli olabilir
    @Query("SELECT * FROM face_embeddings")
    suspend fun getAllEmbeddings(): List<FaceEmbedding>

    @Delete
    suspend fun deletePerson(person: Person)
}
