# FEAT-05 — AI 프록시 + 가드레일 (서버)

- 매칭 이슈: #05
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
AI 키를 클라이언트에 노출하지 않고(결정 #3) Claude를 호출하는 **서버 Route Handler**와, AC-07~10을 코드로 보장하는 **가드레일(시스템 프롬프트 + 후처리 sanitize + 조건부 병원 권장)**을 만든다. 이 프로젝트에서 가장 품질 민감한 핵심 로직이다. AC-06·07·08·09·10·11.

## ★ 보정 지시 3 — 본 FEAT는 FEAT-06/07보다 "먼저 안정화"되어야 한다
- 이 AI Route / prompt / guardrails / Mock은 **FEAT-06(발자국 읽기 연동)·FEAT-07(질문하기)의 기반**이다. 두 클라이언트 FEAT는 본 FEAT가 안정 동작(엔드포인트 입출력·Mock·sanitize 확정)한 뒤 구현한다.
- **`sanitize()`는 실제 Claude 응답·Mock 응답·파싱 폴백 응답 "모두"에 마지막 단계로 적용**한다(어느 경로든 가드레일이 누락되지 않게).
- **클라이언트에 `ANTHROPIC_API_KEY` 또는 `NEXT_PUBLIC_*` 형태의 키를 절대 노출하지 않는다.** 키는 서버 env에만 존재한다.

## 2. 범위
### 구현할 것
- `app/api/ai/summary/route.ts` (POST): 일기 1건 → 4종 요약 JSON(+조건부 vet_note).
- `app/api/ai/ask/route.ts` (POST): 질문 + 프로필 + 최근 5개 기록 → 맞춤 응답.
- `lib/prompt.ts`: 시스템 프롬프트 + 유저 프롬프트 빌더(종 분기=결정 #8, N=5 컨텍스트=결정 #5).
- `lib/guardrails.ts`: `sanitize()`(단정·말대리·의료 표현 제거/완화) + `needsVetNote()`(상태값 판단) + `buildVetNote()`.
- **Mock 모드**(설계서 2-1): `ANTHROPIC_API_KEY` 미설정 시 결정적 Mock 응답 반환(가드레일 형식 준수). **실제 키 없이도 전체 흐름이 동작해야 한다(필수).**
### 구현하지 않을 것
- 클라이언트 호출/표시(FEAT-06·07).
- 데이터 저장(클라이언트 IndexedDB 책임 — 서버는 무상태).

## 3. 입력 / 출력
### `/api/ai/summary` 입력(JSON, 사진 제외 텍스트/상태만)
```jsonc
{
  "pet": { "name","species","breed","age","gender","personality","likes","dislikes","health_notes" },
  "entry": { "date","diary_text","appetite","activity","sleep","toilet","unusual_behavior","mood_tags":[] }
}
```
> ★ 보정 지시 4 — 입력에는 사진/프로필 이미지 등 **Blob을 절대 포함하지 않는다**(텍스트/상태 필드만). 서버는 Blob을 받지도, 기대하지도 않는다.
### `/api/ai/summary` 출력(JSON)
```jsonc
{ "condition":"...", "behavior":"...", "observation":"...", "memory":"...", "vet_note":"...|null" }
```
### `/api/ai/ask` 입력 / 출력
```jsonc
// 입력 (Blob 미포함)
{ "pet": {…동일…}, "question":"...", "recent":[ { "date","appetite","activity","sleep","toilet","unusual_behavior","mood_tags":[],"condition?":"..." } ] }  // 최근 5개
// 출력
{ "answer":"..." }
```

## 4. 동작 흐름
1. POST 수신 → body 파싱·유효성 검사(필수 필드 없으면 400).
2. `buildSummaryPrompt(pet, entry)` 또는 `buildAskPrompt(pet, question, recent)`로 system/user 구성.
3. `ANTHROPIC_API_KEY` 있으면 `@anthropic-ai/sdk`로 `messages.create` 호출(모델=env `ANTHROPIC_MODEL` 기본 **`claude-opus-4-8`**, `max_tokens` summary 900/ask 700, `temperature` 0.6). 없으면 Mock 생성기로 분기.
4. summary는 모델에 **JSON only**로 응답하도록 지시 → 파싱. 파싱 실패 시 1회 재시도, 그래도 실패면 텍스트를 4필드로 분배하는 폴백.
5. `sanitize()`로 각 출력 텍스트 후처리(단정→완충 치환, 말대리·의료 진단 문장 제거). **실모델·Mock·폴백 모든 경로의 마지막 단계로 공통 적용(보정 지시 3).**
6. summary: `needsVetNote(entry)`가 true면 `vet_note`를 강제 세팅(모델 응답에 병원 언급이 이미 있으면 그대로, 없으면 `buildVetNote()` 주입) → **AC-10 보장**. false면 null.
7. JSON 응답 반환.

## 5. 수정 예상 파일
- `05-개발/app/api/ai/summary/route.ts`
- `05-개발/app/api/ai/ask/route.ts`
- `05-개발/lib/prompt.ts`
- `05-개발/lib/guardrails.ts`

## 6. 데이터 구조 / 함수 / 클래스
```ts
// lib/prompt.ts
export const SYSTEM_PROMPT = `
당신은 반려동물 보호자를 돕는 따뜻하고 신중한 일기 도우미입니다. 다음 규칙을 반드시 지키세요.
1) 단정 금지: "확실히/분명히/반드시/100%/틀림없이" 같은 단정 표현을 쓰지 말고,
   "기록을 보면", "가능성이 있어요", "단정할 수는 없지만" 같은 완충 표현을 사용합니다.
2) 반려동물 말 대리 금지: "이 아이가 ~라고 말하고 있어요", "지금 행복하대요"처럼
   반려동물의 말을 대신 전하는 표현을 쓰지 않습니다.
