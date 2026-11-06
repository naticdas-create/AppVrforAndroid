plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lunaro.vr.hand"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lunaro.vr.hand"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "task" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}

// Baixa o modelo oficial do MediaPipe (hand_landmarker.task) no build, se ainda não existir.
val modelFile = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile
val downloadModel by tasks.registering {
    outputs.file(modelFile)
    onlyIf { !modelFile.exists() }
    doLast {
        modelFile.parentFile.mkdirs()
        val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
        java.net.URL(url).openStream().use { input ->
            modelFile.outputStream().use { out -> input.copyTo(out) }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadModel) }
