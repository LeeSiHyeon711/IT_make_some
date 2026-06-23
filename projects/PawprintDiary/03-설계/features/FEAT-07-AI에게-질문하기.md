# FEAT-07 — AI에게 질문하기

- 매칭 이슈: #07
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
보호자가 반려동물에 대한 자유 질문을 입력하면, 프로필 + 최근 5개 기록(결정 #5)을 맥락으로 한 맞춤 응답을 받고, 질문-응답을 저장해 다시 볼 수 있게 한다. AC-11·12.

## 의존성 (보정 지시 1·3·8)
- **FEAT-05(AI 프록시·가드레일·Mock)가 선행 안정화되어 있어야 한다(필수).** `/api/ai/ask`의 확정 입출력/Mock 위에서만 구현한다.
- **usePet 시그니처는 FEAT-03 이후의 `{ pet, loading, refresh }` 기준**으로 사용한다.
- **1마리 MVP**: `activePet` 하나만 기준으로 질문/대화. 반려동물 전환/선택/필터 UI 없음.

## 2. 범위
### 구현할 것
- `app/ask/page.tsx`: 질문 입력 + 응답 표시 + 과거 대화 목록.
- `lib/aiClient.ts` 확장: `requestAsk(pet, question, recent)`.
- 응답 후 `conversationRepo.add(...)` 저장, 화면 하단에 `conversationRepo.listByPet`으로 Q&A 목록(최신순) 표시.
### 구현하지 않을 것
- 서버 라우트/가드레일(FEAT-05).
- 반려동물 사칭 대화(영구 제외) — 일반 Q&A만.

### ★ 보정 지시 4 — AI 요청에 Blob 미포함
- `requestAsk`는 pet/recent 직렬화 시 Blob 필드(`pet.profile_image`, `entry.photos`)를 **제외**하고 텍스트/상태 필드만 매핑해 전송한다(아래 6장).

## 3. 입력 / 출력
### 입력
- activePet, 사용자 질문 텍스트, `entryRepo.recentByPet(pet_id, 5)`.
### 출력
- `/api/ai/ask` 응답 표시 + `conversations` 스토어에 AIConversation 저장.

## 4. 동작 흐름
1. `/ask` 진입 → activePet 확인(없으면 `/profile/new` 유도) → 과거 대화 목록 로드(`listByPet`).
2. 사용자가 질문 입력·전송 → `recentByPet(pet_id,5)` 조회 → `requestAsk(pet, question, recent)`.
3. 응답 표시(완충 어조). `conversationRepo.add({ pet_id, user_question, ai_response })`.
4. 대화 목록 갱신(방금 Q&A가 최상단).

## 5. 수정 예상 파일
- `05-개발/app/ask/page.tsx`
- `05-개발/lib/aiClient.ts`(requestAsk 추가)

## 6. 데이터 구조 / 함수 / 클래스
```ts
// lib/aiClient.ts (확장)
export async function requestAsk(pet: Pet, question: string, recent: DiaryEntry[]): Promise<{ answer: string }>;
// ★ 보정 지시 4: recent는 Blob(entry.photos) 제외 후
//   { date, appetite, activity, sleep, toilet, unusual_behavior, mood_tags, condition: ai_summary?.condition } 형태로 매핑.
//   pet도 profile_image(Blob) 제외하고 텍스트 필드만 전송.

// app/ask/page.tsx 내부 상태
// questions 목록은 AIConversation[] (timestamp desc)
// 전송 중 Spinner, 빈 입력 전송 차단
```
- 디자인 톤(설계서 4-3): 채팅이 아닌 "질문 카드 + 답변 카드" 형식. 예시 질문 칩(예: "요즘 자꾸 문 앞에서 긁어요") 제공해 첫 사용 유도. 카피: "우리 아이에 대해 무엇이든 물어보세요 🐾".

## 7. 예외 처리
- 빈 질문 전송 차단.
- 서버/네트워크 오류 → "답변을 불러오지 못했어요" + 재시도. 질문 텍스트 유지.
- activePet 없으면 EmptyState로 프로필 등록 유도.
- 최근 기록이 0개여도 동작(프로필만으로 응답).

## 8. 완료 조건
- 질문 전송 시 프로필+최근 기록 맥락이 반영된 응답 표시(AC-11).
- 질문-응답이 `conversations`에 저장되고, 재진입/새로고침 후 목록에서 다시 보임(AC-12).
- 응답이 가드레일 어조(서버 보장)로 표시됨.
- AI 요청 payload에 Blob 필드가 포함되지 않음(보정 지시 4).
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. 기록 2~3건 생성 후 `/ask`에서 "요즘 활동량이 줄어든 것 같아요" 질문 → 최근 기록을 언급한 응답 확인(AC-11).
2. 새로고침/재진입 후 이전 Q&A가 목록에 유지되는지 확인(AC-12).
3. IndexedDB `conversations`에 레코드 저장 확인.
4. 네트워크 차단 시 에러+재시도 동작 확인.

## 10. 금지 사항
- 반려동물 사칭 대화 형식 구현 금지(영구 제외).
- 서버 프롬프트/가드레일 수정 금지(FEAT-05).
- N(컨텍스트 개수)을 5 외 임의 변경 금지(결정 #5).
- 다중 반려동물 전환/선택/필터 UI 추가 금지(보정 지시 8).
- 사진/프로필 이미지 Blob을 AI 요청에 포함 금지(보정 지시 4).
- 설계서 외 라이브러리 추가 금지.
