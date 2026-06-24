> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-17 — KakaoRouteProvider (카카오 Local geocode + 모빌리티 경로)

- 매칭 이슈: #17
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
`RouteProvider`(FEAT-15)의 카카오 구현체를 만든다. 카카오 Local API로 주소/키워드→좌표 변환하고, 카카오모빌리티 길찾기로 두 좌표 사이 거리/소요시간을 얻는다. OkHttp 동기 호출 + 내장 `org.json` 파싱. 모든 실패는 null 반환(크래시 없음). (PRD 4-6, 4-7)

## 2. 범위
### 구현할 것
- `route/KakaoRouteProvider.kt` — `geocode(query, type)` + `getRoute(origin, dest)`.
- OkHttp 클라이언트(싱글톤) + `Authorization: KakaoAK` 헤더.
- 엔드포인트 상수를 companion에 모은다(위험 격리, 설계서 7-3).

### 구현하지 않을 것
- 4단계 dest fallback 오케스트레이션 → FEAT-18(RouteService)이 담당. 여기선 단일 geocode 1회만.
- 캐싱/timeout → FEAT-18.
- 키 주입 설정 → FEAT-14(이미 BuildConfig 존재).

## 3. 입력 / 출력
### 입력
- 생성자 `restApiKey: String`(BuildConfig.KAKAO_REST_API_KEY, 빈 문자열 가능).
- `geocode(query, type)`, `getRoute(origin, dest)`.
### 출력
- `GeoPoint?` / `RouteInfo?`. 실패 시 null.

## 4. 동작 흐름
### geocode(query, ADDRESS)
1. `GET https://dapi.kakao.com/v2/local/search/address.json?query={url-encoded query}`.
2. 헤더 `Authorization: KakaoAK $restApiKey`.
3. 응답 JSON `documents` 배열 첫 요소의 `x`(경도)·`y`(위도) → `GeoPoint(lng=x, lat=y)`. 배열 비면 null.

### geocode(query, KEYWORD)
1. `GET https://dapi.kakao.com/v2/local/search/keyword.json?query=...` (헤더 동일).
2. 동일하게 `documents[0].x/y` 파싱.

### getRoute(origin, dest)
1. `GET {DIRECTIONS_BASE}?origin={origin.lng},{origin.lat}&destination={dest.lng},{dest.lat}` (헤더 동일).
2. 응답 `routes[0].summary.distance`(m, Int), `routes[0].summary.duration`(s, Int) 파싱.
3. `routes[0].result_code != 0`(경로 없음) 또는 배열 빈 경우 null.
4. `RouteInfo(distance, duration, RouteInfo.summaryOf(distance, duration), "kakao")` 반환.

모든 호출은 `withContext(Dispatchers.IO) { client.newCall(req).execute().use { ... } }`. 비정상 HTTP 코드/빈 바디/파싱 예외는 try-catch로 잡아 null.

## 5. 수정 예상 파일
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/route/KakaoRouteProvider.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
package com.itmakesome.pickupmemo2.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KakaoRouteProvider(private val restApiKey: String) : RouteProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    override suspend fun geocode(query: String, type: GeoQueryType): GeoPoint? =
        withContext(Dispatchers.IO) {
            if (restApiKey.isBlank() || query.isBlank()) return@withContext null
            val base = when (type) {
                GeoQueryType.ADDRESS -> LOCAL_ADDRESS
                GeoQueryType.KEYWORD -> LOCAL_KEYWORD
            }
            val url = "$base?query=${URLEncoder.encode(query, "UTF-8")}"
            val body = get(url) ?: return@withContext null
            try {
                val docs = JSONObject(body).optJSONArray("documents") ?: return@withContext null
                if (docs.length() == 0) return@withContext null
                val d = docs.getJSONObject(0)
                val lng = d.getString("x").toDouble()
                val lat = d.getString("y").toDouble()
                GeoPoint(lng, lat)
            } catch (e: Exception) { null }
        }

    override suspend fun getRoute(origin: GeoPoint, dest: GeoPoint): RouteInfo? =
        withContext(Dispatchers.IO) {
            if (restApiKey.isBlank()) return@withContext null
            val url = "$DIRECTIONS_BASE?origin=${origin.lng},${origin.lat}" +
                "&destination=${dest.lng},${dest.lat}"
            val body = get(url) ?: return@withContext null
            try {
                val routes = JSONObject(body).optJSONArray("routes") ?: return@withContext null
                if (routes.length() == 0) return@withContext null
                val r0 = routes.getJSONObject(0)
                if (r0.optInt("result_code", 0) != 0) return@withContext null
                val summary = r0.getJSONObject("summary")
                val distance = summary.getInt("distance")
                val duration = summary.getInt("duration")
                RouteInfo(distance, duration, RouteInfo.summaryOf(distance, duration), "kakao")
            } catch (e: Exception) { null }
        }

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "KakaoAK $restApiKey").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (e: Exception) { null }

    companion object {
        // ★ 위험 격리(설계서 7-3): 자전거 엔드포인트 미지원 시 이 상수만 교체.
        private const val LOCAL_ADDRESS = "https://dapi.kakao.com/v2/local/search/address.json"
        private const val LOCAL_KEYWORD = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val DIRECTIONS_BASE = "https://apis-navi.kakaomobility.com/v1/directions"
    }
}
```

## 7. 예외 처리
- 키 빈 문자열/쿼리 빈 문자열 → null(즉시).
- 네트워크 예외·타임아웃(OkHttp 3초)·비정상 HTTP·빈 documents·파싱 실패 → 전부 null(throw 없음).
- 카카오 자전거 전용 엔드포인트 미지원(404/403) 가능성: 설계서 7-3 위험노트 — `DIRECTIONS_BASE` 상수만 자동차 길찾기로 교체하면 distance/duration 동일하게 사용 가능. **이번 FEAT는 PRD 명시 엔드포인트로 구현**하고, 상수를 한 곳에 모아 교체 가능하게만 둔다.

## 8. 완료 조건
- 빌드 성공.
- 유효 키 + 정상 주소로 `geocode` 호출 시 GeoPoint 반환(수동/로그 확인).
- 두 좌표로 `getRoute` 호출 시 RouteInfo 반환.
- 키 없음/네트워크 차단 시 null 반환(크래시 없음).

## 9. 테스트 방법
1. `local.properties`에 유효 카카오 REST 키 설정 후 빌드.
2. 임시 로그/테스트 진입점(FEAT-22가 제공 예정이나, 단독 검증 시 로그)으로 `geocode("서울 관악구 남부순환로161나길 13", ADDRESS)` → 좌표 출력.
3. `geocode("푸라닭 신림점", KEYWORD)` → 좌표 출력.
4. `getRoute(origin, dest)` → distance/duration 출력.
5. 키를 빈 값으로 두고 호출 → null, 크래시 없음.

## 10. 금지 사항
- 4단계 dest fallback 로직 여기 작성 금지(FEAT-18 책임). 여기선 단일 geocode만.
- 캐싱/timeout 추가 금지(FEAT-18).
- 엔드포인트를 코드 곳곳에 흩뿌리기 금지(companion 상수 1곳).
- OkHttp 외 라이브러리/JSON 직렬화 라이브러리 추가 금지.
