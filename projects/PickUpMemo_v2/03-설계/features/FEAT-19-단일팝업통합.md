> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-19 — 단일 팝업 통합 (레이아웃 route/힐 자리 + MemoPopupController 확장) ★위험도 高

- 매칭 이슈: #19
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md`

## 1. 목적
메모와 거리/시간을 **하나의 팝업** 안에 함께 표시하도록 기존 `MemoPopupController`를 확장한다. 별도 팝업 컨트롤러를 만들지 않는다. 메모 nullable 표시 + route 영역 비동기 갱신 + 토큰 세대 검증(경합 안전)을 추가한다. v3.1 오르막 자리도 레이아웃에만 마련한다. (PRD 4-1, 4-4, AC-2, AC-3)

## 2. 범위
### 구현할 것
- `res/layout/overlay_memo_popup.xml`: route 영역 TextView(`tvPopupRoute`) + v3.1 힐 자리 TextView(`tvPopupHill`, 기본 GONE) 추가. **기존 ID 보존**.
- `overlay/MemoPopupController.kt`: `show(context, memo: Memo?, hasRoute: Boolean): Long` + `updateRoute(token, route)` 추가, 기존 `show(context, memo: Memo)`는 신규로 위임.
- `strings.xml`: `등록된 메모 없음`, `거리 정보 확인 불가` 문자열 추가.

### 구현하지 않을 것
- 서비스 결선(케이스 분기/빌드분기) → FEAT-21.
- RouteService 호출 → FEAT-21/22가 함.
- v3.1 힐 실제 표시 → v3.1(여기선 GONE 자리만).

## 3. 입력 / 출력
### 입력
- `show(context, memo: Memo?, hasRoute: Boolean)` — memo null이면 "등록된 메모 없음", hasRoute=true면 route 영역 표시.
- `updateRoute(token: Long, route: RouteInfo?)`.
### 출력
- 화면 상단 오버레이 팝업. `show`는 토큰(Long) 반환. `updateRoute`는 route 영역 텍스트 갱신.

## 4. 동작 흐름
### show(context, memo, hasRoute)
1. `token = generation.incrementAndGet()`를 **`mainHandler.post` 밖에서** 만들어 함수 끝에서 반환.
2. 오버레이 권한 미허가면 표시 skip(기존처럼) 후 token 반환.
3. `mainHandler.post {}` 안에서:
   - `dismiss()`(기존 팝업/타이머 제거) → `currentToken = token`.
   - inflate, params(기존 그대로) addView.
   - memo != null: `tvPopupTitle = "${storeName} ${branchName}"`, `tvPopupContent = content`, 태그 기존 로직.
   - memo == null: `tvPopupTitle.text = getString(등록된 메모 없음)`, `tvPopupContent` GONE, `tvPopupTag` GONE.
   - hasRoute == true: `tvPopupRoute` VISIBLE, 초기 placeholder(예: `…` 또는 빈 문자열) → 이후 updateRoute로 교체.
   - hasRoute == false: `tvPopupRoute` GONE.
   - `tvPopupHill` 항상 GONE(v3.1 자리).
   - 6초 자동닫힘 타이머 등록(기존).

### updateRoute(token, route)
1. `mainHandler.post {}` 안에서 `if (token != currentToken || currentView == null) return@post`(stale 무시).
2. `currentView.findViewById<TextView>(R.id.tvPopupRoute).text = route?.summaryText ?: getString(거리 정보 확인 불가)`. VISIBLE 보장.

### 기존 show(context, memo: Memo) — 위임
```kotlin
fun show(context: Context, memo: Memo) { show(context, memo, hasRoute = false) }
```

## 5. 수정 예상 파일
- 수정: `app/src/main/res/layout/overlay_memo_popup.xml`
- 수정: `app/src/main/java/com/itmakesome/pickupmemo2/overlay/MemoPopupController.kt`
- 수정: `app/src/main/res/values/strings.xml`

## 6. 데이터 구조 / 함수 / 클래스
레이아웃 추가(기존 tvPopupTag 아래, 같은 카드 LinearLayout 내부):
```xml
<!-- route 영역: 거리/시간 또는 '거리 정보 확인 불가' (기본 GONE, show에서 제어) -->
<TextView
    android:id="@+id/tvPopupRoute"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="6dp"
    android:textColor="#CC1A73E8"
    android:textSize="15sp"
    android:textStyle="bold"
    android:visibility="gone" />

