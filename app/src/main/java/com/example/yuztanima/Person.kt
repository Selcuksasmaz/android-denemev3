package com.example.yuztanima

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "persons")
@TypeConverters(MapConverter::class)
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val faceImages: Map<String, String> = emptyMap()
)
