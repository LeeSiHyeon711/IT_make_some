> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-15 — 경로 모델 + RouteProvider 인터페이스

- 매칭 이슈: #15
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
경로 조회 기능의 순수 타입 토대를 만든다. 좌표(GeoPoint)·경로결과(RouteInfo)·제공자 추상화(RouteProvider)를 정의해, 이후 KakaoRouteProvider(FEAT-17)/RouteService(FEAT-18)/팝업(FEAT-19)이 이 타입에 의존하게 한다. (PRD 4-7, 7장 API 레이어 분리)

## 2. 범위
### 구현할 것
- 신규 패키지 `com.itmakesome.pickupmemo2.route` 생성.
- `GeoPoint.kt`, `RouteInfo.kt`, `RouteProvider.kt`(+`GeoQueryType` enum).
- `RouteInfo`의 요약 문자열 포맷 헬퍼(`약 X.Xkm · 약 N분`).

### 구현하지 않을 것
- 실제 네트워크 호출/파싱 → FEAT-17.
- 캐싱/timeout/fallback → FEAT-18.
- 팝업 표시 → FEAT-19.

## 3. 입력 / 출력
### 입력
- 없음(순수 타입 정의). FEAT-14의 인프라 위에 올라간다.
### 출력
- `route` 패키지의 3개 코틀린 파일. 다른 FEAT가 import해 사용.

## 4. 동작 흐름
1. `route/` 패키지 디렉터리 생성.
2. `GeoPoint`, `RouteInfo`, `GeoQueryType`, `RouteProvider`를 정의.
3. 빌드가 통과하는지 확인(아직 호출자 없음).

## 5. 수정 예상 파일
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/route/GeoPoint.kt`
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/route/RouteInfo.kt`
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/route/RouteProvider.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
package com.itmakesome.pickupmemo2.route

// GeoPoint.kt — 카카오 좌표(x=경도 lng, y=위도 lat)
data class GeoPoint(val lng: Double, val lat: Double)

// RouteInfo.kt
data class RouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val summaryText: String,   // "약 X.Xkm · 약 N분"
    val rawProvider: String    // 예: "kakao"
) {
    companion object {
        /** distanceMeters/durationSeconds로 표시 문자열을 만든다 (PRD 9장). */
        fun summaryOf(distanceMeters: Int, durationSeconds: Int): String {
            val km = distanceMeters / 1000.0
            val min = Math.round(durationSeconds / 60.0)
            return "약 ${String.format("%.1f", km)}km · 약 ${min}분"
        }
    }
}

// RouteProvider.kt
enum class GeoQueryType { ADDRESS, KEYWORD }

interface RouteProvider {
    /** 주소/키워드 문자열을 좌표로 변환. 실패 시 null(throw 금지). */
    suspend fun geocode(query: String, type: GeoQueryType): GeoPoint?
    /** 두 좌표 사이 경로의 거리/소요시간. 실패 시 null. */
    suspend fun getRoute(origin: GeoPoint, dest: GeoPoint): RouteInfo?
}
```
- `RouteInfo.summaryOf`는 FEAT-17이 RouteInfo 생성 시 호출한다.

## 7. 예외 처리
- 순수 타입이라 런타임 예외 없음. 인터페이스 계약상 구현체는 실패 시 `null`을 반환(예외 전파 금지)함을 주석으로 명시.

## 8. 완료 조건
- 빌드 성공.
- `route` 패키지에 3개 파일 존재, 다른 FEAT가 import 가능.
- `RouteInfo.summaryOf(2345, 1080)` 형태 호출이 `약 2.3km · 약 18분`을 만든다(수동/단위 확인).

## 9. 테스트 방법
1. 빌드 통과 확인.
2. (선택) Kotlin REPL/임시 로그로 `RouteInfo.summaryOf(2345, 1080)` → `약 2.3km · 약 18분` 확인.
3. 기존 앱 기능 회귀 없음(추가 타입만).

## 10. 금지 사항
- 네트워크/HTTP 코드 작성 금지(여기선 타입만).
- KakaoRouteProvider 구현 선작성 금지(FEAT-17).
- 인터페이스에 메서드 추가/변경 금지(설계서 4-3 시그니처 고정).
- 다른 패키지 파일 수정 금지.
