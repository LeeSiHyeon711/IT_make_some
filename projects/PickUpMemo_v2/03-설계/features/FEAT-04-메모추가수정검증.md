# FEAT-04 — 메모 추가/수정 + 입력 검증

- 매칭 이슈: #4
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
메모를 새로 등록하거나 기존 메모를 수정/삭제하는 입력 화면을 만든다. 상호명·지점명·메모내용 필수 검증으로 빈 값 저장을 막는다. (AC-06 추가·수정, AC-07 필수값 검증)

## 2. 범위
### 구현할 것
- `ui/MemoEditActivity.kt`: 추가 모드(인텐트에 memoId 없음)와 수정 모드(memoId 있음) 처리
- 입력 폼: 상호명·지점명·메모내용·태그 + [저장] + (수정 모드) [삭제]
- 필수 3필드(상호명/지점명/메모내용) 빈 값(trim 후 공백 포함) 검증 → 저장 거부 + 에러 표시
- `res/layout/activity_memo_edit.xml`
### 구현하지 않을 것
- 목록 화면/어댑터 → FEAT-03
- 저장소 본문 → FEAT-02 (Repository 호출만)

## 3. 입력 / 출력
### 입력
- 인텐트 `EXTRA_MEMO_ID`(Long, 없으면 추가 모드), 사용자 입력 4필드
### 출력
- `MemoRepository.add(...)` 또는 `update(memo)` 또는 `delete(memo)` 호출 후 finish()

## 4. 동작 흐름
1. onCreate: `MemoRepository.init(applicationContext)`. 인텐트에서 `memoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)`.
2. memoId != -1L이면 수정 모드: `lifecycleScope.launch { getById(memoId) }`로 기존 값 폼에 채움, [삭제] 버튼 표시.
3. [저장] 클릭:
   - 4필드 읽어 trim. 상호명/지점명/메모내용 중 하나라도 `isBlank()`면 해당 입력에 `error` 설정 + 저장 중단(Toast "필수 항목을 입력하세요").
   - 태그는 trim 후 빈 문자열이면 `null`로 저장(빈 문자열 저장 금지 — AC-11 팝업 처리와 정합).
   - 추가 모드: `add(store, branch, content, tag)`. 수정 모드: 기존 memo를 copy(필드 갱신, `updatedAt = now`) 후 `update`.
   - 완료 후 finish().
4. [삭제] 클릭(수정 모드): `delete(memo)` 후 finish().

## 5. 수정 예상 파일
- 수정: `.../ui/MemoEditActivity.kt` (FEAT-01에서 생성한 스텁을 본문 구현으로 교체. **`EXTRA_MEMO_ID` companion 상수는 FEAT-01 스텁에 이미 있으므로 재정의하지 말고 그대로 사용**)
- 신규: `res/layout/activity_memo_edit.xml`
- 수정: `res/values/strings.xml`(라벨·에러·토스트 문자열)

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
class MemoEditActivity : AppCompatActivity() {
    // companion object { const val EXTRA_MEMO_ID = "memoId" } 는 FEAT-01 스텁에 이미 존재 → 재정의하지 말 것
    private var editing: Memo? = null   // 수정 모드일 때 원본
    private fun onSave() {
        val store = binding.etStore.text.toString().trim()
        val branch = binding.etBranch.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val tagRaw = binding.etTag.text.toString().trim()
        val tag = tagRaw.ifBlank { null }
        if (store.isBlank() || branch.isBlank() || content.isBlank()) { /* error + return */ }
        lifecycleScope.launch {
            if (editing == null) MemoRepository.add(store, branch, content, tag)
            else MemoRepository.update(editing!!.copy(
                storeName = store, branchName = branch, content = content,
                tag = tag, updatedAt = System.currentTimeMillis()))
            finish()
        }
    }
}
```
- 레이아웃: `TextInputLayout`+`TextInputEditText` 4개(상호명/지점명/메모내용/태그), 메모내용은 multiline. 저장/삭제 버튼.
- `EXTRA_MEMO_ID` 상수는 FEAT-01 스텁에 정의되어 있고 FEAT-03도 동일 상수를 참조한다("memoId"). FEAT-04는 재정의 없이 `intent.getLongExtra(EXTRA_MEMO_ID, -1L)`로 읽기만 한다.

## 7. 예외 처리
- 수정 모드인데 getById가 null(이미 삭제됨) → Toast 후 finish().
- 검증 실패 시 DB 접근 없이 즉시 중단.

## 8. 완료 조건
- 빌드 성공.
- 필수 3필드 중 하나라도 빈 값이면 저장되지 않는다(AC-07).
- 추가/수정/삭제가 DB에 반영되어 목록(FEAT-03)에 나타난다(AC-06).
- 태그 미입력 시 DB에 null로 저장된다.

## 9. 테스트 방법
1. [+] → 상호명만 입력 후 저장 → 거부 확인.
2. 4필드 모두 입력(태그 포함) 저장 → 목록 반영.
3. 항목 탭 → 값 채워진 수정 화면 → 메모내용 변경 저장 → 반영 확인.
4. 수정 화면에서 삭제 → 목록에서 제거.
5. 태그 비우고 저장 → 이후 팝업(FEAT-06)에서 태그 영역 미표시(AC-11) 확인.

## 10. 금지 사항
- 목록/어댑터 변경 금지(FEAT-03).
- 태그 빈 문자열을 그대로 저장 금지(null 변환).
- 저장소 구현 변경 금지(Repository API만 사용).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
