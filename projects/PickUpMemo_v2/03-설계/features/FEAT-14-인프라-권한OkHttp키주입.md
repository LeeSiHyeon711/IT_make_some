> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-14 — 인프라: INTERNET 권한 + OkHttp 의존성 + API 키 BuildConfig 주입

- 매칭 이슈: #14
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
v3 경로 조회의 토대를 깐다. 외부 API 통신을 위한 INTERNET 권한, HTTP 클라이언트(OkHttp), 그리고 카카오 REST API 키를 소스 하드코딩 없이 `local.properties → BuildConfig`로 주입하는 빌드 설정을 추가한다. (PRD AC-7, 9장)

## 2. 범위
### 구현할 것
- `AndroidManifest.xml`에 `INTERNET` 권한 1줄 추가.
- `app/build.gradle.kts`에 OkHttp 의존성 1개 추가.
- `app/build.gradle.kts`에 `buildConfig=true` + `local.properties`에서 키를 읽어 `buildConfigField`로 `BuildConfig.KAKAO_REST_API_KEY` 주입.
- `local.properties`에 키 placeholder 라인 안내(파일이 없으면 키 "" 로 빌드되어야 함).

### 구현하지 않을 것
- 실제 네트워크 호출 코드 → FEAT-17.
- RouteService/Provider 클래스 → FEAT-15/17/18.
- 키 값 자체를 커밋하지 않는다(local.properties는 git 미추적).

## 3. 입력 / 출력
### 입력
- 기존 `app/build.gradle.kts`, `AndroidManifest.xml`.
### 출력
- 빌드 시 `BuildConfig.KAKAO_REST_API_KEY` 상수가 생성됨(키 미설정 시 빈 문자열).
- INTERNET 권한 선언, OkHttp 클래스패스 사용 가능.

## 4. 동작 흐름
1. `AndroidManifest.xml`의 기존 `<uses-permission SYSTEM_ALERT_WINDOW>` 아래에 `<uses-permission android:name="android.permission.INTERNET" />` 추가.
2. `app/build.gradle.kts` 상단(또는 android 블록 위)에서 `local.properties`를 읽는다:
   ```kotlin
   import java.util.Properties
   val localProps = Properties().apply {
       val f = rootProject.file("local.properties")
       if (f.exists()) f.inputStream().use { load(it) }
   }
   val kakaoRestKey = localProps.getProperty("KAKAO_REST_API_KEY") ?: ""
   ```
3. `android { buildFeatures { ... } }`에 `buildConfig = true` 추가(기존 `viewBinding = true` 유지).
4. `android { defaultConfig { ... } }`에 `buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestKey\"")` 추가.
5. `dependencies`에 `implementation("com.squareup.okhttp3:okhttp:4.12.0")` 추가.
6. `local.properties`에 주석 + placeholder 한 줄 추가(키 없는 사람도 빌드되게): 예 `# KAKAO_REST_API_KEY=발급키` (실제 키는 사용자가 채움).

## 5. 수정 예상 파일
- 수정: `app/src/main/AndroidManifest.xml`
- 수정: `app/build.gradle.kts`
- 수정: `local.properties` (키 placeholder 주석. git에 커밋되지 않는 파일)

## 6. 데이터 구조 / 함수 / 클래스
- `BuildConfig.KAKAO_REST_API_KEY: String` (생성됨, 미설정 시 `""`).
- gradle: `Properties` 로딩 + `buildConfigField`. 새 클래스/함수 없음.

## 7. 예외 처리
- `local.properties` 부재 또는 키 라인 없음 → `kakaoRestKey = ""` → 빌드 성공, 런타임에 RouteService가 fallback(FEAT-18). 빌드/실행이 깨지지 않아야 한다.
- 따옴표 이스케이프: `buildConfigField`의 값은 반드시 `"\"$kakaoRestKey\""` 형태로 감싼다(문자열 리터럴).

## 8. 완료 조건
- 빌드 성공(JDK 21, 디버그).
- 생성된 `BuildConfig`에 `KAKAO_REST_API_KEY` 필드가 존재.
- `local.properties`에 키가 없어도 빌드 성공(값 "").
- 매니페스트에 INTERNET 권한 선언 확인.
- 기존 의존성/viewBinding/Activity/Service 선언 불변.

## 9. 테스트 방법
1. `local.properties`에 키 라인 없이 `./gradlew :app:assembleDebug` (JDK 21) → 빌드 성공.
2. 생성물 `app/build/generated/.../BuildConfig.java`에 `KAKAO_REST_API_KEY = ""` 확인.
3. `local.properties`에 `KAKAO_REST_API_KEY=테스트값` 추가 후 재빌드 → 해당 값이 주입되는지 확인.
4. 앱 설치·실행 → 기존 메모 목록/팝업 정상(회귀 없음).

## 10. 금지 사항
- API 키를 소스 코드/`build.gradle.kts`/git에 하드코딩 금지(반드시 local.properties 경유).
- OkHttp 외 HTTP/JSON 라이브러리(Retrofit·Moshi·Gson·Ktor 등) 추가 금지.
- 다른 FEAT(네트워크 호출·Provider) 선구현 금지.
- 기존 build.gradle.kts의 Room/KSP/coroutines 의존성·compileOptions 변경 금지.
