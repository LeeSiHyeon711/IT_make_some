# FEAT-14 — 무지개연결 채팅 화면

- 매칭 이슈: #14
- 작성일: 2026-06-29
- 상위 설계서: `03-설계/무지개연결-설계서.md`

## 1. 목적
무지개연결 모드의 본체 화면 `/rainbow`를 만든다. 중심은 **리포트가 아니라 채팅형 회고(Rainbow Chat)**. 최초 진입 시 전체 기록 기반 **첫 회고 카드**를 1회 생성하고, 이후 보호자가 떠난 아이와 함께한 시간에 대해 질문하면 회고형 답변을 제공·저장한다. 미활성 상태 접근은 차단한다. AC-R03·R04·R05·R06·R10·R11.

## 의존성
- **FEAT-10**(`rainbowRepo.isOn`, `conversationRepo` rainbow 분리)·**FEAT-12**(`requestRainbow`)가 선행 안정화되어야 한다.
- `usePet()` 시그니처 `{ pet, loading, refresh }` 사용.

## 2. 범위
### 구현할 것
- `app/rainbow/page.tsx`:
  - 진입 가드: `rainbowRepo.isOn()===false`면 안내 후 `/profile/edit`로 차단(AC-R03).
  - 조용한 헤더 + 첫 회고 카드(최초 진입 시 `requestRainbow(pet,'intro',…)` 1회 자동 생성, **저장 안 함**). **분량은 한국어 300~500자 이내**(짧은 산문 한 단락 느낌, 항목 나열 금지).
  - 추천 질문 칩 4종(회상·탐색 중심).
  - `RainbowChat` 채팅 영역 + "무지개연결 닫기".
- `components/RainbowChat.tsx`:
  - 질문 입력 → `requestRainbow(pet,'chat',question,records,history)` → 답변 표시.
  - `conversationRepo.add({ …, mode:'rainbow' })` 저장 → `listByPetRainbow`로 목록(최신순) 표시.
  - 컨텍스트 records 준비: `entryRepo.listByPet` 후 최대 20개 분산 샘플(Blob 제외는 `requestRainbow` 내부).
### 구현하지 않을 것
- 엔드포인트/프롬프트/가드레일(FEAT-11·12).
- 진입점·이중 확인(FEAT-13).
- 첫 회고 카드 영구 저장(매번 생성 — 설계서 5-2).
- 영상/이미지 분석(향후 확장).

## 3. 입력 / 출력
- 입력: activePet, `entryRepo.listByPet(pet_id)`(전체), 사용자 질문.
- 출력: 첫 회고 카드(비저장), 채팅 Q&A 표시 + `conversations`에 `mode:'rainbow'` 저장.

## 4. 동작 흐름
1. `/rainbow` 진입 → `rainbowRepo.isOn()` 확인. false면 안내 후 `/profile/edit` 리다이렉트(AC-R03).
2. activePet·전체 기록 로드 → 최대 20개 분산 샘플 준비.
3. 최초 진입 시 `requestRainbow(pet,'intro',undefined,records)` → 첫 회고 카드 표시(**300~500자 이내** 짧은 산문, 로딩 중 Spinner, 실패해도 채팅은 사용 가능). 분량은 intro 프롬프트에 명시해 요청하고, 길면 부드럽게 절단해 카드가 길어지지 않게 한다.
4. 과거 rainbow 대화 로드(`listByPetRainbow`) → 채팅 목록 표시.
5. 추천 칩 클릭/직접 입력 → 전송 → `requestRainbow(pet,'chat',question,records,history)`.
6. 응답 표시 → `conversationRepo.add({ pet_id, user_question, ai_response, mode:'rainbow' })` → 목록 갱신.
7. "무지개연결 닫기" → `rainbowRepo.turnOff()` → `/profile/edit` 또는 `/today`로 복귀.

## 5. 수정 예상 파일
- `05-개발/app/rainbow/page.tsx`(신규)
- `05-개발/components/RainbowChat.tsx`(신규)

