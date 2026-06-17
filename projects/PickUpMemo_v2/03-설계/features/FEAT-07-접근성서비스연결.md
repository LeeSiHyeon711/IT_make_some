# FEAT-07 — 접근성 서비스 본문 연결

- 매칭 이슈: #7
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
배민커넥트 화면 변경 이벤트를 수신해 노드 트리에서 텍스트를 조립하고, 패키지 필터 → 업체명 추출(FEAT-05) → 메모 매칭(FEAT-05) → 중복억제(FEAT-05) → 오버레이 팝업(FEAT-06)으로 결선한다. 이 FEAT가 앱의 핵심 가치를 완성한다. (F-01, F-02, F-03, F-04, F-07 / AC-01, AC-04, AC-05, AC-08)

## 2. 범위
### 구현할 것
- `service/PickupAccessibilityService.kt` 본문: 이벤트 수신, 패키지 필터, 노드 트리 텍스트 조립, 추출·매칭·중복억제·팝업 호출, 메모 캐시 초기화
### 구현하지 않을 것
- 추출/매칭/중복억제 알고리즘 → FEAT-05의 object 호출만
- 팝업 렌더링 → FEAT-06의 `MemoPopupController.show` 호출만
- 권한 안내 → FEAT-08

## 3. 입력 / 출력
### 입력
- `AccessibilityEvent` (시스템 콜백), `MemoRepository.getCachedSnapshot()`
### 출력
- 조건 충족 시 `MemoPopupController.show(this, matchedMemo)` 호출(팝업 표시)

## 4. 동작 흐름
1. `onServiceConnected()`: `MemoRepository.init(applicationContext)` 호출 후, **서비스 전용 `serviceScope`(아래)에서** `refreshCache()`를 1회 호출한다. AccessibilityService는 Activity/Fragment가 아니므로 `lifecycleScope`를 사용하지 않는다. `onDestroy()`에서 `serviceScope.cancel()`로 정리한다.
2. `onAccessibilityEvent(event)`:
   - event null → return.
   - `pkg = event.packageName?.toString()`. null/blank → return.
   - `pkg == applicationContext.packageName` → return (자기앱 제외).
   - `pkg in Packages.EXCLUDED_PACKAGES` → return (방어층).
   - `pkg != Packages.TARGET_PACKAGE` → return (화이트리스트: 배민커넥트만 처리).
   - 노드 트리 조립: `root = event.source ?: rootInActiveWindow`; `collectNode(root, segments)` (v1 ScreenAccessibilityService의 collectNode 로직 재사용 — text/desc/id 세그먼트를 `LinkedHashSet`에 모아 ` / `로 join). 최대 세그먼트 200 가드.
   - `fullText = segments.joinToString(" / ")`.
   - `candidate = StoreExtractor.extract(fullText)`; null → return.
   - `memos = MemoRepository.getCachedSnapshot()`; `matched = MemoMatcher.match(candidate, memos)`; null → return.
   - `if (!DedupGuard.shouldShow(matched.id)) return`.
   - `MemoPopupController.show(this, matched)`.
3. `onInterrupt()`: no-op.

## 5. 수정 예상 파일
- 수정: `.../service/PickupAccessibilityService.kt` (FEAT-01 스텁을 본문으로 채움)

## 6. 데이터 구조 / 함수 / 클래스
- `collectNode(node: AccessibilityNodeInfo, out: LinkedHashSet<String>)`: v1과 동일하게 text/contentDescription/viewIdResourceName을 `"text | desc=.. | id=.."` 세그먼트로 만들고 자식 DFS 순회, `MAX_SEGMENTS_PER_EVENT=200` 가드. (v1 `ScreenAccessibilityService.kt` 86~110행 패턴 복제)
- 캐시 갱신용 코루틴 스코프: 서비스에 **전용 `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`** 를 두고 `onServiceConnected`에서 `refreshCache()` 호출, `onDestroy`에서 `serviceScope.cancel()`. `lifecycleScope`는 서비스에 존재하지 않으므로 사용하지 않는다. (메모 추가/수정은 Repository가 캐시를 갱신하나, 서비스 시작 시 1회 보장 필요)
- 매칭·팝업은 접근성 콜백(메인 스레드)에서 동기 호출(getCachedSnapshot은 메모리 읽기라 빠름).

```kotlin
class PickupAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        MemoRepository.init(applicationContext)
        serviceScope.launch {
            MemoRepository.refreshCache()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (pkg == applicationContext.packageName) return
        if (pkg in Packages.EXCLUDED_PACKAGES) return
        if (pkg != Packages.TARGET_PACKAGE) return
        val segments = LinkedHashSet<String>()
        val root = event.source ?: rootInActiveWindow ?: return
        collectNode(root, segments)
        if (segments.isEmpty()) return
        val fullText = segments.joinToString(" / ")
        val candidate = StoreExtractor.extract(fullText) ?: return
        val matched = MemoMatcher.match(candidate, MemoRepository.getCachedSnapshot()) ?: return
        if (!DedupGuard.shouldShow(matched.id)) return
        MemoPopupController.show(this, matched)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
```

## 7. 예외 처리
- root/노드 null → return(크래시 없음).
- 캐시 비어있음(메모 0건) → match null → 팝업 없음(AC-05 정상 동작).
- 오버레이 권한 없으면 MemoPopupController가 내부에서 skip(FEAT-06).
- 노드 순회 비용: 세그먼트 200 가드 + 배민커넥트 패키지로만 한정해 부하 억제.

## 8. 완료 조건
- 빌드 성공.
- 배민커넥트 신규 배차 카드("푸라닭 신림점")에서 등록 메모가 팝업으로 표시(AC-01, AC-08 백그라운드 동작).
- 제외 패키지(카카오맵/카카오톡 등) 이벤트로는 팝업이 뜨지 않음(AC-04).
- 등록 메모 없는 업체는 팝업 없음(AC-05).
- 동일 메모 30초 내 재감지 시 미표시(AC-03).

## 9. 테스트 방법
1. 접근성·오버레이 권한 허가, 메모 "푸라닭/신림점/..." 등록.
2. 배민커넥트 신규 배차 카드 화면(또는 동일 텍스트 모의 화면) → 팝업 표시 확인.
3. 카카오맵/카카오톡 사용 → 팝업 안 뜸 확인.
4. 같은 배차 화면 30초 내 반복 → 1회만 표시 확인.
5. 미등록 업체 카드 → 팝업 없음.

## 10. 금지 사항
- 추출/매칭/중복억제 알고리즘 재구현 금지(FEAT-05 object 호출만).
- 팝업 레이아웃·표시 로직 변경 금지(FEAT-06 호출만).
- 화이트리스트(TARGET_PACKAGE) 우회·전 패키지 처리 금지.
- v1 알림 리스너/로그 저장 결선 금지.
