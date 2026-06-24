> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-16 — AddressExtractor (픽업지/전달지 추출, 소스 A/B)

- 매칭 이슈: #16
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
배민커넥트 화면 텍스트에서 픽업지·전달지 주소 문자열을 추출하는 신규 전담 object를 만든다. 경로 조회(RouteService)의 입력이 된다. **기존 StoreExtractor는 메모 매칭 용도 그대로 두고 절대 수정하지 않는다**(PRD 4-5). AddressExtractor는 독립 동작한다.

## 2. 범위
### 구현할 것
- `matcher/AddressExtractor.kt` — 소스 A(토큰열) 우선, 소스 B(desc 콤마분할) fallback.
- `AddressPair` 데이터 클래스(pickup, dest) + key 헬퍼.

### 구현하지 않을 것
- 좌표 변환/네트워크 → FEAT-17/18.
- StoreExtractor/MemoMatcher 수정(절대 금지).
- 결선(서비스 호출) → FEAT-21.

## 3. 입력 / 출력
### 입력
- `segments: List<String>` — `collectNode`가 모은 토큰열(순서 보존). 세그먼트 형식: `"텍스트 | desc=설명 | id=리소스아이디"`.
- `fullText: String` — `segments.joinToString(" / ")`.
### 출력
- `AddressPair(pickup, dest)` 또는 `null`(둘 중 하나라도 못 얻으면 null).

## 4. 동작 흐름
1. **소스 A (우선)**: `segments`에서 `"픽업지"`를 포함한 세그먼트의 인덱스를 찾고, 그 **다음 세그먼트**의 텍스트를 정제해 pickup으로 사용. `"전달지"`도 동일하게 다음 세그먼트 → dest.
2. 소스 A로 pickup·dest **둘 다** 비어있지 않으면 그대로 반환.
3. **소스 B (fallback)**: 소스 A에서 하나라도 비면, `"신규배차"` 또는 콤마(`,`)를 포함한 desc 세그먼트(한 줄형)를 찾아 콤마로 분할한 토큰 배열에서 `"픽업지"` 다음 토큰 / `"전달지"` 다음 토큰을 추출.
4. 최종적으로 pickup·dest 둘 다 non-blank면 `AddressPair` 반환, 아니면 `null`.

세그먼트 정제(`cleanSegment`): `"텍스트 | desc=.. | id=.."` 형태에서 `id=...` 토큰 제거, 첫 텍스트 조각(파이프 `|` 앞) 우선 사용, 없으면 `desc=` 뒤 값 사용. 앞뒤 공백 trim.

## 5. 수정 예상 파일
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/matcher/AddressExtractor.kt`
- (참고만, 수정 금지): `matcher/StoreExtractor.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
package com.itmakesome.pickupmemo2.matcher

data class AddressPair(val pickup: String, val dest: String) {
    /** 메모없음 케이스 dedup 키(FEAT-20). */
    fun key(): String = "$pickup|$dest"
}

object AddressExtractor {
    private const val KEY_PICKUP = "픽업지"
    private const val KEY_DEST = "전달지"

    fun extract(segments: List<String>, fullText: String): AddressPair? {
        // 1) 소스 A
        var pickup = sourceA(segments, KEY_PICKUP)
        var dest = sourceA(segments, KEY_DEST)
        // 2) 소스 B fallback (둘 중 하나라도 비면 desc 한 줄 분할 시도)
        if (pickup.isNullOrBlank() || dest.isNullOrBlank()) {
            val b = sourceB(segments, fullText)
            if (pickup.isNullOrBlank()) pickup = b?.first
            if (dest.isNullOrBlank()) dest = b?.second
        }
        if (pickup.isNullOrBlank() || dest.isNullOrBlank()) return null
        return AddressPair(pickup!!.trim(), dest!!.trim())
    }

    // "픽업지"/"전달지" 세그먼트 다음 세그먼트의 정제 텍스트
    private fun sourceA(segments: List<String>, key: String): String? {
        val idx = segments.indexOfFirst { it.contains(key) }
        if (idx < 0 || idx + 1 >= segments.size) return null
        return cleanSegment(segments[idx + 1]).ifBlank { null }
    }

    // 콤마 포함 desc 한 줄을 분할해 "픽업지"/"전달지" 다음 값을 뽑음 → Pair(pickup, dest)
    private fun sourceB(segments: List<String>, fullText: String): Pair<String?, String?>? {
        val line = segments.firstOrNull {
            (it.contains("신규배차") || it.contains(",")) &&
            it.contains(KEY_PICKUP) && it.contains(KEY_DEST)
        } ?: fullText
        val tokens = line.replace("desc=", "").replace(Regex("id=\\S+"), "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        val p = nextAfter(tokens, KEY_PICKUP)
        val d = nextAfter(tokens, KEY_DEST)
        if (p == null && d == null) return null
        return p to d
    }

    private fun nextAfter(tokens: List<String>, key: String): String? {
        val i = tokens.indexOfFirst { it.contains(key) }
        // "픽업지 푸라닭" 같이 같은 토큰에 붙어있으면 key 제거 후 사용
        if (i >= 0) {
            val same = tokens[i].replace(key, "").trim()
            if (same.isNotBlank()) return same
            return tokens.getOrNull(i + 1)?.trim()?.ifBlank { null }
        }
        return null
    }

    private fun cleanSegment(seg: String): String {
        val noId = seg.replace(Regex("id=\\S+"), "").trim()
        val head = noId.substringBefore("|").trim()
        if (head.isNotBlank()) return head
        return noId.substringAfter("desc=", "").trim()
    }
}
```

## 7. 예외 처리
- 인덱스 범위 초과/빈 세그먼트 → null 반환(throw 금지).
- 픽업/전달 키가 없으면 null(트리거 안 됨).
- 같은 토큰에 키와 값이 붙은 경우(`"픽업지 푸라닭"`)·다음 토큰에 값이 있는 경우 모두 처리.

## 8. 완료 조건
- 빌드 성공.
- 실제 baemin_logs 샘플 형태에서 pickup=`푸라닭 신림점`, dest=`서울 관악구 남부순환로161나길 13 **** (신림동)` 류가 추출됨.
- 픽업/전달 중 하나만 있으면 null 반환.
- StoreExtractor 파일 미변경.

## 9. 테스트 방법
1. 단위 확인: `segments = ["신규배차_카드 ...", "픽업지", "푸라닭 신림점", "전달지", "서울 관악구 남부순환로161나길 13 **** (신림동)"]` → AddressPair(pickup=`푸라닭 신림점`, dest=`서울...신림동)`).
2. 소스 A 실패 형태(한 줄 desc) → 소스 B로 추출되는지 확인.
3. "픽업지"만 있고 "전달지" 없는 입력 → null.
4. 회귀: StoreExtractor.extract 동작 불변(FEAT-05 그대로).

## 10. 금지 사항
- StoreExtractor/MemoMatcher 수정·확장 금지(PRD 4-5).
- 네트워크/좌표 변환 선구현 금지.
- 추출 외 결선(서비스 호출) 금지(FEAT-21).
- 불필요한 라이브러리 추가 금지(표준 라이브러리만).