## 6. 데이터 구조 / 함수 / 컴포넌트
```tsx
// app/rainbow/page.tsx
// 진입 가드: useEffect(()=>{ rainbowRepo.isOn().then(on=>{ if(!on) router.replace('/profile/edit'); }) })
// 첫 회고: const [intro,setIntro]=useState<string>(); 최초 1회 requestRainbow(pet,'intro',undefined,records)
//          → 300~500자 이내 짧은 산문(분량 초과 시 부드럽게 절단)
// 헤더: "무지개연결" / "함께 남긴 발자국을 천천히 돌아보는 공간이에요."
// 추천 칩(회상·탐색 중심 — 기록을 함께 들여다보고 되돌아보도록 유도):
const RAINBOW_SUGGESTIONS = [
  '우리가 함께 가장 자주 보낸 시간은 어떤 모습이었을까?',   // 탐색: 반복된 일상 패턴 찾기
  '기록 속에서 유난히 빛났던 하루를 다시 보고 싶어',        // 회상: 특정 장면 되짚기
  '계절이 바뀌는 동안 우리는 어떤 시간을 보냈을까?',         // 탐색: 시간 흐름 따라 훑기
  '내가 남긴 기록들을 천천히 함께 돌아봐줘',               // 회상: 전체를 차분히 되걷기
];

// components/RainbowChat.tsx — /ask 카드 패턴 재사용(질문 카드 + 답변 카드)
export function RainbowChat({ pet, records }: { pet: Pet; records: DiaryEntry[] }): JSX.Element;
// history: 최근 몇 턴을 { q, a }로 requestRainbow에 전달(맥락 유지, 선택)
// 저장: conversationRepo.add({ pet_id: pet.pet_id, user_question, ai_response, mode: 'rainbow' });
// 목록: conversationRepo.listByPetRainbow(pet.pet_id) (timestamp desc)
```
- 톤(설계서 6-2): 첫 회고 카드는 **항목 나열 리포트 금지** → "함께한 시간의 분위기/자주 남겨진 순간/돌봄 흔적"을 부드러운 산문으로. 추모 이미지·무지개다리 클리셰 배제. 기존 `Card/Button/Tag/Spinner/AppHeader` 사용.

## 7. 예외 처리
- 미활성 접근 → 안내 후 차단(AC-R03).
- activePet 없음 → EmptyState로 프로필 등록 유도.
- 첫 회고 생성 실패 → 카드 영역에 "다시 불러오기" 제공, 채팅은 계속 사용 가능.
- 채팅 전송 실패 → "답변을 불러오지 못했어요" + 재시도, 질문 텍스트 유지.
- 기록 0~1개여도 동작(엔드포인트가 조심스러운 톤 — AC-R06).
- 빈 질문 전송 차단.

## 8. 완료 조건
- 미활성 상태 `/rainbow` 직접 접근 차단(AC-R03).
- 최초 진입 시 첫 회고 카드 자동 생성(리포트 아님, **300~500자 이내 짧은 산문** — AC-R04).
- 추천 질문 칩이 회상·탐색 중심으로 기록을 함께 되돌아보도록 유도(단정·감정 정의를 요구하지 않음).
- 채팅 질문→회고형 답변, `mode:'rainbow'` 저장, 재진입 시 이어지고 `/ask`와 안 섞임(AC-R05).
- 기록 적을 때 조심스러운 톤(AC-R06).
- "닫기"로 일상 모드 복귀, 기존 화면 무영향(AC-R10).
- AI 요청에 Blob 미포함(AC-R11).
- `npm run build` 타입/빌드 에러 0(AC-R12).

## 9. QA 체크리스트
- [ ] 미활성 상태 `/rainbow` 직접 접근 → 안내 후 `/profile/edit` 차단
- [ ] 활성 후 진입 → 첫 회고 카드 자동 표시(산문, 항목 나열 아님)
- [ ] 첫 회고 카드 분량이 300~500자 이내(초과 시 부드럽게 절단)
- [ ] 추천 칩 4종이 회상·탐색 중심 문구이고, 클릭 → 입력 반영 → 회고형 답변
- [ ] 답변이 `mode:'rainbow'`로 저장, 새로고침/재진입 후 유지
- [ ] 같은 pet의 `/ask` 대화와 섞이지 않음
- [ ] 기록 0~1개 → "남겨진 기록이 많지는 않지만…" 톤
- [ ] 첫 회고/채팅 실패 시 재시도 동작, 질문 텍스트 유지
- [ ] "무지개연결 닫기" → 일상 모드 복귀
- [ ] AI 요청 payload에 사진/이미지 Blob 없음
- [ ] `npm run build` 통과

## 10. 금지 사항
- 리포트형(항목 나열·점수·차트) 화면 구성 금지 — 채팅 중심 유지.
- 첫 회고 카드/AI 연결 글 영구 저장 금지(매번 생성 — 설계서 5-2).
- 반려동물 사칭 대화 형식 금지.
- 엔드포인트/프롬프트/가드레일 수정 금지(FEAT-11·12).
- 사진(Blob) AI 요청 포함 금지(보정 지시 R3).
- `BottomNav` 수정 금지.
- 설계서 외 라이브러리 추가 금지.
