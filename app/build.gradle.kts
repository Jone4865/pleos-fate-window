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

    // === 실제 제출 시 아래 Pleos SDK 의존성으로 교체 ===
    // implementation("ai.pleos.sdk:vehicle-sdk:2.3.2")
    // implementation("ai.pleos.sdk:tts-sdk:1.x")
    // 현재는 VehicleController 인터페이스 + Stub 구현으로 독립 빌드 가능.

    testImplementation("junit:junit:4.13.2")
}