3) 단정적 위로 금지: "100% 행복했어요", "당신을 용서했어요" 같은 표현을 쓰지 않습니다.
4) 의료 진단 금지: 어떤 경우에도 병명·진단·처방 형태로 말하지 않습니다.
5) 보호자 죄책감 자극 금지: "더 잘 돌봐야 했어요" 같은 표현 대신 돌봄의 흔적을 따뜻하게 정리합니다.
6) 종 범위: 강아지·고양이는 종 특성을 반영해 해석하되, '기타' 종은 일반적인 관찰 중심으로만 설명하고
   종 특화 해석을 시도하지 않습니다.
모든 답변은 한국어로, 부드럽고 따뜻한 어조로 작성합니다.`;

// summary: JSON only 응답 요청. species가 '기타'면 행동 해석을 일반 관찰로 제한하도록 지시 문구 분기.
export function buildSummaryPrompt(pet: PetLike, entry: EntryLike): { system: string; user: string };
// ask: 프로필 + 최근 5개 기록을 맥락으로 제공, 질문에 답하도록.
export function buildAskPrompt(pet: PetLike, question: string, recent: EntryLike[]): { system: string; user: string };

// lib/guardrails.ts
// 단정 표현 → 완충 표현 치환 맵
const BANNED_REPLACE: Array<[RegExp, string]> = [
  [/확실히/g, '아마'], [/분명히/g, '아마'], [/틀림없이/g, '어쩌면'],
  [/100\s*%/g, '많이'], [/반드시/g, '되도록'],
];
// 의료 진단/말대리 표현이 든 문장은 제거(문장 단위 split 후 필터)
const STRIP_SENTENCE = [/진단/, /처방/, /말하고\s*있어요/, /행복하대/, /용서했/, /용서해/];
export function sanitize(text: string): string;            // 위 규칙 적용 — 모든 응답 경로의 마지막 단계
export function needsVetNote(entry: EntryLike): boolean;    // 아래 기준
export function buildVetNote(): string;                     // 고정 권장 문구 반환

// needsVetNote 기준(AC-10): 아래 중 하나라도 해당하면 true
//  appetite ∈ {'적게 먹음','거의 안 먹음'}
//  activity ∈ {'조용함','거의 움직이지 않음'}
//  toilet   ∈ {'묽음','굳음','이상 있음'}
// buildVetNote 예: "최근 컨디션이 평소와 달라 보이면, 가까운 시일 내 수의사 선생님과 상담해 보시길 부드럽게 권해드려요."

// route.ts 공통 호출부
import Anthropic from '@anthropic-ai/sdk';
const model = process.env.ANTHROPIC_MODEL || 'claude-opus-4-8';   // 기본값 고정(보정 지시 2)
// const res = await client.messages.create({ model, max_tokens, temperature:0.6, system, messages:[{role:'user',content:user}] });
// const text = res.content.find(c => c.type==='text')?.text ?? '';
```

