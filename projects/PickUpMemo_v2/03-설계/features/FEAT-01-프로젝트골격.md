# FEAT-01 — 프로젝트 골격 + 빌드/매니페스트

- 매칭 이슈: #1
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
PickUpMemo_v2 Android 프로젝트의 빌드 가능한 골격을 만든다. v1의 검증된 Gradle/매니페스트/접근성 config를 재사용하되, 패키지명·SDK·Room(KSP) 의존성·오버레이 권한을 v2 기준으로 맞춘다. 이후 모든 FEAT가 이 골격 위에서 컴파일된다.

## 2. 범위
### 구현할 것
- Gradle 프로젝트 생성: `projects/PickUpMemo_v2/05-개발/PickUpMemo_v2/`
- 루트 `settings.gradle.kts`, 루트 `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`(gradle-8.7-bin), gradle wrapper 스크립트(`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`)
- `app/build.gradle.kts` (namespace/applicationId `com.itmakesome.pickupmemo2`, minSdk 26 / targetSdk 34 / compileSdk 34, Room+KSP, ViewBinding)
- `app/src/main/AndroidManifest.xml` (SYSTEM_ALERT_WINDOW 권한, MainActivity(LAUNCHER)/MemoListActivity/MemoEditActivity 등록, PickupAccessibilityService 등록)
- `app/src/main/res/xml/accessibility_service_config.xml` (v1 미러, packageNames를 `com.woowahan.bros`로 제한)
- `util/Packages.kt` (TARGET_PACKAGE, EXCLUDED_PACKAGES)
- **스텁**: `service/PickupAccessibilityService.kt`(빈 onAccessibilityEvent/onInterrupt), `MainActivity.kt`(LAUNCHER, 제목 텍스트만), `ui/MemoListActivity.kt`·`ui/MemoEditActivity.kt`(빈 AppCompatActivity + 최소 레이아웃 set) — 빌드 통과 및 매니페스트 참조 해소용
  - **`MemoEditActivity` 스텁에는 `companion object { const val EXTRA_MEMO_ID = "memoId" }` 를 미리 포함**한다. FEAT-03이 목록 항목 탭 시 `MemoEditActivity.EXTRA_MEMO_ID`를 참조하므로, FEAT-03 단독 구현 시점에서 컴파일 오류가 나지 않도록 골격에서 상수를 확정해 둔다. FEAT-04는 이 상수를 재정의하지 않고 그대로 사용한다.
- `res/values/strings.xml`(app_name="PickUpMemo", accessibility_service_desc 등 최소), `colors.xml`, `themes.xml`(Theme.PickUpMemo, Material), 최소 레이아웃 `activity_main.xml`(제목 TextView 1개)

### 구현하지 않을 것
- 메모 저장(Room Entity/Dao 본문) → FEAT-02
- 메모 목록/편집 UI 본문 → FEAT-03/04
- 추출·매칭·팝업·서비스 본문 → FEAT-05/06/07
- 권한 판정/안내 → FEAT-08
- v1의 알림 리스너·로그 저장·내보내기·테스트 알림 기능은 **이식 금지**

## 3. 입력 / 출력
### 입력
- v1 코드 참고 경로: `projects/PickUpMemo/05-개발/PickUpMemo/` (settings/build/wrapper/accessibility_service_config/AndroidManifest)
### 출력
- 빌드 가능한 Android 프로젝트 골격(`./gradlew :app:assembleDebug` 성공)

## 4. 동작 흐름
1. v1 루트 Gradle 파일을 복사해 `rootProject.name = "PickUpMemo_v2"`로 변경.
2. `app/build.gradle.kts`에 KSP 플러그인·Room 의존성·v2 applicationId 반영.
3. 매니페스트에 오버레이 권한 + 3 Activity + 접근성 서비스 등록(스텁 클래스 참조).
4. 스텁 클래스/리소스로 컴파일이 통과하도록 최소 구현.

