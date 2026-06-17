# FEAT-06 — 오버레이 팝업 컨트롤러

- 매칭 이슈: #6
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
매칭된 메모를 배민커넥트 화면 위에 오버레이 팝업으로 표시하고 6초 후 자동으로 닫는 컨트롤러를 만든다. 태그가 없으면 태그 영역을 표시하지 않는다. (F-05, F-06 / AC-02, AC-11)

## 2. 범위
### 구현할 것
- `overlay/MemoPopupController.kt`: WindowManager로 오버레이 add/remove, 6초 자동 닫힘, 태그 조건부 표시
- `res/layout/overlay_memo_popup.xml`: 상단 카드형 팝업 레이아웃
### 구현하지 않을 것
- 접근성 서비스에서 호출하는 결선 → FEAT-07 (여기서는 `show(context, memo)` API만 제공)
- 권한 판정/요청 → FEAT-08 (컨트롤러는 `canDrawOverlays` 미허가 시 조용히 skip만)

## 3. 입력 / 출력
### 입력
- `show(context: Context, memo: Memo)` — context는 접근성 서비스 컨텍스트(FEAT-07이 전달)
### 출력
- 화면 상단에 팝업 View 표시 → 6초 후 자동 제거

## 4. 동작 흐름
1. `show(context, memo)` 호출(메인 스레드 가정; 아니면 메인 핸들러로 post).
2. `Settings.canDrawOverlays(context)`가 false면 즉시 return(크래시 방지).
3. 이미 표시 중인 팝업이 있으면 제거(removeCallbacks + removeView).
4. `overlay_memo_popup.xml` inflate → 제목 `"$storeName $branchName"`, 메모내용 content 설정.
5. 태그: `memo.tag`가 null이거나 blank면 태그 뷰 `visibility = GONE`, 아니면 VISIBLE + 텍스트 설정.
6. WindowManager.addView(view, params). params는 6장 참조.
7. `mainHandler.postDelayed(dismissRunnable, 6000)`로 6초 후 removeView.

## 5. 수정 예상 파일
- 신규: `.../overlay/MemoPopupController.kt`, `res/layout/overlay_memo_popup.xml`
- 수정: `res/values/colors.xml`/`themes.xml`(팝업 카드 배경색·라운드 drawable 필요 시), `res/values/strings.xml`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
object MemoPopupController {
    const val AUTO_DISMISS_MS = 6000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var wm: WindowManager? = null
    private val dismissRunnable = Runnable { dismiss() }

    fun show(context: Context, memo: Memo) {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.post {
            dismiss()  // 기존 팝업 제거
            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_memo_popup, null)
            view.findViewById<TextView>(R.id.tvPopupTitle).text = "${memo.storeName} ${memo.branchName}"
            view.findViewById<TextView>(R.id.tvPopupContent).text = memo.content
            val tagView = view.findViewById<TextView>(R.id.tvPopupTag)
            if (memo.tag.isNullOrBlank()) tagView.visibility = View.GONE
            else { tagView.visibility = View.VISIBLE; tagView.text = memo.tag }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = (48 * context.resources.displayMetrics.density).toInt() // 상태바 아래 여백
            }
            try { wmLocal.addView(view, params) } catch (e: Exception) { return@post }
            wm = wmLocal; currentView = view
            mainHandler.removeCallbacks(dismissRunnable)
            mainHandler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
        }
    }
    fun dismiss() {
        mainHandler.removeCallbacks(dismissRunnable)
        val v = currentView ?: return
        try { wm?.removeView(v) } catch (_: Exception) {}
        currentView = null
    }
}
```
- `overlay_memo_popup.xml`: 루트 카드(좌우 margin 16dp, padding 16dp, 라운드 배경 drawable, 약간 불투명 배경 + elevation). 자식: `tvPopupTitle`(굵게, 17sp), `tvPopupContent`(15sp, 최대 4줄), `tvPopupTag`(작은 칩 스타일, 기본 GONE).
- 팝업은 `FLAG_NOT_TOUCHABLE`이라 터치가 아래(배차 카드)로 통과 → 거절/수락 버튼 조작 방해 없음.

## 7. 예외 처리
- `canDrawOverlays` false → skip(권한 안내는 FEAT-08 책임).
- addView/removeView 중복·view 미부착 예외 → try/catch로 무시.
- 새 show가 들어오면 기존 팝업·타이머를 먼저 정리(중복 누적 방지).

## 8. 완료 조건
- 빌드 성공.
- (오버레이 권한 허가 상태에서) show 호출 시 상단에 팝업이 뜨고 6초 후 사라진다.
- 태그 없는 메모는 태그 영역이 보이지 않는다(GONE) — "태그 없음"/빈칸 노출 없음.

## 9. 테스트 방법
- 임시 디버그 트리거(예: MemoListActivity에 임시 버튼 또는 FEAT-07 연동 후) `show(context, memo)` 호출.
- 태그 있는 메모/없는 메모 각각으로 호출해 태그 영역 표시/미표시 확인.
- 6초 후 자동 소멸, 터치가 아래 화면으로 통과하는지 확인.

## 10. 금지 사항
- 접근성 이벤트 수신 코드 포함 금지(FEAT-07).
- 권한 요청/안내 UI 추가 금지(FEAT-08).
- 수동 닫기 버튼·드래그 등 부가 UX 금지(자동 닫힘만).
- 태그 빈 값에 대체 문구 표시 금지.
