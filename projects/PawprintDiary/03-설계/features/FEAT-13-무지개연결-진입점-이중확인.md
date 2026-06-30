# FEAT-13 — 무지개연결 진입점 + 이중 확인

- 매칭 이슈: #13
- 작성일: 2026-06-29
- 상위 설계서: `03-설계/무지개연결-설계서.md`

## 1. 목적
무지개연결 모드의 **조용한 진입점과 이중 확인 활성화 흐름**을 만든다. 프로필 화면 최하단에만 진입점을 두고(하단 탭 미노출), 1차 안내 → 2차 확인을 모두 통과해야만 `rainbowMode='on'`을 저장한다. 비활성화(닫기)도 제공한다. AC-R01·R02·R08·R10.

## ★ 보정 지시 R2 — UI 고정 문구는 sanitize 대상이 아니다
- 본 FEAT의 안내·버튼·설명 문구는 **사람이 직접 작성한 고정 문구**로, `sanitizeRainbow`를 적용하지 않는다.
- 아래 문구는 기능 성격을 분명히 알려야 하므로 **그대로 유지**한다:
  - 1차: "무지개연결은 반려동물이 세상을 떠난 뒤, 함께했던 시간을 조용히 돌아보기 위한 공간이에요. 아직 준비되지 않았다면 지금 열지 않아도 괜찮아요."
  - 2차: "이 기능은 세상을 떠난 반려동물과의 추억을 회상하기 위한 모드입니다. 무지개연결 모드를 활성화할까요?"

## 의존성
- **FEAT-10**(`rainbowRepo.isOn/turnOn/turnOff`)이 선행되어야 한다.
- `/rainbow` 화면 본체는 FEAT-14(여기서는 활성화 후 `router.push('/rainbow')`만).

## 2. 범위
### 구현할 것
- `components/RainbowSection.tsx`: 프로필 최하단 섹션. `rainbowRepo.isOn()`에 따라 분기.
  - 미활성: "무지개연결 열기" → 1차 안내 → 2차 확인 모달 → `turnOn()` → `/rainbow` 이동.
  - 활성: "무지개연결 들어가기"(→ `/rainbow`) + "무지개연결 닫기"(→ `turnOff()`).
- `components/RainbowConfirmModal.tsx`(또는 RainbowSection 내 인라인): 2차 확인 모달.
- `app/profile/edit/page.tsx`: 폼 하단에 `<RainbowSection/>` 1줄 삽입.
### 구현하지 않을 것
- `/rainbow` 화면/채팅(FEAT-14).
- 엔드포인트/프롬프트(FEAT-11·12).
- `BottomNav` 수정(영구 비범위 — AC-R01).

## 3. 입력 / 출력
- 입력: `rainbowRepo.isOn()` 상태.
- 출력: 이중 확인 통과 시 `rainbowMode='on'` 저장 + `/rainbow` 이동 / "닫기" 시 `'off'`.

## 4. 동작 흐름
1. 프로필 진입 → `RainbowSection`이 `isOn()` 조회.
2. 미활성: "무지개연결 열기" 클릭 → 1차 안내 표시(인라인 패널 또는 `/rainbow/intro`).
   - "아직 아니에요" → 닫기(상태 변화 없음).
   - "계속 보기" → 2차 확인 모달.
3. 2차 모달: "취소할게요" → 닫기(저장 없음). "무지개연결 활성화" → `rainbowRepo.turnOn()` → `router.push('/rainbow')`.
4. 활성 상태: "들어가기"(→ `/rainbow`), "닫기"(→ `turnOff()` → 섹션 미활성 표시로 갱신).

## 5. 수정 예상 파일
- `05-개발/components/RainbowSection.tsx`(신규)
- `05-개발/components/RainbowConfirmModal.tsx`(신규, 선택 — 인라인 가능)
- `05-개발/app/profile/edit/page.tsx`(섹션 삽입)

## 6. 데이터 구조 / 함수 / 컴포넌트
```tsx
// components/RainbowSection.tsx
export function RainbowSection(): JSX.Element;
// 내부: const [on, setOn] = useState<boolean>(); useEffect(()=> rainbowRepo.isOn().then(setOn));
//       step: 'idle' | 'intro' | 'confirm'
// 활성화: await rainbowRepo.turnOn(); router.push('/rainbow');
// 닫기:   await rainbowRepo.turnOff(); setOn(false);

// components/RainbowConfirmModal.tsx (또는 인라인)
interface Props { open: boolean; onCancel: () => void; onConfirm: () => void; }
// 버튼: "취소할게요" / "무지개연결 활성화"

// app/profile/edit/page.tsx — 폼 하단
// <ProfileForm ... />
// <RainbowSection />   ← 추가
```
- 톤: 평상시 눈에 띄지 않는 조용한 섹션(작은 제목 + 부드러운 설명). 기존 디자인 시스템(`Card`/`Button`) 사용. 경고색·추모 이미지 배제.
- 모달은 브라우저 `confirm()`/`alert()` 사용 금지(자체 컴포넌트로 구현).

## 7. 예외 처리
- `isOn()` 로딩 중에는 섹션을 중립 상태로 표시(깜빡임 최소화).
- `turnOn/turnOff` 실패 시 안내 후 상태 롤백(불일치 방지).
- 활성화 직후 `/rainbow` 이동 실패해도 `rainbowMode`는 이미 `'on'`이므로 재진입 가능.

## 8. 완료 조건
- 진입점이 프로필 최하단에만 있고 `BottomNav`엔 없음(AC-R01).
- 1차 "아직 아니에요"/2차 "취소"에서 저장 안 됨, 2차 "활성화"까지 통과해야 `'on'` 저장(AC-R02).
- 안내·확인 고정 문구가 sanitize 없이 그대로 표시됨(AC-R08).
- "닫기"로 `'off'` 전환, 일상 모드 무영향(AC-R10).
- `npm run build` 타입/빌드 에러 0(AC-R12).

## 9. QA 체크리스트
- [ ] 프로필 최하단에 "무지개연결" 섹션 표시, 하단 탭엔 없음
- [ ] "열기" → 1차 안내 → "아직 아니에요" → 저장 없이 복귀
- [ ] 1차 "계속 보기" → 2차 모달 → "취소할게요" → 저장 없음
- [ ] 2차 "무지개연결 활성화" → `rainbowMode='on'` 저장 + `/rainbow` 이동
- [ ] 활성 상태 재진입 시 안내 건너뛰고 "들어가기/닫기" 표시
- [ ] "닫기" → `'off'` → 일상 모드 정상
- [ ] 고정 안내 문구 원문 그대로 노출(sanitize 안 됨)
- [ ] 브라우저 alert/confirm 미사용
- [ ] `npm run build` 통과

## 10. 금지 사항
- `BottomNav`에 무지개연결 탭 추가 금지(AC-R01 — 영구).
- UI 고정 문구에 `sanitizeRainbow` 적용 금지(보정 지시 R2).
- 이중 확인 없이 곧바로 활성화 금지(AC-R02).
- 브라우저 `alert/confirm/prompt` 사용 금지(블로킹 다이얼로그).
- `/rainbow` 화면/채팅 구현 금지(FEAT-14).
- 설계서 외 라이브러리 추가 금지.
