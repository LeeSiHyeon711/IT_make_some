# FEAT-03 — 반려동물 프로필 등록/수정

- 매칭 이슈: #03
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
보호자가 반려동물 프로필(이름·종·나이·성별 등 + 프로필 이미지)을 등록·수정하는 화면을 만든다. AI가 우리 아이를 이해하는 기반 데이터이며, 첫 진입 흐름의 출발점이다. AC-01·02.

## 2. 범위
### 구현할 것
- `app/profile/new/page.tsx`(등록), `app/profile/edit/page.tsx`(수정) — `ProfileForm` 공용 사용.
- `components/ProfileForm.tsx`: PRD 6-1 전 항목 입력. 필수(이름·종·나이·성별) 검증.
- `components/PhotoUpload.tsx`(단일 이미지 모드) + `lib/imageUtils.ts`의 `resizeImageToBlob` — 프로필 이미지 리사이즈 후 Blob 저장(결정 #2 A안).
- `components/BlobImage.tsx`: Blob → objectURL 표시(언마운트 시 revoke).
- 저장 시 `petRepo.create`/`petRepo.update` 호출, 등록이면 `metaRepo.set('activePetId', pet_id)` 후 `/today`로 이동.
- `lib/petContext.tsx` 확장: `usePet()`이 activePet 로딩/갱신 제공(FEAT-01 골격을 실구현으로 교체).
### 구현하지 않을 것
- 일기 작성/사진 다중 첨부(FEAT-04에서 PhotoUpload 다중 모드 사용).
- 다중 반려동물 선택/전환 UI(MVP 제외, 결정 #6).
- AI 호출.

### ★ 보정 지시 1 — usePet 실구현 시그니처를 여기서 "확정 + 교체"
- 본 FEAT가 FEAT-01의 임시 골격을 **실제 구현으로 교체**한다. 확정 시그니처는 다음으로 고정한다.
  ```ts
  usePet(): { pet?: Pet; loading: boolean; refresh: () => Promise<void> }
  ```
  - `pet`: 현재 activePet(`petRepo.getActive()` 결과). 없으면 `undefined`.
  - `loading`: 최초 로딩/refresh 진행 여부.
  - `refresh()`: `petRepo.getActive()` 재조회로 `pet` 갱신(프로필 등록·수정 직후 호출).
- **후속 FEAT-04/06/07/08/09는 이 시그니처(`{ pet, loading, refresh }`)를 기준으로 구현**한다. 다른 형태의 usePet을 가정하지 않는다.

### ★ 보정 지시 8 — 1마리 MVP 원칙
- 데이터는 `pet_id`를 유지하되, **반려동물 전환/선택/필터 UI를 절대 만들지 않는다.** 화면은 항상 `activePet` 1마리 기준으로만 동작한다(today/entries/ask 전부 동일).

## 3. 입력 / 출력
### 입력
- 사용자 폼 입력, 이미지 파일(File).
### 출력
- `pets` 스토어에 Pet 레코드 저장, `meta.activePetId` 설정. 등록 후 `/today` 이동.

## 4. 동작 흐름
1. `/profile/new` 진입 → 빈 ProfileForm.
2. 사용자가 입력. 필수 4항목 채워지면 저장 버튼 활성.
3. 이미지 선택 시 `resizeImageToBlob(file, 800, 0.85)`로 다운스케일 → 미리보기(BlobImage) 표시.
4. 저장 클릭 → `petRepo.create(form)` → `metaRepo.set('activePetId', pet.pet_id)` → `usePet().refresh()` → `router.push('/today')`.
5. `/profile/edit` 진입 → `petRepo.getActive()`로 기존 값 프리필 → 수정 후 `petRepo.update` → `usePet().refresh()`.

## 5. 수정 예상 파일
- `05-개발/app/profile/new/page.tsx`, `app/profile/edit/page.tsx`
- `05-개발/components/ProfileForm.tsx`, `components/PhotoUpload.tsx`, `components/BlobImage.tsx`
- `05-개발/lib/imageUtils.ts`
- `05-개발/lib/petContext.tsx`(확장 — usePet 실구현으로 교체)

## 6. 데이터 구조 / 함수 / 클래스
```tsx
// lib/imageUtils.ts
// File을 canvas로 그려 최대 변 maxSize 이하로 축소, JPEG Blob 반환
export async function resizeImageToBlob(file: File, maxSize: number, quality: number): Promise<Blob>;

// components/BlobImage.tsx
export function BlobImage(props: { blob?: Blob; alt: string; className?: string }): JSX.Element;
// useEffect로 URL.createObjectURL(blob), cleanup에서 revokeObjectURL

// components/PhotoUpload.tsx (mode로 단일/다중 공용 — FEAT-04에서 다중 사용)
type PhotoUploadProps =
  | { mode: 'single'; value?: Blob;   onChange: (b?: Blob) => void }
  | { mode: 'multi';  value: Blob[];  onChange: (b: Blob[]) => void; max?: number };
export function PhotoUpload(props: PhotoUploadProps): JSX.Element;

// components/ProfileForm.tsx
type ProfileFormValues = Omit<Pet, 'pet_id' | 'created_at'>;
export function ProfileForm(props: {
  initial?: Pet;                       // 수정 시 프리필
  submitLabel: string;                 // '등록하기' | '수정 저장'
  onSubmit: (values: ProfileFormValues) => Promise<void>;
}): JSX.Element;
// 종 선택: 강아지/고양이/기타 (Tag 또는 select)
// 성별 선택: 수컷/암컷/중성화 수컷/중성화 암컷
// 필수: name, species, age, gender → 미충족 시 저장 비활성 + 안내

// lib/petContext.tsx (확장 — FEAT-01 임시 골격을 교체. 보정 지시 1)
export function usePet(): {
  pet?: Pet;
  loading: boolean;
  refresh: () => Promise<void>;        // petRepo.getActive() 재로딩
};
```
- 디자인 톤(설계서 4-3): Field/Card/Tag/Button 공통 컴포넌트 사용. "우리 아이를 소개해 주세요 🐾" 같은 따뜻한 카피. 유아틱 금지.

## 7. 예외 처리
- 필수 항목 미입력 시 저장 비활성 + 각 필드 하단 안내 문구.
- 이미지 리사이즈 실패(손상 파일 등) → 이미지 없이 저장 진행 + "이미지를 불러오지 못했어요" 토스트.
- `/profile/edit` 진입 시 activePet 없으면 `/profile/new`로 유도.
- 이미지 용량 과다 방지: 리사이즈 후 저장(원본 저장 금지).

## 8. 완료 조건
- 필수 4항목 입력 후 저장 시 `pets`에 레코드 생성 + `activePetId` 설정(AC-01).
- 프로필 이미지 업로드 시 저장 후 화면에 표시(AC-02).
- 새로고침/탭 재진입 후에도 프로필 유지(AC-01·15 일부).
- 수정 화면에서 기존 값 프리필 후 변경 저장 동작.
- `usePet()`이 `{ pet, loading, refresh }` 시그니처로 동작하고 등록/수정 후 `refresh()`로 갱신됨(보정 지시 1).
- `npm run build` **타입/빌드 에러 0**으로 통과(전역 규칙 — 설계서 7장).

## 9. 테스트 방법
1. `/profile/new`에서 이름·종·나이·성별만 입력 → 저장 → `/today`로 이동되고 IndexedDB `pets`에 레코드 확인.
2. 이미지 첨부 후 저장 → 미리보기/표시 확인, 새로고침 후에도 이미지 표시.
3. `/profile/edit` 진입 → 값 프리필 확인 → 성격 등 수정 저장 → 반영 확인.
4. 필수 항목 비우면 저장 버튼 비활성 확인.

## 10. 금지 사항
- 일기/AI 영역 침범 금지(FEAT-04·05·06).
- 다중 반려동물 선택/전환/필터 UI 추가 금지(결정 #6 · 보정 지시 8).
- 원본 이미지 무압축 저장 금지(반드시 resize).
- 설계서 외 라이브러리 추가 금지.
