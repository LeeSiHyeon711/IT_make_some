# FEAT-02 — 로컬 저장 계층 (IndexedDB)

- 매칭 이슈: #02
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
프로필·기록·대화·메타를 브라우저에 영구 저장하는 **IndexedDB 데이터 계층**을 만든다(설계 결정 #1). 모든 후속 화면 FEAT가 이 타입/저장소 함수만 호출하면 되도록, **도메인 타입과 Repository 함수 시그니처를 여기서 확정**한다. AC-01·05·15의 토대.

## 2. 범위
### 구현할 것
- `lib/types.ts`: 모든 도메인 타입(Pet/DiaryEntry/AISummary/AIConversation/Meta + 선택값 유니온).
- `lib/db.ts`: `idb`로 DB(`pawprint-diary` v1) 초기화 + 스토어/인덱스 정의.
- `lib/repos.ts`: petRepo / entryRepo / conversationRepo / metaRepo 함수 일체. 사진·프로필 이미지는 **Blob 그대로 저장**(설계 결정 #2 A안).
### 구현하지 않을 것
- 화면/폼(FEAT-03·04), 이미지 리사이즈(FEAT-03), AI 호출(FEAT-05).
- 다중 반려동물 전환 로직(MVP 제외) — 단, 모든 함수는 `pet_id` 인자를 받는 형태로 설계(결정 #6).

### ★ 보정 지시 4 — Blob 필드는 저장 전용, AI 전송 금지(타입 주석으로 못박기)
- `Pet.profile_image`(Blob)와 `DiaryEntry.photos`(Blob[])는 **IndexedDB에만 저장**하는 필드다.
- **이 Blob 필드들은 AI API 요청(`/api/ai/*`)에 절대 포함하지 않는다.** aiClient(FEAT-06/07)가 직렬화 시 Blob 필드를 제외하고 텍스트/상태 필드만 매핑한다.
- 이를 타입 정의에 **주석으로 명시**해 후속 FEAT가 실수로 직렬화하지 않게 한다(아래 6장 참조).

### ★ 보정 지시 7 — 날짜/정렬 기준 통일(여기서 확정)
- `DiaryEntry.date`는 **`YYYY-MM-DD` 문자열**, `created_at`은 **ISO 문자열**로 고정.
- 목록 정렬 기준: **date desc, 동일 날짜는 created_at desc.** `entryRepo.listByPet`이 이 정렬을 보장한다.

## 3. 입력 / 출력
### 입력
- 후속 FEAT의 호출(객체 인자). 브라우저 IndexedDB 가용 환경.
### 출력
- 영구 저장된 레코드, 조회 결과 배열/객체. 새로고침·탭 재진입 후에도 유지.

## 4. 동작 흐름
1. `getDB()`가 최초 호출 시 `openDB('pawprint-diary', 1, { upgrade })`로 스토어/인덱스 생성(싱글톤 Promise 캐시).
2. 각 repo 함수는 `getDB()`를 await 후 해당 스토어에 read/write.
3. 쓰기 시 `crypto.randomUUID()`로 id, `new Date().toISOString()`로 시각 생성.
4. 조회 정렬은 메모리에서 `date`(YYYY-MM-DD) desc, 동일 날짜는 `created_at` desc(보정 지시 7).

## 5. 수정 예상 파일
- `05-개발/lib/types.ts`
- `05-개발/lib/db.ts`
- `05-개발/lib/repos.ts`

## 6. 데이터 구조 / 함수 / 클래스
```ts
// lib/types.ts — 이 타입 이름·필드를 모든 FEAT가 그대로 사용한다(변경 금지)
export type Species  = '강아지' | '고양이' | '기타';
export type Gender   = '수컷' | '암컷' | '중성화 수컷' | '중성화 암컷';
export type Appetite = '잘 먹음' | '보통' | '적게 먹음' | '거의 안 먹음';
export type Activity = '매우 활발함' | '보통' | '조용함' | '거의 움직이지 않음';
export type Sleep    = '평소보다 많이 잠' | '보통' | '평소보다 적게 잠';
export type Toilet   = '정상' | '묽음' | '굳음' | '없음' | '이상 있음';

export interface Pet {
  pet_id: string;
  name: string;
  species: Species;
  breed?: string;
  age: string;
  gender: Gender;
  personality?: string;
  likes?: string;
  dislikes?: string;
  health_notes?: string;
  profile_image?: Blob;     // ⚠ IndexedDB 저장 전용 Blob. AI 요청에 절대 포함 금지(보정 지시 4)
  created_at: string;       // ISO
}

export interface AISummary {
  condition: string;        // 오늘의 컨디션 요약
  behavior: string;         // 행동 해석
  observation: string;      // 관찰 포인트
  memory: string;           // 한 줄 추억 문장
  vet_note?: string;        // 조건부 병원 상담 권장(FEAT-05/06)
  generated_at: string;     // ISO
}

export interface DiaryEntry {
  entry_id: string;
  pet_id: string;
  date: string;             // YYYY-MM-DD (정렬 1순위 desc — 보정 지시 7)
  diary_text?: string;
  appetite?: Appetite;
  activity?: Activity;
  sleep?: Sleep;
  toilet?: Toilet;
  unusual_behavior?: string;
  mood_tags: string[];
  photos: Blob[];           // ⚠ IndexedDB 저장 전용 Blob 배열. AI 요청에 절대 포함 금지(보정 지시 4)
  ai_summary?: AISummary;   // 저장 시 함께 저장(결정 #4). FEAT-04는 미포함 저장, FEAT-06이 병합
  created_at: string;       // ISO (정렬 2순위: 동일 date 내 desc — 보정 지시 7)
}

export interface AIConversation {
  conversation_id: string;
  pet_id: string;
  user_question: string;
  ai_response: string;
  timestamp: string;        // ISO
}

export interface MetaRecord { key: string; value: string; }

// lib/db.ts
import { openDB, type IDBPDatabase } from 'idb';
export function getDB(): Promise<IDBPDatabase>;
// upgrade에서:
//  pets        : keyPath 'pet_id'
//  entries     : keyPath 'entry_id'; index 'by-pet'(pet_id); index 'by-pet-date'(['pet_id','date'])
//  conversations: keyPath 'conversation_id'; index 'by-pet'(pet_id)
//  meta        : keyPath 'key'

// lib/repos.ts
export const petRepo = {
  create(input: Omit<Pet,'pet_id'|'created_at'>): Promise<Pet>,   // id/created_at 생성 후 저장
  update(pet: Pet): Promise<Pet>,
  get(pet_id: string): Promise<Pet | undefined>,
  getActive(): Promise<Pet | undefined>,                          // metaRepo.get('activePetId')로 조회
};
export const entryRepo = {
  save(input: Omit<DiaryEntry,'entry_id'|'created_at'>): Promise<DiaryEntry>,
  update(entry: DiaryEntry): Promise<DiaryEntry>,
  get(entry_id: string): Promise<DiaryEntry | undefined>,
  remove(entry_id: string): Promise<void>,
  listByPet(pet_id: string): Promise<DiaryEntry[]>,               // date desc, 동일 날짜 created_at desc(보정 지시 7)
  recentByPet(pet_id: string, n: number): Promise<DiaryEntry[]>,  // 최신 n개(질문 컨텍스트용, 동일 정렬 기준)
};
export const conversationRepo = {
  add(input: Omit<AIConversation,'conversation_id'|'timestamp'>): Promise<AIConversation>,
  listByPet(pet_id: string): Promise<AIConversation[]>,           // timestamp desc
};
export const metaRepo = {
  get(key: string): Promise<string | undefined>,
  set(key: string, value: string): Promise<void>,
};
```

## 7. 예외 처리
- IndexedDB 미지원/프라이빗 모드 등 `getDB()` 실패 시 throw하고, 호출 측이 사용자에게 "이 브라우저에서는 저장이 어려워요" 안내할 수 있도록 에러 메시지를 한국어로 명확히.
- `getActive()`는 activePetId 없거나 해당 pet 없으면 `undefined` 반환(에러 아님).
- `crypto.randomUUID()` 미지원 환경 대비 폴백(타임스탬프+랜덤) 한 줄 유틸 허용.

## 8. 완료 조건
- 네 개 스토어가 생성되고, 각 repo 함수가 타입대로 동작한다.
- `petRepo.create` 후 `petRepo.getActive`(activePetId set 시) 또는 `petRepo.get`으로 동일 데이터 조회.
- `entryRepo.listByPet`가 **date desc → created_at desc** 순서로 반환한다(보정 지시 7).
- 새로고침/탭 재진입 후에도 데이터가 유지된다(IndexedDB 영속성).
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. 임시로 브라우저 콘솔 또는 테스트용 버튼에서 `petRepo.create({...})` 호출 → 반환 객체에 `pet_id`/`created_at` 존재 확인.
2. DevTools → Application → IndexedDB → `pawprint-diary` → `pets`에 레코드 보이는지 확인.
3. 새로고침 후 `petRepo.get(id)`로 같은 데이터 반환 확인.
4. `entryRepo.save` → `entryRepo.listByPet`가 날짜 역순(동일 날짜는 created_at 역순)으로 반환되는지 확인.

## 10. 금지 사항
- 화면/폼 구현 금지(FEAT-03·04).
- 타입 이름/필드 임의 변경 금지(다른 FEAT가 이 이름에 의존).
- 서버/네트워크 호출 금지(이 계층은 100% 클라이언트 로컬).
- LocalStorage로 대체 구현 금지(IndexedDB 확정).
- Blob 필드(`profile_image`/`photos`)를 네트워크/AI로 내보내는 코드 작성 금지(보정 지시 4).