## 5. 수정 예상 파일 (전부 신규 생성)
- `05-개발/PickUpMemo_v2/settings.gradle.kts`
- `05-개발/PickUpMemo_v2/build.gradle.kts`
- `05-개발/PickUpMemo_v2/gradle.properties`
- `05-개발/PickUpMemo_v2/gradle/wrapper/gradle-wrapper.properties`
- `05-개발/PickUpMemo_v2/gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- `05-개발/PickUpMemo_v2/app/build.gradle.kts`
- `05-개발/PickUpMemo_v2/app/src/main/AndroidManifest.xml`
- `05-개발/PickUpMemo_v2/app/src/main/res/xml/accessibility_service_config.xml`
- `.../java/com/itmakesome/pickupmemo2/util/Packages.kt`
- `.../service/PickupAccessibilityService.kt` (스텁)
- `.../MainActivity.kt` (스텁)
- `.../ui/MemoListActivity.kt`, `.../ui/MemoEditActivity.kt` (스텁)
- `.../res/values/strings.xml`, `colors.xml`, `themes.xml`
- `.../res/layout/activity_main.xml`

## 6. 데이터 구조 / 함수 / 클래스
**루트 `build.gradle.kts`** (KSP 추가):
```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
```
**`settings.gradle.kts`**: v1 그대로 + `rootProject.name = "PickUpMemo_v2"`. pluginManagement repositories에 google()/mavenCentral()/gradlePluginPortal() 유지(KSP는 gradlePluginPortal/google에서 해소).

**`app/build.gradle.kts`** 핵심:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.itmakesome.pickupmemo2"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.itmakesome.pickupmemo2"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
```
**`util/Packages.kt`**:
```kotlin
package com.itmakesome.pickupmemo2.util
object Packages {
    const val TARGET_PACKAGE = "com.woowahan.bros"
    val EXCLUDED_PACKAGES = setOf(
        "com.android.systemui", "com.lge.launcher3", "com.lge.signboard",
        "net.daum.android.map", "com.kakao.talk", "android",
        "com.itmakesome.pickupmemo2"
    )
}
```
**`accessibility_service_config.xml`**: v1 미러 + `android:packageNames="com.woowahan.bros"` 추가(대상 한정으로 노이즈/부하 감소). 나머지 flags(`flagReportViewIds|flagIncludeNotImportantViews|flagRetrieveInteractiveWindows`), `canRetrieveWindowContent="true"`, `accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged"`, `notificationTimeout="100"` 유지.

**매니페스트**: `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`. 서비스 등록은 v1 ScreenAccessibilityService 블록과 동일(name만 `.service.PickupAccessibilityService`). FileProvider/알림 리스너/POST_NOTIFICATIONS는 등록하지 않음.

**스텁 PickupAccessibilityService**:
```kotlin
class PickupAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* FEAT-07에서 구현 */ }
    override fun onInterrupt() {}
}
```

**스텁 MemoEditActivity** (EXTRA_MEMO_ID 상수 확정 — 본문은 FEAT-04):
```kotlin
class MemoEditActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MEMO_ID = "memoId"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FEAT-04에서 본문 구현
    }
}
```

## 7. 예외 처리
- KSP/Room 버전 충돌 시: Kotlin 1.9.24 ↔ KSP `1.9.24-1.0.20` 정확히 맞출 것. Room 2.6.1 고정.
- 빌드 JDK가 25면 실패 → **JDK 21**로 `./gradlew` 실행(빌드노트에 기록, FEAT-09 최종 빌드와 동일 기준).

## 8. 완료 조건
- `./gradlew :app:assembleDebug`가 JDK 21 환경에서 성공한다.
- 매니페스트에 오버레이 권한·3 Activity·접근성 서비스가 등록되어 있다.
- `Packages.kt`, 접근성 config가 v2 기준으로 존재한다.

## 9. 테스트 방법
1. `cd projects/PickUpMemo_v2/05-개발/PickUpMemo_v2`
2. `JAVA_HOME=<JDK21경로> ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL 확인.
3. `app/build/outputs/apk/debug/app-debug.apk` 생성 확인.

## 10. 금지 사항
- v1의 알림 리스너/로그 저장/내보내기/테스트 알림 코드 이식 금지.
- 메모/추출/매칭/팝업/권한 본문 선구현 금지(스텁만).
- Compose·DI·네트워크 라이브러리 추가 금지.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
