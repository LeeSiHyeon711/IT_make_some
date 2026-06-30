# FEAT-12 — 무지개연결 AI 엔드포인트

- 매칭 이슈: #12
- 작성일: 2026-06-29
- 상위 설계서: `03-설계/무지개연결-설계서.md`

## 1. 목적
무지개연결 회고 응답을 생성하는 **서버 Route Handler `/api/ai/rainbow`** 와 클라이언트 래퍼 `requestRainbow()`를 만든다. 첫 회고(intro)와 채팅(chat)을 `intent`로 단일 경로에서 분기하고, 키 없으면 결정적 Mock·실패 시 graceful Mock 폴백을 보장한다. 모든 응답 경로의 마지막 단계로 `sanitizeRainbow()`를 적용한다. AC-R04·R05·R06·R09·R11.

## 의존성
- **FEAT-10**(대화 분리·plumbing 일부)·**FEAT-11**(`rainbowPrompt`·`sanitizeRainbow`)가 선행 안정화되어 있어야 한다.
- 기존 `/api/ai/ask` 구조·`@anthropic-ai/sdk`·모델 기본값 정책을 그대로 승계한다.

## 2. 범위
### 구현할 것
- `app/api/ai/rainbow/route.ts`(POST):
  - 입력 `{ pet, intent:'intro'|'chat', question?, records, history? }` (Blob 미포함 — 보정 지시 R3).
  - `intent`별로 `buildRainbowIntroPrompt` / `buildRainbowChatPrompt` 선택.
  - 키 있으면 Claude 호출(모델 기본 `claude-opus-4-8`), 없으면 결정적 Mock. 실패 시 Mock 폴백.
  - 모든 경로 끝에 `sanitizeRainbow()` 적용 → `{ answer }` 반환.
- `lib/aiClient.ts` 확장: `requestRainbow(pet, intent, question?, records, history?)` — Blob 제외 직렬화 후 POST.
### 구현하지 않을 것
- 프롬프트/가드레일 본체(FEAT-11).
- 화면/대화 저장 호출(FEAT-14가 호출·저장).
- 기존 `/api/ai/{summary,ask}` 수정.

## 3. 입력 / 출력
### 입력(JSON, Blob 미포함)
```jsonc
{
  "pet": { "name","species","breed","age","gender","personality","likes","dislikes","health_notes" },
  "intent": "intro" | "chat",
  "question": "...",            // chat일 때만
  "records": [ { "date","mood_tags":[],"diary_text":"...","condition":"...","photoCount":0 } ], // 최대 20, 샘플
  "history": [ { "q":"...","a":"..." } ]  // 선택, chat 맥락
}
```
### 출력
```jsonc
{ "answer": "..." }
```

## 4. 동작 흐름
1. POST 수신 → body 파싱·유효성(필수: `pet.name`, `pet.species`, `intent`; chat이면 `question`). 누락 시 400.
2. `intent==='intro'` → `buildRainbowIntroPrompt(pet, records)`; `'chat'` → `buildRainbowChatPrompt(pet, question, records, history)`.
3. `ANTHROPIC_API_KEY` 있으면 `messages.create`(model=env `ANTHROPIC_MODEL` 기본 `claude-opus-4-8`, max_tokens intro 700/chat 700, temperature 0.6). 없으면 Mock 분기.
4. 응답 텍스트 추출 → **`sanitizeRainbow()` 적용**(실모델·Mock·폴백 모든 경로 공통 마지막 단계).
5. `{ answer }` 반환. 호출 실패/타임아웃 → Mock 폴백(graceful).

## 5. 수정 예상 파일
- `05-개발/app/api/ai/rainbow/route.ts`(신규)
- `05-개발/lib/aiClient.ts`(`requestRainbow` 추가)

## 6. 데이터 구조 / 함수 / 컴포넌트
```ts
// app/api/ai/rainbow/route.ts
const model = process.env.ANTHROPIC_MODEL || 'claude-opus-4-8';
interface RainbowRequest {
  pet: PetLike;
  intent: 'intro' | 'chat';
  question?: string;
  records: RainbowRecord[];
  history?: { q: string; a: string }[];
}
// Mock: intent별 결정적 회고 문구(완충 어조), records 수가 적으면 "남겨진 기록이 많지는 않지만…" 분기.
//       반드시 sanitizeRainbow() 통과.
function buildMockRainbow(req: RainbowRequest): string;
export async function POST(request: Request): Promise<Response>;

// lib/aiClient.ts (확장) — Blob 제외 직렬화(보정 지시 R3)
export async function requestRainbow(
  pet: Pet,
  intent: 'intro' | 'chat',
  question: string | undefined,
  records: DiaryEntry[],
  history?: { q: string; a: string }[],
): Promise<{ answer: string }>;
// records 매핑: { date, mood_tags, diary_text(절단), condition: ai_summary?.condition, photoCount: photos.length }
// pet 매핑: profile_image(Blob) 제외, 텍스트 필드만
```
- 컨텍스트 샘플링(설계서 7-2): records가 20 초과면 처음·중간·최근이 고루 들어가도록 분산 추출(클라이언트 `requestRainbow` 또는 호출 측 FEAT-14에서 준비). 본 FEAT는 받은 records를 그대로 프롬프트에 사용.

## 7. 예외 처리
- 필수 필드 누락 → 400 + `{ error }`(한국어).
- Anthropic 호출 실패/타임아웃 → 500 대신 Mock 폴백 반환 + 서버 로그(사용자 흐름 유지).
- `sanitizeRainbow` 단계는 모든 경로에서 생략 금지.
- 키/`NEXT_PUBLIC_*` 클라이언트 노출 금지(기존 정책 승계).

## 8. 완료 조건
- `/api/ai/rainbow`가 intro/chat 입력에 대해 `{ answer }`로 정상 응답.
- 키 없을 때 Mock 동일 입력 → 동일 출력(AC-R09), records 적을 때 조심스러운 톤(AC-R06).
- 모든 응답이 `sanitizeRainbow` 통과 → 금지 문장 미출현(AC-R07, FEAT-11과 연계).
- AI 요청 payload에 Blob 미포함(AC-R11).
- 클라이언트 번들에 키 미노출.
- `npm run build` 타입/빌드 에러 0(AC-R12).

## 9. QA 체크리스트
- [ ] (키 없음) `curl POST /api/ai/rainbow {intent:'intro', records:[…]}` → 산문형 회고, 항목 나열 아님
- [ ] (키 없음) `{intent:'chat', question:'가장 자주 기록된 행복한 순간은?'}` → 기록 인용형 답변
- [ ] records 0~1개 → "남겨진 기록이 많지는 않지만…" 톤
- [ ] 동일 입력 2회 → 동일 Mock 출력(결정성)
- [ ] 금지 문장(말대리·영적·사망원인) 미출현
- [ ] payload에 Blob/이미지 없음, 키 클라이언트 미노출
- [ ] 호출 실패 시 Mock 폴백으로 흐름 유지
- [ ] `npm run build` 통과

## 10. 금지 사항
- 프롬프트/가드레일 본체 수정 금지(FEAT-11).
- 기존 `/api/ai/{summary,ask}` 수정 금지.
- 사진(Blob) AI 전송/수신 금지(보정 지시 R3).
- `sanitizeRainbow` 생략 금지(모든 경로 공통).
- 모델 기본값을 `claude-opus-4-8` 외로 하드코딩 금지(env 미설정 시 이 값).
- 설계서 외 라이브러리 추가 금지.
