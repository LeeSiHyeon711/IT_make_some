> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-21 — onAccessibilityEvent 결선 (트리거 승격·4케이스·빌드분기·지연표시) ★위험도 高

- 매칭 이슈: #21
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md` (5장 결선 흐름도가 이 FEAT의 기준)

## 1. 목적
v3 핵심 결선. 기존 매칭 라인을 보존한 채 AddressExtractor(FEAT-16)와 RouteService(FEAT-18)를 결합해, **팝업 트리거를 "메모 매칭 또는 주소 추출 성공"으로 승격**하고, 4가지 케이스(A/B/C/D) + 빌드타입 분기 + 케이스 C release 지연표시를 구현한다. (PRD 4-2~4-4, AC-1·2·3·5)

## 2. 범위
### 구현할 것
- `service/PickupAccessibilityService.kt`의 `onAccessibilityEvent` 결선 변경(설계서 5장 흐름도).
- `onServiceConnected`에 `RouteService.init(BuildConfig.KAKAO_REST_API_KEY)` 추가.
- AddressExtractor 호출 + 케이스 분기(메모 유무 × route 성공/실패) + `BuildConfig.DEBUG` 분기 + 지연표시.
- 메모없음 dedup은 `DedupGuard.shouldShow(addr.key())` 사용.

### 구현하지 않을 것
- AddressExtractor/RouteService/MemoPopupController 내부 로직 → FEAT-16/18/19 호출만.
- 로그저장(maybeLogBaemin)·패키지 필터·collectNode 변경 → 보존(불변).
- v3.1 힐 → 호출 안 함.

## 3. 입력 / 출력
### 입력
- `AccessibilityEvent`, `MemoRepository.getCachedSnapshot()`, `BuildConfig.KAKAO_REST_API_KEY`, `BuildConfig.DEBUG`.
### 출력
- 케이스별 `MemoPopupController.show(...)` + 비동기 `updateRoute(...)`.

## 4. 동작 흐름 (설계서 5장 흐름도 그대로)
보존 라인: 패키지 필터 → `collectNode` → `fullText`/`segments` → `maybeLogBaemin(...)`.

그 다음:
```kotlin
val snapshot = MemoRepository.getCachedSnapshot()
val candidate = StoreExtractor.extract(fullText)
val matched = candidate?.let { MemoMatcher.match(it, snapshot) }
val addr = AddressExtractor.extract(segments.toList(), fullText)

if (matched == null && addr == null) return          // 트리거 승격
val hasRoute = addr != null

// 중복 억제
if (matched != null) { if (!DedupGuard.shouldShow(matched.id)) return }
else { if (!DedupGuard.shouldShow(addr!!.key())) return }

when {
    matched != null -> {                              // 케이스 A/B (메모 있음, 즉시 표시)
        val token = MemoPopupController.show(this, matched, hasRoute)
        if (hasRoute) serviceScope.launch {
            val r = RouteService.resolve(addr!!.pickup, addr.dest)
            MemoPopupController.updateRoute(token, r)
        }
    }
    addr != null && BuildConfig.DEBUG -> {            // 메모 없음 + debug (즉시 표시)
        val token = MemoPopupController.show(this, null, true)
        serviceScope.launch {
            val r = RouteService.resolve(addr.pickup, addr.dest)
            MemoPopupController.updateRoute(token, r)
        }
    }
    addr != null -> {                                 // 메모 없음 + release (지연 표시)
        serviceScope.launch {
            val r = RouteService.resolve(addr.pickup, addr.dest)
            if (r != null) {
                val token = MemoPopupController.show(this, null, true)
                MemoPopupController.updateRoute(token, r)
            }   // r == null → 미표시(케이스 D-release)
        }
    }
}
```
주: `segments`는 기존에 `LinkedHashSet<String>`이므로 `segments.toList()`로 순서 보존 리스트 전달. 기존 `if (segments.isEmpty()) return`·`fullText` 조립은 그대로.

## 5. 수정 예상 파일
- 수정: `app/src/main/java/com/itmakesome/pickupmemo2/service/PickupAccessibilityService.kt`

## 6. 데이터 구조 / 함수 / 클래스
- 신규 import: `AddressExtractor`, `com.itmakesome.pickupmemo2.route.RouteService`, `com.itmakesome.pickupmemo2.BuildConfig`.
- `onServiceConnected`에 `RouteService.init(BuildConfig.KAKAO_REST_API_KEY)` 추가(기존 MemoRepository/BaeminLogRepository init 보존).
- `serviceScope`(기존 IO 스코프) 재사용 — 새 스코프 만들지 않는다.
- `show`/`updateRoute`는 내부에서 `mainHandler.post`하므로 IO에서 호출해도 안전(지연표시 포함).

## 7. 예외 처리
- addr·matched 둘 다 null → return(팝업 없음).
- RouteService.resolve가 null(timeout/실패/키없음) → 메모 있으면 케이스 B(`거리 정보 확인 불가`), 메모없음+release면 미표시, 메모없음+debug면 `거리 정보 확인 불가`.
- updateRoute는 토큰 세대 검증으로 stale 자동 무시(FEAT-19).
- 접근성 콜백(메인 스레드)에서 네트워크 직접 호출 금지 — 반드시 `serviceScope.launch`.

## 8. 완료 조건
- 빌드 성공(debug/release 둘 다).
- 케이스 A: 메모 있는 배차 → 즉시 메모 팝업 → route 영역 갱신.
- 케이스 B: 메모 있고 조회 실패 → `거리 정보 확인 불가`.
- 케이스 C: 메모 없고 조회 성공 → (release) 조회 후 팝업, (debug) 즉시 팝업 후 갱신.
- 케이스 D: 메모 없고 조회 실패 → release 미표시 / debug 표시(`거리 정보 확인 불가`).
- 기존 매칭 라인·로그저장·화이트리스트 동작 보존.

## 9. 테스트 방법 (회귀 검증 필수 — 위험도 高)
1. **회귀(AC-8)**: 메모 등록 배차(또는 TestActivity 모드 B)에서 메모 팝업이 v2와 동일하게 뜸. baemin_logs 적재 지속. 메모 CRUD 정상.
2. 케이스 A: 유효 키 + 메모 있는 카드 → 메모 + 거리/시간.
3. 케이스 B: 키 비우거나 망 차단 + 메모 있는 카드 → `거리 정보 확인 불가`.
4. 케이스 C/D: 메모 없는 카드 → debug 빌드는 팝업, release 빌드는 (성공 시만/실패 시 미표시).
5. 같은 배차 30초 반복 → 1회만(메모없음은 key 기준).
6. 비배민 패키지 → 팝업 없음(화이트리스트 보존).

## 10. 금지 사항
- StoreExtractor/MemoMatcher/AddressExtractor/RouteService/MemoPopupController 내부 재구현 금지(호출만).
- 패키지 화이트리스트(TARGET_PACKAGE) 우회·전 패키지 처리 금지.
- `maybeLogBaemin`·`collectNode`·패키지 필터 동작 변경 금지(보존).
- 접근성 콜백 스레드에서 네트워크 동기 호출 금지(반드시 serviceScope).
- 새 코루틴 스코프 생성 금지(기존 serviceScope 재사용).
