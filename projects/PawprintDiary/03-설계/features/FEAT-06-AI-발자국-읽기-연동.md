# FEAT-06 — AI 발자국 읽기 연동 (클라이언트)

- 매칭 이슈: #06
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
오늘의 발자국 저장 직후 `/api/ai/summary`를 호출해 4종 요약을 받아 **기록에 함께 저장**(결정 #4)하고, `/today` 화면에 따뜻하게 표시한다. AC-06·10.

## 의존성 (보정 지시 1·3)
- **FEAT-05(AI 프록시·가드레일·Mock)가 선행 안정화되어 있어야 한다(필수).** 본 FEAT는 `/api/ai/summary`의 확정 입출력/Mock 동작 위에서만 구현한다.
- **usePet 시그니처는 FEAT-03 이후의 `{ pet, loading, refresh }` 기준**으로 사용한다.

## 2. 범위
### 구현할 것
- `lib/aiClient.ts`: `requestSummary(pet, entry)` 클라이언트 fetch 래퍼.
- `components/AISummaryPanel.tsx`: 4종(컨디션/행동해석/관찰포인트/추억문장) + vet_note 표시.
- `app/today/page.tsx`(FEAT-04 산출물) 확장: 저장 → summary 호출 → `entryRepo.update`로 `ai_summary` 합쳐 저장 → 패널 표시. 로딩/에러 상태.
### 구현하지 않을 것
- 서버 라우트/프롬프트/가드레일(FEAT-05).
- 질문하기(FEAT-07), 목록/상세(FEAT-08).

### ★ 보정 지시 5 — 일기 "선저장 보장", AI 실패해도 데이터 유실 금지
- 본 FEAT는 **이미 저장된 entry(FEAT-04에서 ai_summary 없이 저장됨)를 입력으로 받아** `/api/ai/summary`를 호출하고, 응답을 `entryRepo.update`로 **`ai_summary`만 병합**한다.
- AI 호출 순서는 반드시 **`entryRepo.save` 성공 → 그 다음 summary 호출**이다. AI 호출/병합이 실패해도 **이미 저장된 일기 본문은 절대 유실되지 않는다.**
- 실패 시 entry는 `ai_summary` 없는 상태로 남고(데이터 보존), 화면은 재시도 버튼만 제공한다.

### ★ 보정 지시 4 — AI 요청에 Blob 미포함
- `requestSummary`는 pet/entry 직렬화 시 `pet.profile_image`, `entry.photos`(Blob)를 **제외**하고 텍스트/상태 필드만 매핑해 전송한다.

## 3. 입력 / 출력
### 입력
- **이미 저장된** DiaryEntry(entry_id 포함, ai_summary 없음), activePet.
### 출력
- `entries`의 해당 레코드에 `ai_summary`(AISummary) 병합 저장. 화면에 AISummaryPanel 렌더.

## 4. 동작 흐름
1. (FEAT-04 저장 흐름에 이어) **entry 저장 완료가 선행된 뒤** → `setAiLoading(true)`, "AI가 오늘의 발자국을 읽고 있어요 🐾" 표시.
2. `requestSummary(pet, entry)` 호출(POST /api/ai/summary, **사진/프로필이미지 Blob 제외** 텍스트/상태만 전송).
3. 응답 수신 → `ai_summary = { ...response, generated_at: now }` 구성 → `entryRepo.update({ ...entry, ai_summary })`.
4. `AISummaryPanel`에 4종 + vet_note(있을 때만) 표시. `setAiLoading(false)`.
5. 실패 시 에러 카드 + "다시 읽기" 버튼(재호출). **이때 entry 본문은 이미 저장되어 있어 데이터 손실 없음(보정 지시 5).**

## 5. 수정 예상 파일
- `05-개발/lib/aiClient.ts`
- `05-개발/components/AISummaryPanel.tsx`
- `05-개발/app/today/page.tsx`(FEAT-04에서 만든 파일에 AI 연동 추가)

## 6. 데이터 구조 / 함수 / 클래스
```ts
// lib/aiClient.ts
type SummaryResponse = { condition: string; behavior: string; observation: string; memory: string; vet_note: string | null };
export async function requestSummary(pet: Pet, entry: DiaryEntry): Promise<SummaryResponse>;
// ★ 보정 지시 4: pet/entry에서 Blob 필드(pet.profile_image, entry.photos) 제외 후 직렬화.
//   전송 payload 예:
//   pet  → { name, species, breed, age, gender, personality, likes, dislikes, health_notes }
//   entry→ { date, diary_text, appetite, activity, sleep, toilet, unusual_behavior, mood_tags }
//   fetch('/api/ai/summary', { method:'POST', headers:{'content-type':'application/json'}, body: JSON.stringify(...) })

// components/AISummaryPanel.tsx
export function AISummaryPanel(props: { summary: AISummary; loading?: boolean }): JSX.Element;
// 4개 카드(SectionTitle): "오늘의 컨디션", "행동 이야기", "내일의 관찰 포인트", "오늘의 한 줄 추억"
// summary.vet_note 있으면 별도 톤(부드러운 강조) 안내 카드로 표시
```
- 디자인 톤(설계서 4-3): 4종을 카드로 구분, 추억 문장은 살짝 강조(인용체). vet_note는 경고가 아니라 "살펴봐요 🐾" 같은 부드러운 안내 톤(불안 자극 금지).

## 7. 예외 처리
- 네트워크/서버 오류 → "AI 읽기를 불러오지 못했어요. 다시 시도해 주세요" + 재시도 버튼. **entry 본문 저장은 이미 완료되어 데이터 손실 없음(보정 지시 5).**
- 응답 필드 누락 시 누락 항목만 "내용을 준비하지 못했어요"로 표시(앱 크래시 금지).
- AI 응답이 늦어도 사용자가 다른 탭 이동 가능(저장은 이미 끝남).

## 8. 완료 조건
- 저장 후 4종 요약이 화면에 모두 표시되고 `entries.ai_summary`에 병합 저장됨(AC-06).
- AI 호출 실패 시에도 일기 본문이 그대로 보존됨(보정 지시 5).
- 저장된 ai_summary는 새로고침/상세 화면(FEAT-08)에서 재생성 없이 그대로 표시(결정 #4).
- 식욕 저하·활동량 감소·배변 이상 입력 시 vet_note가 표시됨(AC-10).
- AI 요청 payload에 Blob 필드가 포함되지 않음(보정 지시 4).
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. (키 없이 Mock으로) `/today`에서 식욕 "거의 안 먹음" 선택 후 저장 → 4종 요약 + 병원 권장 안내 표시 확인(AC-06·10).
2. IndexedDB `entries` 레코드에 `ai_summary` 저장 확인.
3. 새로고침 후 상세(FEAT-08)에서 같은 요약 텍스트 표시(재생성 안 됨) 확인.
4. 네트워크 차단 상태로 저장 → **일기 본문은 IndexedDB에 남아 있고** 에러 카드 + 재시도 버튼 동작 확인(보정 지시 5).

## 10. 금지 사항
- 서버 프롬프트/가드레일 로직 수정 금지(FEAT-05 소관).
- 조회 시 AI 재생성 구현 금지(저장 시 함께 저장 — 결정 #4).
- AI 호출을 일기 저장보다 먼저 하거나, 실패 시 일기 저장을 롤백하는 구현 금지(보정 지시 5).
- 사진/프로필 이미지 Blob을 AI 요청에 포함 금지(보정 지시 4).
- 설계서 외 라이브러리 추가 금지.
