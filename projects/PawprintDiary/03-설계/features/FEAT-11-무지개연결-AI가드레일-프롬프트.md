# FEAT-11 — 무지개연결 AI 가드레일 + 회고 프롬프트

- 매칭 이슈: #11
- 작성일: 2026-06-29
- 상위 설계서: `03-설계/무지개연결-설계서.md`

## 1. 목적
무지개연결 모드의 **안전장치와 회고 전용 프롬프트**를 만든다. 이 FEAT가 모드의 정체성을 코드로 보장하는 가장 품질 민감한 부분이다. ① 회고 전용 시스템/유저 프롬프트 빌더(`lib/rainbowPrompt.ts`), ② 위험 문장 패턴 차단기 `sanitizeRainbow()`(`lib/guardrails.ts` 확장). AC-R06·R07·R08.

## ★ 보정 지시 R1 — `sanitizeRainbow`는 "단어 삭제기"가 아니라 "위험 문장 패턴 차단기"
- **단어 자체**(`고마워`, `사망`, `무지개`, `세상을 떠난` 등)를 무조건 제거하지 **않는다**.
- 차단 대상은 **반려동물을 주어로 한 감정/의지/영적 단정**, **사망 원인 추측**, **슬픔 재촉**의 *문장 형태*다.

| 차단(금지) | 허용(통과) |
|------------|-----------|
| "아이가 고마워하고 있어요" | "고마웠던 순간을 기록 속에서 찾아볼게요." |
| "아이도 행복했을 거예요" | "무지개연결은 함께한 시간을 돌아보는 공간이에요." |
| "하늘에서 보고 있어요" | "세상을 떠난 반려동물과의 추억을 회상하기 위한 모드입니다." |
| "당신을 용서했을 거예요" | "남겨진 발자국 속에 돌보려 한 흔적이 보여요." |
| "사망 원인은 ~일 수 있어요" | "이 기록은 결론을 내리기보다 천천히 바라보게 해줘요." |

## ★ 보정 지시 R2 — UI 고정 문구와 AI 응답 후처리 분리
- `sanitizeRainbow`는 **`/api/ai/rainbow`가 생성한 AI 응답에만** 적용한다.
- 첫 진입 안내·버튼·모드 설명 등 **UI 고정 문구는 sanitize 대상이 아니다**(FEAT-13에서 하드코딩, 본 함수 미적용).

## 의존성
- 독립. 기존 `lib/guardrails.ts`의 `sanitize`/`BANNED_REPLACE`/`STRIP_SENTENCE`는 **변경하지 않고**, 별도 함수/패턴으로 합성한다.

## 2. 범위
### 구현할 것
- `lib/rainbowPrompt.ts`(신규):
  - `RAINBOW_SYSTEM_PROMPT` — 회고 전용·더 엄격한 시스템 프롬프트.
  - `buildRainbowIntroPrompt(pet, records)` — 첫 회고 카드용 user 프롬프트.
  - `buildRainbowChatPrompt(pet, question, records, history?)` — 채팅 답변용 user 프롬프트.
- `lib/guardrails.ts`(확장, 기존 불변):
  - `sanitizeRainbow(text)` — 기존 `sanitize` 통과 후 무지개 전용 위험 **문장** 패턴 차단을 추가 적용.
  - 회고 전용 패턴 상수(반려동물 주어 감정/의지/영적 단정, 사망원인 추측, 슬픔 재촉).
### 구현하지 않을 것
- 엔드포인트/클라이언트 호출(FEAT-12).
- 화면(FEAT-14).
- 기존 `sanitize`/`SYSTEM_PROMPT` 수정(불변).

## 3. 입력 / 출력
- `buildRainbow*Prompt`: 입력 `PetLike` + 회고 컨텍스트(기록 배열, Blob 제외) → 출력 `{ system, user }`.
- `sanitizeRainbow`: 입력 임의 AI 텍스트 → 출력 위험 문장 차단·완화된 텍스트.

## 4. 동작 흐름 (sanitizeRainbow)
1. 먼저 기존 `sanitize(text)` 적용(단정 완충·기본 말대리/의료 문장 제거 재사용).
2. 결과를 문장 단위로 분리(마침표·느낌표·물음표 기준, 기존 방식 차용).
3. **위험 문장 패턴**에 매칭되는 문장만 제거. 패턴은 "반려동물 지시어 + 감정/의지/영적 술어"의 **근접 조합**으로 구성(단어 단독 매칭 금지 — 보정 지시 R1).
4. 남은 문장을 재결합해 반환. (UI 문구에는 적용하지 않음 — 보정 지시 R2)

## 5. 수정 예상 파일
- `05-개발/lib/rainbowPrompt.ts`(신규)
- `05-개발/lib/guardrails.ts`(확장 — 기존 export 불변)

## 6. 데이터 구조 / 함수 / 컴포넌트
```ts
// lib/rainbowPrompt.ts
export const RAINBOW_SYSTEM_PROMPT = `당신은 세상을 떠난 반려동물과 함께한 시간을, 보호자가 조용히
되돌아보도록 돕는 안내자입니다. 반려동물을 대신해 말하지 않습니다. 다음을 반드시 지키세요.
1) 반려동물 말 대리 금지: "아이가 ~라고 말해요", "고마워하고 있어요"처럼 반려동물을 주어로
   감정/의지를 대신 전하지 않습니다.
