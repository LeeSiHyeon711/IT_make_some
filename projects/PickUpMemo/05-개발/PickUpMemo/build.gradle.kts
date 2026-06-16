// 루트 빌드 스크립트 — 플러그인 버전만 선언(apply는 모듈에서)
// 설계서 2-1: AGP 8.5.2 + Kotlin 1.9.24 (검증된 안정 페어)
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
