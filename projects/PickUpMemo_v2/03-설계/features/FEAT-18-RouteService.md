> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-18 — RouteService (5초 timeout · 캐싱 · 전달지 4단계 fallback)

- 매칭 이슈: #18
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
픽업지/전달지 문자열을 받아 좌표 변환→경로 조회까지 오케스트레이션하는 단일 진입점을 만든다. ★ 배민 배차 타이머(약 30초)를 고려해 **전체 조회를 5초 timeout**으로 제한하고, 전달지 마스킹 주소는 **4단계 geocode fallback**, 동일 배차 반복은 메모리 캐시로 처리한다. 모든 실패는 null(=`거리 정보 확인 불가`). (설계서 6·7-1)

## 2. 범위
### 구현할 것
- `route/RouteService.kt`(object) — `init(apiKey)`, `resolve(pickup, dest): RouteInfo?`.
- 5초 `withTimeoutOrNull` 적용.
- 전달지 4단계 geocode fallback(`geocodeDestWithFallback`), 픽업지 키워드 단일.
- `ConcurrentHashMap` 메모리 캐시(key=`pickup|dest`).

### 구현하지 않을 것
- HTTP/파싱 → FEAT-17(KakaoRouteProvider) 호출만.
- 팝업 표시/케이스 분기 → FEAT-19/21.
- 디스크/DB 캐시(메모리만).

## 3. 입력 / 출력
### 입력
- `init(apiKey: String)` — `BuildConfig.KAKAO_REST_API_KEY`(서비스/액티비티에서 전달).
- `resolve(pickup: String, dest: String)`.
### 출력
- `RouteInfo?` — 성공 시 거리/시간, 실패/timeout/키없음 시 null.

## 4. 동작 흐름
1. `resolve`: `key = "$pickup|$dest"`. 캐시에 있으면 즉시 반환.
2. provider 미초기화(또는 apiKey blank) → null.
3. `withTimeoutOrNull(TIMEOUT_MS)` 블록 안에서:
   - `origin = provider.geocode(pickup, KEYWORD)` ; null이면 블록 null.
   - `destPt = geocodeDestWithFallback(dest)` ; null이면 블록 null.
   - `provider.getRoute(origin, destPt)`.
4. 블록 결과가 non-null이면 캐시에 저장 후 반환. timeout/실패면 null.

### geocodeDestWithFallback(dest) — 4단계 (설계서 7-1)
1. `geocode(dest, ADDRESS)` (원문).
2. 실패 시 `geocode(maskRemoved(dest), ADDRESS)` — `****` 및 `(...)` 괄호 동명 제거.
3. 실패 시 `geocode(roadHead(dest), ADDRESS)` — 도로명 앞부분(번지/마스킹 이하 절단).
4. 실패 시 `geocode(dest, KEYWORD)` — 키워드 검색 전환.
5. 모두 실패 → null.

## 5. 수정 예상 파일
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/route/RouteService.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
package com.itmakesome.pickupmemo2.route

import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

object RouteService {
    const val TIMEOUT_MS = 5000L   // ★ 설계서 6장: 배차 30초 대비 1/6

    @Volatile private var provider: RouteProvider? = null
    private val cache = ConcurrentHashMap<String, RouteInfo>()

    /** apiKey로 KakaoRouteProvider를 주입(멱등). 빈 키여도 등록은 하되 resolve가 null 반환. */
    fun init(apiKey: String) {
        if (provider == null) {
            synchronized(this) {
                if (provider == null) provider = KakaoRouteProvider(apiKey)
            }
        }
    }

    suspend fun resolve(pickup: String, dest: String): RouteInfo? {
        val key = "$pickup|$dest"
        cache[key]?.let { return it }
        val p = provider ?: return null
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            val origin = p.geocode(pickup, GeoQueryType.KEYWORD) ?: return@withTimeoutOrNull null
            val destPt = geocodeDestWithFallback(p, dest) ?: return@withTimeoutOrNull null
            p.getRoute(origin, destPt)
        }
        if (result != null) cache[key] = result
        return result
    }

    private suspend fun geocodeDestWithFallback(p: RouteProvider, dest: String): GeoPoint? {
        p.geocode(dest, GeoQueryType.ADDRESS)?.let { return it }                       // 1
        p.geocode(maskRemoved(dest), GeoQueryType.ADDRESS)?.let { return it }          // 2
        p.geocode(roadHead(dest), GeoQueryType.ADDRESS)?.let { return it }             // 3
        p.geocode(dest, GeoQueryType.KEYWORD)?.let { return it }                       // 4
        return null
    }

    // "**** " 마스킹 + "(신림동)" 괄호 동명 제거
    private fun maskRemoved(dest: String): String =
        dest.replace("****", " ").replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("\\s+"), " ").trim()

    // 도로명 앞부분: 첫 숫자(번지) 이전까지 (예: "서울 관악구 남부순환로161나길")
    private fun roadHead(dest: String): String {
        val cleaned = maskRemoved(dest)
        val m = Regex("^(.*?[가-힣]+[로길])").find(cleaned)
        return (m?.groupValues?.get(1) ?: cleaned).trim()
    }
}
```

## 7. 예외 처리
- 5초 초과 → `withTimeoutOrNull` 반환 null → `거리 정보 확인 불가`(설계서 6장).
- provider null(init 전)·키 blank → null.
- geocode/route 내부 예외는 FEAT-17에서 이미 null로 흡수.
- 캐시 동시성: `ConcurrentHashMap` 사용.

## 8. 완료 조건
- 빌드 성공.
- 유효 키 + 정상 픽업/전달 → RouteInfo 반환, 두 번째 동일 호출은 캐시 즉시 반환.
- 마스킹 전달지(`... 13 **** (신림동)`)가 2~4단계 중 하나로 좌표 확보.
- 의도적으로 느린/차단 네트워크에서 5초 내 null 반환(무한 대기 없음).
- 키 없음 → null, 크래시 없음.

## 9. 테스트 방법
1. `RouteService.init(BuildConfig.KAKAO_REST_API_KEY)` 후 `resolve("푸라닭 신림점", "서울 관악구 남부순환로161나길 13 **** (신림동)")` → RouteInfo 확인(FEAT-22 TestActivity로 호출).
2. 같은 인자 재호출 → 캐시 hit(네트워크 미발생).
3. 비행기 모드/잘못된 키 → null, `거리 정보 확인 불가` 경로 동작.
4. (timeout) 매우 느린 망 시뮬레이션 → 5초 후 null.

## 10. 금지 사항
- HTTP/JSON 파싱 직접 작성 금지(FEAT-17 호출만).
- 팝업/케이스 분기 로직 금지(FEAT-19/21).
- timeout 값(5000) 임의 변경 금지(설계서 6장 고정값).
- 디스크/DB 캐시 추가 금지(메모리 캐시만).
