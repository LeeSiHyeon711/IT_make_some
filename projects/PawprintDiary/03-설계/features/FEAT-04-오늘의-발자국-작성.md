# FEAT-04 — 오늘의 발자국 작성 (저장까지)

- 매칭 이슈: #04
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
보호자가 오늘 반려동물 상태를 5분 안에 기록·저장하는 입력 화면을 만든다. AI 연동(FEAT-06) 이전까지의 **순수 입력·저장**을 책임진다. AC-03·04·05.

## 의존성 (보정 지시 1·8)
- **usePet 시그니처는 FEAT-03 이후의 `{ pet, loading, refresh }` 기준**으로 사용한다(FEAT-01 임시 골격 아님).
- **1마리 MVP**: `activePet` 하나만 기준으로 동작. 반려동물 전환/선택 UI 없음.

## 2. 범위
### 구현할 것
- `app/today/page.tsx`: 날짜(기본 오늘)·식욕·활동량·수면·배변·특이행동·자유일기·기분태그·사진 입력 폼.
- `components/DiaryForm.tsx`(상태 관리/검증), `components/StepSelector.tsx`(단계형 4종 공용), `components/MoodTagPicker.tsx`(복수 선택).
- `PhotoUpload`(multi 모드, max 4) 재사용(FEAT-03 제공).
- 저장: `entryRepo.save(...)` 호출. **이 FEAT에서는 ai_summary 없이 일기 저장까지만**(AI 연동은 FEAT-06이 이 화면에 얹는다).
- 저장 직후 UI 자리(placeholder): "AI가 오늘의 발자국을 읽고 있어요" 영역 — 실제 AI 호출/표시는 FEAT-06.
### 구현하지 않을 것
- AI 호출·요약 표시(FEAT-05·06).
- 기록 목록/상세(FEAT-08).
- 프로필 폼(FEAT-03).

### ★ 보정 지시 5 — 일기 저장과 AI 요약 저장 흐름 "분리"
- 본 FEAT는 **`ai_summary` 필드를 절대 포함하지 않고** `entryRepo.save`로 일기 본문(상태·태그·사진·자유일기)만 저장한다.
- AI 요약 생성·병합은 **전적으로 FEAT-06**의 책임이다(저장된 entry를 받아 `/api/ai/summary` 호출 → `entryRepo.update`로 `ai_summary` 병합).
- 따라서 본 FEAT의 저장은 **AI와 무관하게 항상 성공해야 하며**, 작성한 일기 데이터가 AI 단계 때문에 유실되는 일이 없어야 한다(선저장 보장의 출발점).

## 3. 입력 / 출력
### 입력
- activePet(없으면 `/profile/new` 유도), 사용자 폼 입력.
### 출력
- `entries` 스토어에 DiaryEntry 저장(pet_id=activePet.pet_id, **ai_summary 미포함**). `mood_tags`/`photos`는 빈 배열 허용.

## 4. 동작 흐름
1. `/today` 진입 → `usePet()`로 activePet 확인(없으면 안내 후 `/profile/new`).
2. 폼 렌더: 날짜 기본값 today(`format(new Date(),'yyyy-MM-dd')`). 상태 4종은 StepSelector, 기분은 MoodTagPicker, 사진은 PhotoUpload multi.
3. 모든 항목 선택사항이나 **날짜는 필수** → 날짜만 있어도 저장 가능(AC-03).
4. 저장 클릭 → `entryRepo.save({ pet_id, date, ... , mood_tags, photos })`(ai_summary 미포함) → 저장된 entry_id 보관 → (FEAT-06이 여기서 AI 호출을 이어받음) → 현재는 "저장 완료" 상태 표시.

## 5. 수정 예상 파일
- `05-개발/app/today/page.tsx`
- `05-개발/components/DiaryForm.tsx`, `components/StepSelector.tsx`, `components/MoodTagPicker.tsx`

## 6. 데이터 구조 / 함수 / 클래스
```tsx
// components/StepSelector.tsx — 식욕/활동량/수면/배변 공용 단계형 선택
export function StepSelector<T extends string>(props: {
  label: string;
  options: readonly T[];
  value?: T;
  onChange: (v?: T) => void;   // 같은 값 재클릭 시 해제(undefined) 허용
}): JSX.Element;

// 옵션 상수(타입은 FEAT-02 유니온과 일치)
const APPETITE = ['잘 먹음','보통','적게 먹음','거의 안 먹음'] as const;
const ACTIVITY = ['매우 활발함','보통','조용함','거의 움직이지 않음'] as const;
const SLEEP    = ['평소보다 많이 잠','보통','평소보다 적게 잠'] as const;
const TOILET   = ['정상','묽음','굳음','없음','이상 있음'] as const;

// components/MoodTagPicker.tsx — 복수 선택
const MOOD_TAGS = ['활발함','차분함','예민함','피곤해 보임','애교 많음','식욕 없어 보임'] as const;
export function MoodTagPicker(props: { value: string[]; onChange: (v: string[]) => void }): JSX.Element;

// components/DiaryForm.tsx
type DiaryFormValues = Omit<DiaryEntry,'entry_id'|'pet_id'|'created_at'|'ai_summary'>; // ai_summary 제외(보정 지시 5)
export function DiaryForm(props: {
  initial?: Partial<DiaryFormValues>;
  onSubmit: (values: DiaryFormValues) => Promise<void>;  // page가 entryRepo.save 연결
  submitting?: boolean;
}): JSX.Element;
```
- 디자인 톤(설계서 4-3): 한 화면 스크롤로 5분 내 완료(AC-04). 섹션 구분(SectionTitle), StepSelector는 가로 칩 형태로 탭 한 번에 선택. 카피: "오늘의 발자국을 남겨볼까요? 🐾".

## 7. 예외 처리
- activePet 없으면 폼 대신 EmptyState + "먼저 우리 아이를 소개해 주세요" → `/profile/new`.
- 미래 날짜 선택 차단 또는 경고(기본은 오늘, 과거 선택 허용).
- 사진 4장 초과 시 추가 비활성 + 안내.
- 저장 실패(IndexedDB 오류) 시 한국어 토스트 + 폼 값 유지.

## 8. 완료 조건
- 날짜만 두고 저장 가능(AC-03).
- 전체 항목 입력이 한 화면 스크롤·탭 선택으로 5분 내 가능한 구조(AC-04).
- 저장한 기록이 `entries`에 **ai_summary 없이** 남고 새로고침 후에도 유지(AC-05).
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. `/today`에서 아무것도 안 고르고 저장 → IndexedDB `entries`에 레코드 생성 확인(AC-03).
2. 상태 4종·기분태그·사진·일기 입력 후 저장 → 모든 값 저장 확인(ai_summary 필드는 아직 없음).
3. 새로고침 후 (FEAT-08 목록 또는 IndexedDB)에서 기록 유지 확인(AC-05).
4. StepSelector 같은 값 재클릭 시 해제되는지 확인.

## 10. 금지 사항
- AI 호출/요약 표시 구현 금지(FEAT-06이 담당).
- `ai_summary` 필드를 본 FEAT에서 채워 저장 금지(보정 지시 5 — FEAT-06 소관).
- 목록/상세 화면 구현 금지(FEAT-08).
- 다중 반려동물 전환/선택 UI 추가 금지(보정 지시 8).
- 사진 원본 무압축 저장 금지(PhotoUpload가 resize 처리).
- 설계서 외 라이브러리 추가 금지.
