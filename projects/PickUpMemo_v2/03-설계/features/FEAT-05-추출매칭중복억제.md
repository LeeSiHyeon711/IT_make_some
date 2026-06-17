# FEAT-05 — 업체명 추출 · 매칭 · 중복억제 로직

- 매칭 이슈: #5
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
접근성 화면 텍스트에서 업체명 후보를 추출하고, 등록 메모와 강한 매칭(상호명+지점명 둘 다 포함)을 판정하며, 동일 메모 ID에 대해 30초 중복 표시를 억제하는 **순수 로직**을 만든다. UI/오버레이/서비스와 분리해 단위 테스트 가능하게 한다. (F-02, F-03, F-04 / AC-03 / 7-1 매칭 케이스)

## 2. 범위
### 구현할 것
- `matcher/StoreExtractor.kt`: 조립된 화면 텍스트 → 업체명 후보(detectedStoreText)
- `matcher/MemoMatcher.kt`: detectedStoreText + 메모 목록 → 매칭 메모(첫 매칭)
- `matcher/DedupGuard.kt`: 메모 ID별 마지막 표시 시각 기반 30초 억제
### 구현하지 않을 것
- 접근성 이벤트 수신·노드 순회 → FEAT-07 (조립된 문자열을 입력으로 받음)
- 팝업 표시 → FEAT-06
- 패키지 필터링 → FEAT-07 (이 로직은 패키지를 모름)

## 3. 입력 / 출력
### 입력
- StoreExtractor: `fullText: String` (노드 트리에서 조립된 한 화면의 텍스트, 세그먼트가 `/`·`|`·`,`로 섞임)
- MemoMatcher: `detectedStoreText: String`, `memos: List<Memo>`
- DedupGuard: `memoId: Long`, 현재 시각
### 출력
- StoreExtractor: 업체명 후보 String (없으면 null)
- MemoMatcher: 매칭된 `Memo?`
- DedupGuard: 표시 허용 여부 Boolean

## 4. 동작 흐름
1. `StoreExtractor.extract(fullText)`:
   - `fullText`에 `"신규배차_카드"` 포함 OR (`"픽업지"` 포함 AND `"전달지"` 포함)이 아니면 → null.
   - `"픽업지"` 이후 ~ `"전달지"` 이전 부분 문자열 추출(`substringAfter("픽업지").substringBefore("전달지")`).
   - 구분자/토큰 정리: `id=...`·`desc=...` 토큰 제거, `/ | ,` → 공백, 연속 공백 1개로, trim.
   - 결과가 빈 문자열이면 null, 아니면 후보 반환.
2. `MemoMatcher.match(detectedStoreText, memos)`:
   - 비교 전 `detectedStoreText`와 각 memo의 `storeName`·`branchName`을 **`trim()`** 한다(앞뒤 공백으로 인한 예외적 미매칭 방지).
   - trim한 `storeName`·`branchName`이 **빈 문자열이면 그 memo는 매칭 대상에서 제외**한다(빈 문자열 `contains`가 항상 true가 되는 문제 방어).
   - memos를 순회(목록은 호출자가 전달; id 오름차순/입력순). 각 memo에 대해
     `store.isNotEmpty() && branch.isNotEmpty() && detected.contains(store) && detected.contains(branch)`이면 그 memo 반환.
   - 첫 매칭만 반환(여러 매칭 시 첫 번째). 매칭 없으면 null.
   - **유지 원칙**: contains 기반 단순 매칭 / 상호명 AND 지점명 모두 포함 시에만 성공 / 상호명만·지점명만 일치 시 실패. 정규화·유사도·NLP·AI는 추가하지 않는다.
3. `DedupGuard.shouldShow(memoId, now)`:
   - `lastShownAt[memoId]`가 없거나 `now - lastShownAt[memoId] >= 30_000`이면 true + `lastShownAt[memoId] = now` 갱신.
   - 아니면 false(갱신 안 함).

## 5. 수정 예상 파일 (전부 신규)
- `.../matcher/StoreExtractor.kt`
- `.../matcher/MemoMatcher.kt`
- `.../matcher/DedupGuard.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
object StoreExtractor {
    private const val KEY_CARD = "신규배차_카드"
    private const val KEY_PICKUP = "픽업지"
    private const val KEY_DEST = "전달지"
    fun extract(fullText: String?): String? {
        if (fullText.isNullOrBlank()) return null
        val hasCard = fullText.contains(KEY_CARD)
        val hasPickupDest = fullText.contains(KEY_PICKUP) && fullText.contains(KEY_DEST)
        if (!hasCard && !hasPickupDest) return null
        if (!fullText.contains(KEY_PICKUP) || !fullText.contains(KEY_DEST)) return null
        val between = fullText.substringAfter(KEY_PICKUP).substringBefore(KEY_DEST)
        val cleaned = between
            .replace(Regex("id=\\S+"), " ")
            .replace(Regex("desc=[^/|,]*"), " ")
            .replace('/', ' ').replace('|', ' ').replace(',', ' ')
            .replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { null }
    }
}

object MemoMatcher {
    fun match(detectedStoreText: String, memos: List<Memo>): Memo? {
        val detected = detectedStoreText.trim()
        return memos.firstOrNull { memo ->
            val store = memo.storeName.trim()
            val branch = memo.branchName.trim()
            store.isNotEmpty() &&
            branch.isNotEmpty() &&
            detected.contains(store) &&
            detected.contains(branch)
        }
    }
}

object DedupGuard {
    const val WINDOW_MS = 30_000L
    private val lastShownAt = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    fun shouldShow(memoId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownAt[memoId]
        if (last != null && now - last < WINDOW_MS) return false
        lastShownAt[memoId] = now
        return true
    }
    fun reset() = lastShownAt.clear()  // 테스트/디버그용
}
```

### 검증해야 할 매칭 케이스 (PRD 7-1)
| 등록(상호/지점) | 감지 | 기대 |
|---|---|---|
| 푸라닭/신림점 | "푸라닭 신림점" | 매칭 |
| 푸라닭/봉천점 | "푸라닭 신림점" | 미매칭 |
| 푸라닭/신림점 | "푸라닭 봉천점" | 미매칭 |

## 7. 예외 처리
- fullText null/blank → null 반환(크래시 없음).
- 메모 storeName/branchName이 빈 값일 가능성은 FEAT-04 검증으로 차단되나, contains는 빈 문자열에 항상 true가 되므로 `MemoMatcher`가 trim 후 `isNotEmpty()` 가드로 한 번 더 방어한다(빈 값 메모는 매칭 제외).
- DedupGuard는 동시 호출 대비 ConcurrentHashMap 사용.

## 8. 완료 조건
- 빌드 성공.
- 위 3개 케이스가 명세대로 동작(매칭 1건/미매칭 2건).
- 동일 memoId 30초 내 2회 호출 시 1회만 true.

## 9. 테스트 방법
- 임시 main/단위 테스트 또는 디버그 호출로:
  - PRD 20장 "신규 배차 카드 테스트 텍스트"를 extract에 넣어 "푸라닭 신림점" 후보 확인.
  - 위 3개 매칭 케이스 결과 확인.
  - shouldShow(1L) 연속 2회 → true, false 확인.

## 10. 금지 사항
- 접근성 이벤트·노드 순회 코드 포함 금지(FEAT-07).
- 팝업/UI 호출 금지(FEAT-06).
- 패키지 필터 로직 포함 금지(FEAT-07).
- OCR/정규식 과설계 금지 — 명세된 단순 추출/contains만.
