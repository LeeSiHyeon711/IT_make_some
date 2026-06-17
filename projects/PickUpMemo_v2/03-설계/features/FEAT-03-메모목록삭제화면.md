# FEAT-03 — 메모 목록 + 삭제 화면

- 매칭 이슈: #3
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
등록된 메모 전체를 목록으로 조회하고 삭제할 수 있는 화면을 만든다. MainActivity에서 진입 경로를 연결해 사용자가 메모 관리 기능에 도달하게 한다. (AC-06의 조회·삭제, AC-09 전 단계 진입점)

## 2. 범위
### 구현할 것
- `ui/MemoListActivity.kt`: RecyclerView로 메모 목록(updatedAt DESC) 표시, 항목 삭제, [+] 버튼으로 추가 화면 진입, 항목 탭으로 수정 화면 진입(인텐트만; 수정 화면 본문은 FEAT-04)
- `ui/MemoAdapter.kt`: ListAdapter/RecyclerView.Adapter
- `res/layout/activity_memo_list.xml`, `res/layout/item_memo.xml`
- MainActivity 스텁에 [메모 관리] 버튼 추가 → MemoListActivity 실행 (activity_main.xml에 버튼 1개 추가)
### 구현하지 않을 것
- 추가/수정 폼 본문·검증 → FEAT-04 (여기서는 인텐트로 화면만 띄움)
- 권한 상태 표시(MainActivity의 권한 영역) → FEAT-08

## 3. 입력 / 출력
### 입력
- `MemoRepository.observeAll()` Flow
### 출력
- 화면에 메모 목록 렌더링, 삭제 시 DB 반영(자동으로 목록 갱신)

## 4. 동작 흐름
1. onCreate에서 `MemoRepository.init(applicationContext)` 멱등 호출.
2. `lifecycleScope.launch { repository.observeAll().collect { adapter.submitList(it) } }`로 목록 구독.
3. 항목 삭제 버튼 → `lifecycleScope.launch { MemoRepository.delete(memo) }`.
4. [+] FAB/버튼 → `startActivity(Intent(this, MemoEditActivity::class.java))` (id 미전달 = 추가 모드).
5. 항목 본문 탭 → `Intent(this, MemoEditActivity::class.java).putExtra(MemoEditActivity.EXTRA_MEMO_ID, memo.id)` (수정 모드).
6. 빈 목록이면 "등록된 메모가 없습니다" 안내 TextView 표시.

## 5. 수정 예상 파일
- 신규: `.../ui/MemoListActivity.kt`, `.../ui/MemoAdapter.kt`
- 신규: `res/layout/activity_memo_list.xml`, `res/layout/item_memo.xml`
- 수정: `MainActivity.kt`(버튼 클릭 → MemoListActivity), `res/layout/activity_main.xml`(버튼 추가), `res/values/strings.xml`(문자열 추가)

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
class MemoAdapter(
    private val onClick: (Memo) -> Unit,
    private val onDelete: (Memo) -> Unit
) : ListAdapter<Memo, MemoAdapter.VH>(DIFF) { ... }
```
- `item_memo.xml`: 제목 TextView(`"$storeName $branchName"`, 굵게), 미리보기 TextView(content, 1~2줄 ellipsize), 태그 TextView(있을 때만 VISIBLE), 삭제 버튼(ImageButton/Button).
- DiffUtil: areItemsTheSame = id 비교, areContentsTheSame = data class equals.
- 인텐트 키 상수: **FEAT-01 스텁에서 이미 정의된** `MemoEditActivity.EXTRA_MEMO_ID`(="memoId")를 참조해 사용한다. 이 화면에서 새로 정의하지 않는다. (수정 모드 진입 시 `putExtra(MemoEditActivity.EXTRA_MEMO_ID, memo.id)`.)

## 7. 예외 처리
- 삭제 후 Flow가 자동 갱신하므로 수동 새로고침 불필요.
- 잘못된 클릭 중복 방지는 MVP 범위 밖(무시).

## 8. 완료 조건
- 빌드 성공.
- 메모가 목록에 updatedAt DESC로 표시되고, 삭제 시 즉시 목록에서 사라진다.
- [+]·항목 탭이 MemoEditActivity를 띄운다(추가/수정 모드 인텐트 분기).
- MainActivity에서 [메모 관리]로 목록에 진입할 수 있다.

## 9. 테스트 방법
1. 앱 실행 → [메모 관리] → 빈 목록 안내 확인.
2. (FEAT-04 완료 후) 메모 추가 → 목록에 표시 확인.
3. 항목 삭제 버튼 → 목록에서 제거 확인.

## 10. 금지 사항
- 추가/수정 폼 검증 로직 구현 금지(FEAT-04).
- 권한 상태 UI 추가 금지(FEAT-08).
- 태그가 빈 값일 때 "태그 없음" 같은 문구 표시 금지(빈 값이면 태그 뷰 GONE).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
