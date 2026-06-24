> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-20 — DedupGuard 확장 (문자열 key 오버로드)

- 매칭 이슈: #20
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
v3 "메모 없음" 케이스는 memoId가 없으므로 픽업/전달 주소 조합(key 문자열) 기준으로 30초 중복 억제가 필요하다. 기존 `shouldShow(memoId: Long)`을 보존한 채 `shouldShow(key: String)` 오버로드를 추가한다. (설계서 4-8, 5장 4단계)

## 2. 범위
### 구현할 것
- `matcher/DedupGuard.kt`에 `shouldShow(key: String, now)` 오버로드 + 전용 `ConcurrentHashMap<String, Long>` 추가.
- `reset()`이 두 맵 모두 비우도록 보강.

### 구현하지 않을 것
- 기존 `shouldShow(memoId: Long)` 로직/시그니처 변경(보존만).
- 서비스에서의 호출 결선 → FEAT-21.

## 3. 입력 / 출력
### 입력
- `shouldShow(key: String, now: Long = System.currentTimeMillis())`.
### 출력
- `Boolean` — 마지막 표시로부터 30초(WINDOW_MS) 지났으면 true(+타임스탬프 갱신), 아니면 false.

## 4. 동작 흐름
1. `lastShownByKey[key]` 조회.
2. 존재 + `now - last < WINDOW_MS` → false.
3. 아니면 `lastShownByKey[key] = now` 후 true.
(기존 Long 버전과 동일 패턴, 다른 맵)

## 5. 수정 예상 파일
- 수정: `app/src/main/java/com/itmakesome/pickupmemo2/matcher/DedupGuard.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
object DedupGuard {
    const val WINDOW_MS = 30_000L
    private val lastShownAt = ConcurrentHashMap<Long, Long>()        // 기존(보존)
    private val lastShownByKey = ConcurrentHashMap<String, Long>()   // 신규

    fun shouldShow(memoId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownAt[memoId]
        if (last != null && now - last < WINDOW_MS) return false
        lastShownAt[memoId] = now
        return true
    }

    fun shouldShow(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownByKey[key]
        if (last != null && now - last < WINDOW_MS) return false
        lastShownByKey[key] = now
        return true
    }

    fun reset() { lastShownAt.clear(); lastShownByKey.clear() }
}
```

## 7. 예외 처리
- key blank여도 정상 동작(빈 문자열 키 허용). 동시성은 ConcurrentHashMap으로 처리.

## 8. 완료 조건
- 빌드 성공.
- `shouldShow("a|b")` 첫 호출 true, 30초 내 재호출 false.
- 기존 `shouldShow(memoId)` 동작 불변(별도 맵).
- `reset()`이 둘 다 비움.

## 9. 테스트 방법
1. `shouldShow("p|d")` → true, 즉시 재호출 → false.
2. `shouldShow(1L)` → true, 즉시 재호출 → false(기존 회귀 없음).
3. 두 오버로드가 서로 간섭하지 않는지(같은 시점 Long/String 각각 true).

## 10. 금지 사항
- 기존 `shouldShow(Long)` 변경/삭제 금지.
- WINDOW_MS 값 변경 금지.
- 서비스 호출 결선 금지(FEAT-21).