<!-- v3.1 오르막 알림 자리(동작 없음, 항상 GONE) -->
<TextView
    android:id="@+id/tvPopupHill"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:textColor="#CCD84315"
    android:textSize="13sp"
    android:visibility="gone" />
```
MemoPopupController 추가 멤버:
```kotlin
import com.itmakesome.pickupmemo2.route.RouteInfo
import java.util.concurrent.atomic.AtomicLong

private val generation = AtomicLong(0)
@Volatile private var currentToken = 0L

fun show(context: Context, memo: Memo?, hasRoute: Boolean): Long { /* 위 흐름 */ }
fun updateRoute(token: Long, route: RouteInfo?) { /* 위 흐름 */ }
fun show(context: Context, memo: Memo) { show(context, memo, hasRoute = false) }
```
- `import` 시 `Memo`는 nullable 허용. 문자열은 `context.getString(R.string.popup_no_memo)` / `R.string.popup_route_unavailable`.

strings.xml 추가:
```xml
<string name="popup_no_memo">등록된 메모 없음</string>
<string name="popup_route_unavailable">거리 정보 확인 불가</string>
```

## 7. 예외 처리
- 오버레이 권한 미허가: 표시 skip, token은 반환(updateRoute는 currentView null로 자동 무시).
- 자동닫힘/dismiss/새 show 후 늦게 온 updateRoute: `token != currentToken` 또는 `currentView == null`로 무시(경합 안전).
- addView 실패(detached): 기존처럼 try-catch 무시.

## 8. 완료 조건
- 빌드 성공.
- `show(ctx, memo, false)`: 기존과 동일한 메모 팝업(route 영역 GONE) — **회귀 없음**.
- `show(ctx, memo, true)` 후 `updateRoute(token, routeInfo)`: 같은 팝업 route 영역이 `약 X.Xkm · 약 N분`으로 갱신(팝업 재생성 없음).
- `show(ctx, null, true)`: "등록된 메모 없음" + route 영역.
- `updateRoute(staleToken, ...)`: 갱신 안 됨.
- 기존 `show(context, memo)` 호출부(TestActivity·서비스)가 그대로 컴파일·동작.

## 9. 테스트 방법 (회귀 검증 필수 — 위험도 高)
1. **회귀**: 기존 메모 팝업이 v2와 동일하게 뜨는지(TestActivity 모드 A로 메모 선택 → 팝업) — route 영역 안 보이고 메모만 표시.
2. `show(ctx, memo, true)` → placeholder → `updateRoute` → 거리/시간 표시 확인(전체 재생성 없이 텍스트만 바뀜).
3. `show(ctx, null, true)` → "등록된 메모 없음" + route.
4. show 두 번 연속 호출 후 첫 token으로 updateRoute → 무시되는지.
5. 자동 6초 닫힘 후 updateRoute → 무시되는지.

## 10. 금지 사항
- 별도 RoutePopupController/별도 윈도우 생성 금지(단일 팝업 원칙).
- 기존 레이아웃 ID(tvPopupTitle/tvPopupContent/tvPopupTag) 변경·삭제 금지.
- 기존 `show(context, memo: Memo)` 시그니처 제거/변경 금지(회귀 방어).
- 서비스 결선/케이스 분기/RouteService 호출 금지(FEAT-21).
- v3.1 힐 영역에 실제 텍스트 채우기 금지(GONE 유지).
