# FEAT-08 — 권한 안내 (접근성 + 오버레이)

- 매칭 이슈: #8
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
접근성 서비스 권한과 오버레이("다른 앱 위에 표시") 권한의 현재 상태를 MainActivity에서 보여주고, 미허가 시 각 설정 화면으로 이동하는 버튼을 제공한다. 재부팅/강제종료 후 복귀 시 onResume에서 상태를 재확인해 안내한다. (F-12, F-13 / AC-09, AC-10 / PRD 비기능 백그라운드 복원 안내)

## 2. 범위
### 구현할 것
- `util/PermissionChecker.kt`: 접근성 활성 판정(v1 로직 재사용) + 오버레이 권한 판정 + 두 설정 이동 Intent
- MainActivity 완성: 권한 상태 2줄 표시(onResume 갱신), [접근성 설정] [오버레이 권한 설정] 버튼, 기존 [메모 관리] 버튼 유지
- `activity_main.xml`에 상태 TextView·버튼 추가, strings 추가
### 구현하지 않을 것
- 메모 CRUD 화면 → FEAT-03/04
- 팝업/서비스 로직 → FEAT-06/07
- 자동 복원(BootReceiver) → 미구현(PRD: 자동 복원 미보장, 안내만)

## 3. 입력 / 출력
### 입력
- 시스템 설정값(`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, `Settings.canDrawOverlays`)
### 출력
- 화면에 권한 상태 표시 + 설정 화면 이동

## 4. 동작 흐름
1. MainActivity.onCreate: 버튼 리스너 연결([접근성 설정]→accessibility intent, [오버레이 설정]→overlay intent, [메모 관리]→MemoListActivity).
2. onResume: `refreshStatus()` — 접근성 활성/오버레이 허가 여부를 재판정해 상태 텍스트 갱신. (설정에서 토글하고 돌아오면 즉시 반영, 재부팅/강제종료 후 복귀 시에도 현재 상태 표시)
3. 권한 미허가 안내: 미허가 상태는 상태 텍스트로 항상 노출(별도 모달 불필요) — AC-09/AC-10 충족.

## 5. 수정 예상 파일
- 신규: `.../util/PermissionChecker.kt`
- 수정: `MainActivity.kt`, `res/layout/activity_main.xml`, `res/values/strings.xml`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
object PermissionChecker {
    fun isAccessibilityEnabled(context: Context): Boolean {
        // v1 PermissionChecker.isAccessibilityEnabled 로직 재사용:
        // ComponentName(context, PickupAccessibilityService::class.java)를
        // Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES(":" 구분)에서 찾는다.
    }
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
               Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```
- 접근성 판정은 v1 `projects/PickUpMemo/.../util/PermissionChecker.kt`의 `isAccessibilityEnabled` + `matchesComponent` 로직을 그대로 가져오되 서비스 클래스만 `PickupAccessibilityService`로 교체. (알림 리스너 관련 함수는 가져오지 않음)
- MainActivity:
```kotlin
override fun onResume() { super.onResume(); refreshStatus() }
private fun refreshStatus() {
    val a11y = PermissionChecker.isAccessibilityEnabled(this)
    val overlay = PermissionChecker.canDrawOverlays(this)
    binding.tvAccessibilityStatus.text = getString(R.string.status_accessibility,
        if (a11y) getString(R.string.status_on) else getString(R.string.status_off))
    binding.tvOverlayStatus.text = getString(R.string.status_overlay,
        if (overlay) getString(R.string.status_granted) else getString(R.string.status_denied))
}
```
- 설정 이동은 `try/catch(ActivityNotFoundException)`로 감싸 토스트 안내(크래시 방지, v1 패턴).

## 7. 예외 처리
- 설정 액션 미존재 기기 → ActivityNotFoundException catch + 토스트.
- onResume 재판정으로 외부 토글/재부팅 상태를 항상 반영.

## 8. 완료 조건
- 빌드 성공.
- 접근성 미허가 상태로 앱 실행 시 안내(상태 "꺼짐" + 설정 버튼) 표시(AC-09).
- 오버레이 미허가 시 안내 + 설정 이동 버튼 제공(AC-10).
- 설정에서 권한 토글 후 돌아오면 상태가 갱신된다.

## 9. 테스트 방법
1. 권한 모두 끈 상태로 앱 실행 → 두 상태 "꺼짐/미허가" + 버튼 표시.
2. [접근성 설정] → 토글 ON → 복귀 시 "실행 중" 갱신.
3. [오버레이 권한 설정] → 허용 → 복귀 시 "허가" 갱신.
4. 앱 강제종료 후 재실행 → 현재 권한 상태가 정확히 표시.

## 10. 금지 사항
- 메모 CRUD/팝업/서비스 로직 변경 금지.
- BootReceiver·자동 권한 복원 구현 금지(범위 밖).
- v1 알림 접근 권한 관련 UI/판정 이식 금지.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
