plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.itmakesome.pickupmemo"
    // 설계서 2-1: compileSdk / targetSdk = 34, minSdk = 26
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itmakesome.pickupmemo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // MVP 검증 도구 — 디버그 APK 중심. release는 기본 비축소.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 설계서 2-1: JDK 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // XML View 레이아웃 사용(Compose 미사용). ViewBinding으로 안전한 뷰 접근.
        viewBinding = true
    }
}

dependencies {
    // 설계서 2-1 표준 androidx + Material 3 + Coroutines (과한 스택 금지)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // F-1(#11): 로그 조회 목록. Material 1.12.0이 transitive로 포함하지만 명시적으로 고정한다.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
