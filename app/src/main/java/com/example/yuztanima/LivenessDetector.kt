package com.example.yuztanima

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

class LivenessDetector {

    var previousFace: Face? = null
    private var movementThreshold = 5.0f
    private var blinkThreshold = 0.1f
    private var smileThreshold = 0.8f

    // Hareket tespiti
    fun detectMovement(face: Face): Boolean {
        val previousFace = this.previousFace
        this.previousFace = face

        if (previousFace == null) {
            return false
        }

        val xDiff = abs(face.boundingBox.centerX() - previousFace.boundingBox.centerX())
        val yDiff = abs(face.boundingBox.centerY() - previousFace.boundingBox.centerY())

        return xDiff > movementThreshold || yDiff > movementThreshold
    }

    // Göz kırpma tespiti
    fun detectBlink(face: Face): Boolean {
        if (face.leftEyeOpenProbability == null || face.rightEyeOpenProbability == null) {
            return false
        }

        return face.leftEyeOpenProbability!! < blinkThreshold || face.rightEyeOpenProbability!! < blinkThreshold
    }

    // Gülümseme tespiti
    fun detectSmile(face: Face): Boolean {
        if (face.smilingProbability == null) {
            return false
        }

        return face.smilingProbability!! > smileThreshold
    }

    // Yüz dokusu analizi (basit bir örnek)
    fun analyzeTexture(faceBitmap: Bitmap): Boolean {
        // Gerçek uygulamada burada daha karmaşık bir doku analizi yapılır
        // Örneğin, yerel ikili örüntüler (LBP), Fourier dönüşümü veya
        // derin öğrenme modelleri kullanılabilir

        // Basit bir varyans analizi yapalım
        val pixels = IntArray(faceBitmap.width * faceBitmap.height)
        faceBitmap.getPixels(pixels, 0, faceBitmap.width, 0, 0, faceBitmap.width, faceBitmap.height)

        var sum = 0.0
        var sumSquares = 0.0

        for (pixel in pixels) {
            val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                    0.587 * ((pixel shr 8) and 0xFF) +
                    0.114 * (pixel and 0xFF)).toInt()
            sum += gray
            sumSquares += (gray * gray)
        }

        val mean = sum / pixels.size
        val variance = (sumSquares / pixels.size) - (mean * mean)

        // Gerçek yüzlerin belirli bir varyans aralığında olması beklenir
        return variance > 300 && variance < 5000
    }

    // Canlılık kontrolü - çeşitli testleri bir araya getirir
    fun checkLiveness(face: Face, faceBitmap: Bitmap): LivenessResult {
        var score = 0
        val results = mutableMapOf<String, Boolean>()

        val movement = detectMovement(face)
        if (movement) score += 1
        results["movement"] = movement

        val blink = detectBlink(face)
        if (blink) score += 2
        results["blink"] = blink

        val smile = detectSmile(face)
        if (smile) score += 1
        results["smile"] = smile

        val texture = analyzeTexture(faceBitmap)
        if (texture) score += 3
        results["texture"] = texture

        return LivenessResult(
            score = score,
            tests = results,
            isLive = score >= 3 && texture // Texture analizi geçmek zorunda
        )
    }

    data class LivenessResult(
        val score: Int,
        val tests: Map<String, Boolean>,
        val isLive: Boolean
    )
}