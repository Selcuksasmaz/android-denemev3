plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.yuztanima"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.yuztanima"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
        implementation("com.google.android.material:material:1.11.0")

         // Room
        implementation("androidx.room:room-runtime:2.6.1")
         implementation("androidx.room:room-ktx:2.6.1")
        kapt("androidx.room:room-compiler:2.6.1")

        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

         // ML Kit
         implementation("com.google.mlkit:face-detection:16.1.6")

         // CameraX
         implementation("androidx.camera:camera-camera2:1.3.1")
        implementation("androidx.camera:camera-lifecycle:1.3.1")
         implementation("androidx.camera:camera-view:1.3.1")

         // TensorFlow Lite (for FaceNet)
         implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
         // Gson (for JSON serialization)
        implementation("com.google.code.gson:gson:2.10.1")

         // Testing
         testImplementation("junit:junit:4.13.2")
         androidTestImplementation("androidx.test.ext:junit:1.1.5")
         androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    }