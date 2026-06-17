# FEAT-10 — 팝업 검증 테스트 화면

- 매칭 이슈: #10
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md` (v2.1 개선 추가 설계 — 개선요청 1)

## 1. 목적
실제 배민커넥트 배차 없이도 사용자가 **팝업 동작을 직접 검증**할 수 있는 테스트 화면을 만든다. 운영 매칭(접근성 서비스의 배민 패키지 화이트리스트)은 **일절 건드리지 않고**, 앱 내부에서 (a) 저장된 메모로 팝업을 직접 띄워 렌더링을 확인하고, (b) 화면에서 본 텍스트를 붙여넣어 실제 추출→매칭→팝업 파이프라인(FEAT-05 + FEAT-06)을 그대로 태워 검증한다. ("푸라닭 신림점" 같은 저장 상호가 감지되면 팝업이 실제로 뜨는지 사람이 확인 가능)

## 2. 범위
### 구현할 것
- `ui/TestActivity.kt`: 두 가지 안전한 검증 모드를 가진 테스트 화면
  - 모드 A: 저장된 메모 중 하나를 선택해 `MemoPopupController.show(this, memo)` 직접 호출 (팝업 렌더링·오버레이 권한 확인)
  - 모드 B: 화면 텍스트를 붙여넣는 입력란 + `StoreExtractor.extract` → `MemoMatcher.match` → 매칭 시 `MemoPopupController.show` (실 파이프라인 검증)
- `res/layout/activity_test.xml`
- `MainActivity`에 [팝업 테스트] 버튼 추가 → `TestActivity` 실행 (`activity_main.xml`에 버튼 1개 추가)
- `AndroidManifest.xml`에 `TestActivity` 등록 (`exported="false"`)
- `strings.xml`에 테스트 화면 문자열 추가

### 구현하지 않을 것
- 접근성 서비스(`PickupAccessibilityService`) 수정 금지 — 운영 매칭 경로/화이트리스트/노이즈 필터 불변. **"테스트 매칭 모드"로 배민 외 패키지를 매칭 대상에 넣는 방식(설계 옵션 b)은 채택하지 않는다.**
- 추출/매칭/팝업 알고리즘 재구현 금지 — 기존 `StoreExtractor`/`MemoMatcher`/`MemoPopupController` 호출만
- 배민 로그 보관 기능(FEAT-12/13)과 무관

## 3. 입력 / 출력
### 입력
- `MemoRepository.observeAll()`(또는 `getCachedSnapshot()` + `refreshCache()`)로 읽은 저장된 메모 목록
- 모드 B: 사용자가 입력란에 붙여넣은 임의 텍스트(화면에서 본 텍스트 모사)
### 출력
- 화면 상단 오버레이 팝업 표시(권한 있을 때) + 결과 토스트(추출/매칭 성공·실패 안내)

## 4. 동작 흐름
1. MainActivity의 [팝업 테스트] 버튼 → `startActivity(Intent(this, TestActivity::class.java))`.
2. `TestActivity.onCreate`: `MemoRepository.init(applicationContext)` 멱등 호출 → `lifecycleScope.launch { MemoRepository.refreshCache(); 메모목록을 Spinner에 채움 }`.
3. **모드 A** — [선택한 메모로 팝업 띄우기] 클릭:
   - 메모 0건이면 토스트("저장된 메모가 없습니다") 후 return.
   - `Settings.canDrawOverlays(this)`가 false면 토스트("오버레이 권한을 먼저 허용하세요") 후 return.
   - 선택된 `Memo`로 `MemoPopupController.show(this, memo)` 호출 → 상단 팝업 표시(6초 자동 닫힘은 FEAT-06이 처리).
4. **모드 B** — [텍스트로 매칭 테스트] 클릭:
   - 입력 텍스트 `input`을 읽는다. blank면 토스트 후 return.
   - `candidate = StoreExtractor.extract(input)`; null이면 토스트("추출 실패: '픽업지'·'전달지' 또는 '신규배차_카드' 키워드가 필요합니다") 후 return.
   - `memos = MemoRepository.getCachedSnapshot()`(없으면 빈 목록); `matched = MemoMatcher.match(candidate, memos)`.
   - matched null이면 토스트("매칭되는 메모 없음 / 추출된 상호: $candidate") 후 return.
   - `canDrawOverlays` false면 토스트 안내 후 return.
   - `MemoPopupController.show(this, matched)` + 토스트("매칭: ${matched.storeName} ${matched.branchName}").

## 5. 수정 예상 파일
- 신규: `.../ui/TestActivity.kt`, `res/layout/activity_test.xml`
- 수정: `MainActivity.kt`(버튼 리스너 추가), `res/layout/activity_main.xml`(버튼 추가), `AndroidManifest.xml`(TestActivity 등록), `res/values/strings.xml`(문자열 추가)

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
package com.itmakesome.pickupmemo2.ui

class TestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTestBinding
    private var memos: List<Memo> = emptyList()   // Spinner 표시용

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.title_test)

        MemoRepository.init(applicationContext)
        lifecycleScope.launch {
            MemoRepository.refreshCache()
            memos = MemoRepository.getCachedSnapshot()
            val labels = memos.map { "${it.storeName} ${it.branchName}" }
            binding.spinnerMemos.adapter = ArrayAdapter(
                this@TestActivity, android.R.layout.simple_spinner_dropdown_item, labels
            )
        }

        binding.btnShowSelected.setOnClickListener { showSelectedMemo() }
        binding.btnMatchTest.setOnClickListener { runMatchTest() }
    }

    private fun showSelectedMemo() { /* 4단계 3 흐름 */ }
    private fun runMatchTest() { /* 4단계 4 흐름 */ }
}
```
- `MemoPopupController.show(context, memo)` 시그니처(FEAT-06)·`StoreExtractor.extract(fullText: String?): String?`(FEAT-05)·`MemoMatcher.match(detectedStoreText: String, memos: List<Memo>): Memo?`(FEAT-05)를 그대로 사용한다.
- `activity_test.xml` 구성(세로 LinearLayout, padding 24dp):
  - 모드 A 섹션: 안내 TextView + `Spinner(spinnerMemos)` + `Button(btnShowSelected, "선택한 메모로 팝업 띄우기")`
  - 구분선 + 모드 B 섹션: 안내 TextView + 멀티라인 `EditText(etTestText, hint="화면에서 본 텍스트 붙여넣기 (예: 픽업지 푸라닭 신림점 전달지 ...)")` + `Button(btnMatchTest, "텍스트로 매칭 테스트")`
