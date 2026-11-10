plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fatewindow.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fatewindow.app"
        minSdk = 28            // Pleos Connect 요구사항: API 28+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pleos Vehicle SDK. 저장소 공개 버전은 2.0.3, 2.3.2 (문서 예시의 2.3.1은 없음, 2026-11-10 확인)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Pleos TextToSpeech SDK 는 포털에서 "준비 중" → Android 기본 TextToSpeech 로 대체 (PleosVehicleController.speak)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
