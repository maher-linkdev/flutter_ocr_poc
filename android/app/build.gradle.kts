plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.flutter_ocr_poc"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "com.example.flutter_ocr_poc"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Required for Paddle Lite native libraries
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Source sets for Paddle Lite JNI libraries
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    // Kotlin coroutines for async MethodChannel handling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Paddle Lite Java API (local JAR)
    // Place PaddlePredictor.jar in android/app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // ONNX Runtime for Arabic rec (PIR→ONNX path; use when recOnnxFileName is set)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
}

flutter {
    source = "../.."
}
