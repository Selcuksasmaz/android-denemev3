package com.example.yuztanima

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(
    tableName = "face_embeddings",
    foreignKeys = [ForeignKey(
        entity = Person::class,
        parentColumns = ["id"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE
    )]
)
@TypeConverters(FloatArrayConverter::class)
data class FaceEmbedding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val angle: String,
    val embedding: FloatArray
) {
    // data class'ın otomatik oluşturduğu equals ve hashCode'u kullanmak için
    // buraya manuel bir şey eklemiyoruz. FloatArray karşılaştırması için
    // içerik bazlı (contentDeepEquals) bir karşılaştırma gerekiyorsa,
    // bu sınıfı kullanan yerlerde manuel yapılmalıdır.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEmbedding

        if (id != other.id) return false
        if (personId != other.personId) return false
        if (angle != other.angle) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + personId.hashCode()
        result = 31 * result + angle.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
