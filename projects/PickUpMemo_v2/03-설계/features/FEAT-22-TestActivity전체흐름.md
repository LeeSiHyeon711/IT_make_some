> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-22 — TestActivity 수동 입력 → 전체 흐름 재현 (주소→경로→팝업)

- 매칭 이슈: #22
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
실제 배민커넥트 앱 없이도 v3 경로 기능을 검증할 수 있게, 기존 `TestActivity`에 픽업지/전달지를 수동 입력해 `RouteService.resolve → MemoPopupController.show/updateRoute` 전체 흐름을 재현하는 섹션을 추가한다. 마스킹 주소 fallback과 route 갱신을 눈으로 확인한다. (PRD 4-8, AC-6)

## 2. 범위
### 구현할 것
- `res/layout/activity_test.xml`: 모드 C 섹션 추가 — 픽업지 입력 + 전달지 입력 + [경로 조회 팝업] 버튼.
- `ui/TestActivity.kt`: 픽업/전달 입력값으로 `RouteService.init` → `lifecycleScope`에서 `resolve` → `show(null/선택메모, true)` + `updateRoute`.

### 구현하지 않을 것
- 기존 모드 A(메모 선택 팝업)/모드 B(붙여넣기 매칭) 변경 → 보존.
- 접근성 서비스 수정 → 건드리지 않음.
- AddressExtractor 호출은 선택(여기선 입력값을 직접 pickup/dest로 사용. 단, 한 칸에 전체 화면 텍스트를 넣어 AddressExtractor로 분해하는 보조 버튼은 선택 구현 가능).

## 3. 입력 / 출력
### 입력
- 픽업지 텍스트(EditText), 전달지 텍스트(EditText).
### 출력
- 경로 조회 결과를 단일 팝업으로 표시(`등록된 메모 없음` + route 영역). Toast로 성공/실패 안내.

## 4. 동작 흐름
1. 화면 진입 시 `RouteService.init(BuildConfig.KAKAO_REST_API_KEY)`(멱등) 호출.
2. [경로 조회 팝업] 클릭:
   - 픽업/전달 입력값 검증(둘 다 non-blank). blank면 Toast 후 return.
   - 오버레이 권한 확인(미허가 시 Toast — 기존 패턴 재사용).
   - `val token = MemoPopupController.show(this, null, hasRoute = true)` (지연 없이 즉시 팝업, 테스트 가시성).
   - `lifecycleScope.launch { val r = RouteService.resolve(pickup, dest); MemoPopupController.updateRoute(token, r); Toast(r 성공/실패) }`.
3. (선택) 모드 B 결과를 활용해 메모 매칭 시 `show(matched, true)`로도 확장 가능하나 필수 아님.

## 5. 수정 예상 파일
- 수정: `app/src/main/res/layout/activity_test.xml`
- 수정: `app/src/main/java/com/itmakesome/pickupmemo2/ui/TestActivity.kt`
- 수정: `app/src/main/res/values/strings.xml` (입력 힌트/버튼/Toast 문자열)

## 6. 데이터 구조 / 함수 / 클래스
레이아웃 추가(모드 B 아래, 구분선 후):
```xml
<TextView android:id="@+id/tvSectionC" ... android:text="@string/test_section_c" .../>
<EditText android:id="@+id/etPickup" android:hint="@string/hint_pickup" .../>
<EditText android:id="@+id/etDest" android:hint="@string/hint_dest" .../>
<Button android:id="@+id/btnRouteTest" android:text="@string/btn_route_test" .../>
```
TestActivity 추가:
```kotlin
import com.itmakesome.pickupmemo2.BuildConfig
import com.itmakesome.pickupmemo2.route.RouteService

// onCreate에 추가
RouteService.init(BuildConfig.KAKAO_REST_API_KEY)
binding.btnRouteTest.setOnClickListener { runRouteTest() }

private fun runRouteTest() {
    val pickup = binding.etPickup.text?.toString()?.trim().orEmpty()
    val dest = binding.etDest.text?.toString()?.trim().orEmpty()
    if (pickup.isBlank() || dest.isBlank()) { Toast(...); return }
    if (!Settings.canDrawOverlays(this)) { Toast(test_overlay_required); return }
    val token = MemoPopupController.show(this, null, hasRoute = true)
    lifecycleScope.launch {
        val r = RouteService.resolve(pickup, dest)
        MemoPopupController.updateRoute(token, r)
        Toast.makeText(this@TestActivity,
            r?.summaryText ?: getString(R.string.popup_route_unavailable),
            Toast.LENGTH_LONG).show()
    }
}
```
strings.xml 추가: `test_section_c`, `hint_pickup`(예 `푸라닭 신림점`), `hint_dest`(예 `서울 관악구 남부순환로161나길 13 **** (신림동)`), `btn_route_test`(`경로 조회 팝업`).

## 7. 예외 처리
- 입력 blank/오버레이 미허가 → Toast 후 중단(크래시 없음).
- resolve null(키없음/실패/timeout) → 팝업 route 영역 `거리 정보 확인 불가` + Toast.
- 기존 모드 A/B 로직과 충돌 없도록 별도 메서드/버튼 사용.

## 8. 완료 조건
- 빌드 성공.
- 픽업/전달 입력 후 버튼 → 팝업 표시, route 영역이 거리/시간 또는 `거리 정보 확인 불가`로 갱신.
- 마스킹 전달지 입력 시에도 좌표 fallback으로 조회 시도(FEAT-18 경로).
- 기존 모드 A/B 동작 보존.

## 9. 테스트 방법 (회귀 포함)
1. **회귀**: 모드 A(메모 선택 팝업)·모드 B(붙여넣기 매칭) 기존대로 동작.
2. 모드 C: `푸라닭 신림점` / `서울 관악구 남부순환로161나길 13 **** (신림동)` 입력 → 팝업 + 거리/시간.
3. 잘못된 주소/키 없음 → `거리 정보 확인 불가`.
4. 입력 비우고 버튼 → Toast 안내, 크래시 없음.

## 10. 금지 사항
- 기존 모드 A/B 코드 변경/삭제 금지.
- 접근성 서비스(PickupAccessibilityService) 수정 금지.
- RouteService/MemoPopupController 내부 로직 변경 금지(호출만).
- 불필요한 라이브러리 추가 금지.