- 문자열 추가(strings.xml): `title_test`("팝업 테스트"), `btn_popup_test`("팝업 테스트"), `btn_show_selected`, `btn_match_test`, `hint_test_text`, `test_no_memo`, `test_overlay_required`, `test_extract_failed`, `test_no_match`(`매칭 없음 · 추출: %s`), `test_matched`(`매칭: %s`), `test_section_a`, `test_section_b`.

## 7. 예외 처리
- 저장 메모 0건: Spinner 비어 있음 → 모드 A는 토스트 안내 후 무동작.
- 오버레이 권한 미허가: `MemoPopupController`가 내부적으로 조용히 skip하므로, 화면에서 **사전 체크 + 토스트**로 사용자에게 이유를 알린다(아무 일도 안 일어나 보이는 혼란 방지).
- 모드 B 추출 실패/매칭 실패: 각각 명확한 토스트로 구분 안내(추출된 후보 문자열 노출).

## 8. 완료 조건
- 빌드 성공.
- MainActivity에서 [팝업 테스트]로 TestActivity 진입 가능.
- 모드 A: 저장된 메모 선택 → (오버레이 권한 허가 시) 상단 팝업이 뜨고 6초 후 사라진다.
- 모드 B: "픽업지 푸라닭 신림점 전달지 ..." 입력 → "푸라닭/신림점" 메모가 저장돼 있으면 팝업 표시, 없으면 "매칭 없음" 토스트.
- 접근성 서비스 코드·운영 매칭 동작은 변경되지 않는다(기본 동작 불변).

## 9. 테스트 방법
1. 메모 "푸라닭 / 신림점 / (내용)" 등록, 오버레이 권한 허용.
2. [팝업 테스트] 진입 → Spinner에서 메모 선택 → [선택한 메모로 팝업 띄우기] → 상단 팝업 확인.
3. 모드 B 입력란에 `픽업지 푸라닭 신림점 전달지 강남` 붙여넣기 → [텍스트로 매칭 테스트] → 팝업 + "매칭" 토스트 확인.
4. 매칭 안 되는 텍스트(`픽업지 없는상호 전달지 x`) → "매칭 없음" 토스트 확인.
5. 오버레이 권한 끈 상태 → 두 버튼 모두 "오버레이 권한을 먼저 허용하세요" 토스트 확인.

## 10. 금지 사항
- `PickupAccessibilityService` 및 운영 매칭 화이트리스트/노이즈 필터 수정 금지(영구 변경 금지).
- 추출/매칭/팝업 알고리즘 재구현 금지(기존 object 호출만).
- 배민 로그 보관/내보내기(FEAT-12/13) 선구현 금지.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
