package com.example.yuztanima

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FloatArrayConverter {
    @TypeConverter
    fun fromString(value: String): FloatArray {
        val type = object : TypeToken<FloatArray>() {}.type
        return Gson().fromJson(value, type)
    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        return Gson().toJson(array)
    }
}
