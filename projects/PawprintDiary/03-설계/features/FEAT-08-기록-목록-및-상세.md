# FEAT-08 — 기록 목록 및 상세 보기

- 매칭 이슈: #08
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
저장된 발자국 기록을 날짜 역순 목록으로 보여주고, 선택한 날의 입력 내용과 AI 요약(저장된 것 재사용)을 상세 화면에 함께 표시한다. AC-13·14.

## 의존성 (보정 지시 1·8)
- **usePet 시그니처는 FEAT-03 이후의 `{ pet, loading, refresh }` 기준**으로 사용한다.
- **1마리 MVP**: `activePet` 하나의 기록만 `listByPet(activePet.pet_id)`로 표시. 반려동물 전환/선택/필터 UI 없음.

## 2. 범위
### 구현할 것
- `app/entries/page.tsx`: `entryRepo.listByPet(activePet.pet_id)` 목록(시간역순). `EntryCard` 미리보기.
- `components/EntryCard.tsx`: 날짜·대표 기분태그·요약 1줄(ai_summary.condition 앞부분)·대표 사진 썸네일.
- `app/entries/[entryId]/page.tsx`: 상세 — 입력 전체(상태 4종·특이행동·자유일기·기분태그·사진 BlobImage) + `AISummaryPanel`(FEAT-06)로 AI 요약 4종 표시. 기록 없을 때/항목 없을 때 빈 상태.
- (선택) 상세에서 삭제 버튼 → `entryRepo.remove` 후 목록 복귀.
### 구현하지 않을 것
- AI 재생성(저장된 ai_summary 사용 — 결정 #4).
- 반려동물별 필터 UI(MVP 1마리 제외 — 결정 #6). 날짜 탐색만.
- 사진첩/갤러리(2차 확장).

### ★ 보정 지시 6 — 상세는 "저장된 ai_summary 재사용", AI 재호출 금지
- 상세 조회 시 **AI를 절대 재호출하지 않는다.** `entry.ai_summary`가 있으면 그대로 `AISummaryPanel`에 표시한다.
- `ai_summary`가 **없는 경우**(예: 과거 작성 시 AI 실패로 미저장)에는 "AI 읽기가 아직 없어요" 안내를 보여주고, 선택적으로 "지금 읽기" 재시도 버튼(FEAT-06의 requestSummary 재호출)을 제공할 수 있다(선택 구현). 단, ai_summary가 **있는** 항목에 대해서는 재호출/재생성 금지.

### ★ 보정 지시 7 — 목록 정렬 기준 명시
- 목록은 `entryRepo.listByPet`가 반환하는 순서를 그대로 사용한다. 정렬 기준은 **date desc, 동일 날짜는 created_at desc**(FEAT-02에서 확정). 화면에서 임의 재정렬하지 않는다.

## 3. 입력 / 출력
### 입력
- activePet, `entries` 스토어.
### 출력
- 목록 화면, 상세 화면(읽기). 삭제 시 레코드 제거.

## 4. 동작 흐름
1. `/entries` 진입 → activePet 확인 → `listByPet`로 목록 로드 → **date desc·created_at desc** 순서로 EntryCard 렌더(보정 지시 7). 0건이면 EmptyState("아직 발자국이 없어요 🐾" + /today 링크).
2. 카드 클릭 → `/entries/[entryId]` → `entryRepo.get(entryId)` → 입력 전체 + AISummaryPanel 표시(**저장된 ai_summary 재사용, 재호출 없음** — 보정 지시 6).
3. ai_summary 없는 과거 기록은 "AI 읽기가 아직 없어요" 안내 + (선택)"지금 읽기" 버튼으로 FEAT-06 재호출 가능(선택 구현).
4. 삭제 시 확인 후 remove → `/entries` 복귀.

## 5. 수정 예상 파일
- `05-개발/app/entries/page.tsx`
- `05-개발/app/entries/[entryId]/page.tsx`
- `05-개발/components/EntryCard.tsx`
- `05-개발/lib/format.ts`(날짜/미리보기 포맷 유틸 — 없으면 생성)

## 6. 데이터 구조 / 함수 / 클래스
```ts
// lib/format.ts
export function formatDateK(isoDate: string): string;  // 'yyyy-MM-dd' → '2026년 6월 22일 (일)'
export function previewText(s?: string, n?: number): string; // 앞 n글자 + …

// components/EntryCard.tsx
export function EntryCard(props: { entry: DiaryEntry; onClick: () => void }): JSX.Element;
// 표시: formatDateK(entry.date) · mood_tags 1~2 Tag · previewText(ai_summary?.condition ?? diary_text) · entry.photos[0] BlobImage(있으면)
```
- 디자인 톤(설계서 4-3): 목록은 카드 세로 나열, 날짜는 따뜻한 헤더. 썸네일은 둥근 모서리. 상세는 입력/AI 영역을 시각적으로 구분.

## 7. 예외 처리
- activePet 없으면 프로필 등록 유도.
- 존재하지 않는 entryId 접근 → "기록을 찾을 수 없어요" + 목록 복귀.
- 사진/요약 없는 항목도 깨지지 않게 조건부 렌더.

## 8. 완료 조건
- 날짜별 기록 목록이 **date desc·created_at desc**로 표시되고, 카드 클릭으로 상세 진입(AC-13 · 보정 지시 7).
- 상세에 입력 내용 + AI 요약 4종이 함께 표시됨(AC-14).
- 저장된 ai_summary를 **재생성/재호출 없이** 표시(결정 #4 · 보정 지시 6).
- 기록 0건일 때 빈 상태 안내 표시.
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. 기록 2건 이상 생성 후 `/entries` → 최신이 위로 오는 역순 목록 확인(같은 날짜면 created_at 역순)(AC-13).
2. 카드 클릭 → 상세에서 입력 전체 + AI 4종 표시 확인(AC-14).
3. 새로고침 후 동일 요약 텍스트(재생성/재호출 없음) 확인(보정 지시 6).
4. 기록 전부 삭제 후 빈 상태 안내 확인.

## 10. 금지 사항
- 조회 시 AI 재호출/재생성 금지(결정 #4 · 보정 지시 6. 단, ai_summary 자체가 없는 항목의 선택적 "지금 읽기"는 예외).
- 반려동물 필터/전환 UI 추가 금지(결정 #6 · 보정 지시 8).
- 목록을 date/created_at 외 기준으로 재정렬 금지(보정 지시 7).
- 작성 폼 로직 중복 구현 금지(FEAT-04 소관).
- 설계서 외 라이브러리 추가 금지.