2) 감정 단정 금지: "행복했어요/행복했을 거예요/용서했어요"처럼 반려동물의 감정을 단정하지 않습니다.
3) 종교·영적 해석 금지: "하늘에서 보고 있어요", "무지개다리 건너", "기다리고 있어요" 등을 쓰지 않습니다.
4) 죄책감 자극·슬픔 재촉 금지: "이제 괜찮아져야 해요" 같은 표현 대신, 슬픔을 서둘러 정리하지
   않아도 된다는 태도를 유지합니다.
5) 의료 판단·사망 원인 추측 금지.
지향: 기록에 실제로 남아있는 사실(날짜·활동·자주 남겨진 순간·좋아했던 것)만 인용하고,
보호자가 꾸준히 기록하고 돌본 흔적을 조용히 비춥니다. 감정은 보호자의 몫으로 남깁니다.
기록이 적으면 "남겨진 기록이 많지는 않지만…"처럼 조심스럽게 답합니다.
모든 답변은 한국어로, 따뜻하지만 단정하지 않는 어조로 작성합니다.`;

export function buildRainbowIntroPrompt(pet: PetLike, records: RainbowRecord[]): { system: string; user: string };
export function buildRainbowChatPrompt(
  pet: PetLike, question: string, records: RainbowRecord[], history?: { q: string; a: string }[]
): { system: string; user: string };

// RainbowRecord: Blob 제외 회고용 경량 타입
// { date, mood_tags?, diary_text?(절단), condition?(ai_summary.condition), photoCount? }

// lib/guardrails.ts (확장 — 기존 sanitize/STRIP_SENTENCE 불변)
// 위험 "문장" 패턴: 반려동물 지시어 + 감정/의지/영적 술어의 근접 조합 (단어 단독 매칭 금지)
const RAINBOW_STRIP_SENTENCE: RegExp[] = [
  /(아이|아이가|반려동물|그 아이).{0,12}(행복|고마워|고마워하|용서|기뻐했|좋아했을\s*거)/,
  /(하늘에서|무지개다리|곁에서\s*지켜|기다리고\s*있어요|보고\s*있어요)/,
  /(괜찮아져야|이제\s*그만\s*슬퍼|잊어야)/,
  /(사망|죽음|떠난)\s*원인.{0,8}(때문|일\s*수|였을)/,
];
export function sanitizeRainbow(text: string): string; // sanitize(text) 후 RAINBOW_STRIP_SENTENCE 문장 제거
```
> 패턴은 위 예시를 기준으로 한 초안이다. 구현 시 보정 지시 R1의 차단/허용 표를 모두 만족(허용 문장은 통과)하도록 정밀화한다.

## 7. 예외 처리
- 빈 문자열/`undefined` 입력 → 그대로 반환(크래시 금지).
- 모든 문장이 제거되면 안전 기본 문구로 대체(예: "기록을 천천히 살펴보았어요. 함께한 시간이 남아 있어요.") → 빈 응답 방지.
- 허용되어야 할 문구(보정 지시 R1 표의 우측)가 제거되면 패턴이 과도한 것 → 패턴을 좁혀 통과시킨다.

## 8. 완료 조건
- `RAINBOW_SYSTEM_PROMPT`가 2장 절대 원칙·지향을 모두 명시.
- `sanitizeRainbow`가 보정 지시 R1 표의 **금지 문장은 제거**, **허용 문장은 통과**(AC-R07).
- UI 고정 안내 문구는 본 함수와 무관하게 유지 가능(보정 지시 R2 — FEAT-13에서 검증).
- 기존 `sanitize`/`SYSTEM_PROMPT`/`/ask` 동작에 회귀 없음.
- `npm run build` 타입/빌드 에러 0(AC-R12).

## 9. QA 체크리스트
- [ ] 금지 문장 5종("아이가 고마워하고 있어요" 등) 각각 `sanitizeRainbow`로 제거됨
- [ ] 허용 문장 3종("고마웠던 순간을 기록 속에서 찾아볼게요." 등) 그대로 통과
- [ ] "사망 원인은 ~일 수 있어요" 제거, "세상을 떠난 반려동물과의 추억…" 통과
- [ ] 빈/undefined 입력 안전 처리, 전부 제거 시 기본 문구 대체
- [ ] 기존 `sanitize`/`/ask` 회귀 없음
- [ ] `npm run build` 통과

## 10. 금지 사항
- 단어 단독 삭제 방식 금지(`고마워`/`무지개`/`사망` 등 단어만으로 제거 금지 — 보정 지시 R1).
- UI 고정 문구에 `sanitizeRainbow` 적용 금지(보정 지시 R2).
- 기존 `sanitize`/`SYSTEM_PROMPT` 수정 금지(확장만).
- 엔드포인트/화면 구현 금지(타 FEAT 영역).
- 설계서 외 라이브러리 추가 금지.