### Mock 모드(키 없을 때) 규칙
- summary: 입력 상태/태그를 그대로 인용한 완충형 4문장 + `needsVetNote`면 vet_note 포함. 단정·말대리·의료 표현 절대 미포함(가드레일 형식 그대로 → QA가 키 없이도 AC-06·07·08·09·10 검증 가능). **Mock 응답도 마지막에 `sanitize()`를 통과**시킨다(보정 지시 3).
- ask: 질문을 인용하고 프로필/최근 기록을 언급하는 완충형 1~2문단. 역시 `sanitize()` 통과.

## 7. 예외 처리
- 필수 필드 누락 → 400 + `{ error }`(한국어).
- Anthropic 호출 실패/타임아웃 → 500 대신 **graceful**: Mock 폴백 응답 반환(사용자 흐름 끊김 방지) + 서버 로그.
- summary JSON 파싱 실패 → 재시도 1회 → 폴백 분배.
- `sanitize`는 항상 마지막에 적용(실모델/Mock/폴백 모든 경로 공통) → 가드레일 누락 방지(보정 지시 3).

## 8. 완료 조건
- 두 엔드포인트가 정해진 입출력 JSON으로 동작.
- 키 설정 시 실제 Claude 응답(모델 기본 `claude-opus-4-8`), 미설정 시 Mock 응답 — 둘 다 가드레일 형식 준수.
- **키 없이(Mock)도 전체 end-to-end 흐름이 동작한다(보정 지시 9):** [프로필 등록 → 발자국 작성 → Mock AI 요약 표시/저장 → 기록 상세 조회 → AI 질문 Mock 응답 저장]을 본 서버가 막힘 없이 받친다(요약·질문 두 엔드포인트가 Mock으로 정상 응답).
- summary 응답에 condition/behavior/observation/memory 4필드가 항상 존재(AC-06).
- 출력에 단정 표현("확실히/100%/용서"), 말대리("말하고 있어요"), 진단/처방 표현이 포함되지 않음 — 실모델/Mock/폴백 모든 경로에서(AC-07·08·09).
- 식욕 저하·활동량 감소·배변 이상 입력 시 vet_note 존재(AC-10).
- ask가 프로필+최근 기록을 참조한 응답 생성(AC-11).
- 클라이언트 번들에 `ANTHROPIC_API_KEY`/`NEXT_PUBLIC_*` 키가 노출되지 않음(보정 지시 3).
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. 키 없이 `npm run dev` → `curl -X POST localhost:3000/api/ai/summary -H 'content-type: application/json' -d '{"pet":{"name":"콩","species":"강아지","age":"3살","gender":"수컷"},"entry":{"date":"2026-06-22","appetite":"거의 안 먹음","activity":"조용함","mood_tags":["피곤해 보임"]}}'` → 4필드 + vet_note 포함, 단정/진단 표현 없음 확인(AC-10).
2. `species:"기타"`로 호출 → 행동 해석이 일반 관찰 중심인지 확인(결정 #8).
3. `/api/ai/ask`에 question+recent 전달 → 답변에 프로필/최근 기록 맥락 반영 확인.
4. (키 보유 시) `.env.local`에 ANTHROPIC_API_KEY 설정 후 동일 호출 → 실제 응답도 가드레일 통과 확인.

## 10. 금지 사항
- 클라이언트에 API 키 노출 금지(`NEXT_PUBLIC_` 접두사 사용 금지 · 보정 지시 3).
- 화면/저장 로직 구현 금지(FEAT-06·07·02 영역).
- 사진(Blob)을 AI로 전송/수신 금지(텍스트/상태만 · 보정 지시 4).
- sanitize 단계 생략 금지(실모델·Mock·폴백 모든 응답 경로 공통 적용 · 보정 지시 3).
- 모델 기본값을 `claude-opus-4-8` 외로 하드코딩 금지(env 미설정 시 이 값 · 보정 지시 2).
- 설계서 외 라이브러리 추가 금지.
