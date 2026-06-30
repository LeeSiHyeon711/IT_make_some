# FEAT-10 — 무지개연결 상태 저장 + 대화 분리

- 매칭 이슈: #10
- 작성일: 2026-06-29
- 상위 설계서: `03-설계/무지개연결-설계서.md`

## 1. 목적
무지개연결 모드의 **데이터 토대**를 만든다. ① `meta` 스토어 기반 활성화 플래그(`rainbowMode`)를 다루는 `rainbowRepo`, ② 기존 `conversations` 스토어를 `/ask`와 섞이지 않게 분리하기 위한 `AIConversation.mode` 선택 필드와 `conversationRepo` 확장. **IndexedDB 스키마 버전은 올리지 않는다(마이그레이션 0).** AC-R02·R05·R10.

## 의존성
- 독립. FEAT-12·13·14의 선행 토대다(상태 플래그·대화 저장이 여기서 확정된다).

## 2. 범위
### 구현할 것
- `lib/types.ts`: `AIConversation`에 **선택적** 필드 `mode?: 'ask' | 'rainbow'` 추가.
- `lib/repos.ts`:
  - `rainbowRepo` 추가 — `metaRepo` 얇은 래퍼: `isOn()` / `turnOn()` / `turnOff()`.
  - `conversationRepo` 확장 — `add`가 `mode`를 받아 저장(미지정 시 기존과 동일), rainbow 전용 조회 헬퍼 `listByPetRainbow(pet_id)`(또는 `listByPet`에 mode 필터).
### 구현하지 않을 것
- UI/엔드포인트/프롬프트(FEAT-11·12·13·14).
- DB 스키마 버전업·마이그레이션(불필요 — 객체 필드 추가만).
- `Pet` 타입에 사망 상태 추가(영구 비범위 — 설계서 5-3).

## 3. 입력 / 출력
- 입력: 기존 `meta`/`conversations` 스토어(스키마 변경 없음).
- 출력: `rainbowMode` 플래그 read/write, `mode` 분리 저장·조회가 동작하는 repo 함수.

## 4. 동작 흐름
1. `rainbowRepo.isOn()` → `metaRepo.get('rainbowMode') === 'on'`.
2. `rainbowRepo.turnOn()` → `metaRepo.set('rainbowMode','on')` + `metaRepo.set('rainbowActivatedAt', new Date().toISOString())`.
3. `rainbowRepo.turnOff()` → `metaRepo.set('rainbowMode','off')`(또는 키 제거 정책 중 하나로 통일).
4. `conversationRepo.add({ pet_id, user_question, ai_response, mode })` → `mode` 포함 저장. `mode` 미지정 호출(`/ask`)은 기존과 동일.
5. `conversationRepo.listByPetRainbow(pet_id)` → `by-pet` 인덱스 조회 후 `mode==='rainbow'`만 필터, timestamp desc.

## 5. 수정 예상 파일
- `05-개발/lib/types.ts` (필드 추가)
- `05-개발/lib/repos.ts` (`rainbowRepo` 추가, `conversationRepo` 확장)

## 6. 데이터 구조 / 함수 / 컴포넌트
```ts
// lib/types.ts — 선택적 필드 추가(기존 레코드 호환: 없으면 'ask'로 간주)
export interface AIConversation {
  conversation_id: string;
  pet_id: string;
  user_question: string;
  ai_response: string;
  timestamp: string;
  mode?: 'ask' | 'rainbow';   // ★ 추가
}

// lib/repos.ts
export const rainbowRepo = {
  async isOn(): Promise<boolean> { return (await metaRepo.get('rainbowMode')) === 'on'; },
  async turnOn(): Promise<void> {
    await metaRepo.set('rainbowMode', 'on');
    await metaRepo.set('rainbowActivatedAt', new Date().toISOString());
  },
  async turnOff(): Promise<void> { await metaRepo.set('rainbowMode', 'off'); },
};

// conversationRepo.add 확장 — mode 전달 가능(미지정 시 저장 객체에서 생략)
async add(input: Omit<AIConversation, 'conversation_id' | 'timestamp'>): Promise<AIConversation>;
// rainbow 전용 조회
async listByPetRainbow(pet_id: string): Promise<AIConversation[]>; // mode==='rainbow', timestamp desc
```
- `conversations` 스토어/인덱스(`by-pet`)는 그대로 사용. 별도 인덱스 추가 불필요(앱 내 대화량이 작아 클라이언트 필터로 충분).

## 7. 예외 처리
- `metaRepo.get`이 undefined → `isOn()`은 false(에러 아님).
- IndexedDB 접근 실패 시 기존 `getDB` 에러 전파 정책 따름(호출 측에서 처리).
- `mode` 없는 기존 레코드는 rainbow 필터에서 제외(=`/ask` 대화로 간주) → 기존 `/ask` 동작 불변.

## 8. 완료 조건
- `AIConversation.mode` 추가 후 기존 `/ask` 저장/조회 동작에 회귀 없음(AC-R10).
- `rainbowRepo.isOn/turnOn/turnOff`로 `rainbowMode` 토글이 영속 저장됨(AC-R02·R10).
- `listByPetRainbow`가 rainbow 대화만 반환, `/ask` 대화와 섞이지 않음(AC-R05).
- DB 버전 변경·마이그레이션 코드 없음(설계서 5).
- `npm run build` 타입/빌드 에러 0(AC-R12).

## 9. QA 체크리스트
- [ ] `rainbowMode` set/get이 새로고침 후에도 유지됨
- [ ] `turnOn` 후 `isOn()===true`, `turnOff` 후 `false`
- [ ] `mode:'rainbow'`로 add → `listByPetRainbow`에만 나타나고 `/ask` 목록(기존 `listByPet`)엔 섞이지 않음
- [ ] `mode` 미지정 add(`/ask` 경로) → 기존과 동일 저장/조회
- [ ] DB 버전 그대로(1), 마이그레이션 코드 없음
- [ ] `npm run build` 통과

## 10. 금지 사항
- DB 스키마 버전업·마이그레이션 추가 금지(객체 필드 추가로 충분).
- `Pet` 타입에 사망/추모 상태 추가 금지(설계서 5-3).
- UI/엔드포인트/프롬프트 구현 금지(타 FEAT 영역).
- 기존 `metaRepo`/`conversationRepo`의 기존 시그니처 파괴 금지(확장만).
