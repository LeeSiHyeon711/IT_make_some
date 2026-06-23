# FEAT-01 — 프로젝트 셋업 + 디자인 시스템

- 매칭 이슈: #01
- 작성일: 2026-06-22
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
PawprintDiary의 Next.js 코드베이스 골격과 **따뜻·부드러운 디자인 시스템(테마 토큰 + 공통 UI 컴포넌트 + 전역 레이아웃/네비)**을 만든다. 이후 모든 화면 FEAT가 이 위에 얹힌다. 포트폴리오 수준 완성도(AC-16)의 기반이다.

## 2. 범위
### 구현할 것
- Next.js 14(App Router) + React 18 + TypeScript + Tailwind CSS 프로젝트 초기화(`05-개발/` 루트).
- `app/globals.css`에 디자인 토큰(CSS 변수, 설계서 4-3 컬러표 그대로) + Tailwind 베이스.
- `app/layout.tsx`: Pretendard 폰트, 배경 토큰, `<BottomNav/>` 자리, `PetProvider`(빈 껍데기라도 import 가능하게 — 실제 구현은 FEAT-02/03에서 채움 → 본 FEAT에서는 children만 감싸는 최소 Provider 골격 제공).
- 공통 UI 컴포넌트(`components/ui/`): `Button`, `Card`, `Tag`, `Field`, `EmptyState`, `Spinner`, `SectionTitle`.
- 전역 컴포넌트: `BottomNav`(오늘/기록/질문/프로필 4탭, 현재 경로 활성표시), `AppHeader`(타이틀+옵션 children).
- 임시 `app/page.tsx`: "발자국일기" 환영 + /today 링크(라우팅 분기는 FEAT-09에서 완성).
- `.env.example`(ANTHROPIC_API_KEY=, ANTHROPIC_MODEL=claude-opus-4-8), `빌드노트.md` 초안(실행법: `npm install` → `npm run dev`).

### 구현하지 않을 것
- IndexedDB/저장 로직(FEAT-02), 실제 폼·화면(FEAT-03~08), AI 호출(FEAT-05), 홈 분기 로직(FEAT-09).
- PetProvider의 실제 상태 로딩(여기선 children 통과만).

### ★ 보정 지시 1 — PetProvider/usePet는 "임시 골격"임을 못박는다
- 본 FEAT의 `PetProvider`는 **children을 그대로 통과시키는 임시 골격**이고, `usePet()`은 **임시 빈 구현**(빌드/타입만 통과하는 최소 형태)이다. 여기서 activePet 로딩·전역 상태를 절대 구현하지 않는다.
- **실제 activePet 로딩/refresh 구조는 FEAT-03에서 교체**한다. FEAT-03 이후 확정 시그니처는 `usePet(): { pet?: Pet; loading: boolean; refresh: () => Promise<void> }` 이다.
- 따라서 **후속 FEAT-04/06/07/08/09는 FEAT-03 이후의 usePet 시그니처(`{ pet, loading, refresh }`) 기준으로 구현**한다. 본 FEAT의 임시 형태에 의존하는 화면 로직을 만들지 않는다.

### ★ 보정 지시 2 — `.env.example` 모델 기본값 고정
- `.env.example`의 `ANTHROPIC_MODEL` 기본값은 **반드시 `claude-opus-4-8`** 로 둔다(공식 모델 목록에 존재하는 값). 임의의 다른 모델명으로 바꾸지 않는다.
- `ANTHROPIC_API_KEY`는 비워 둔다(빈 값). **키가 없어도 Mock 모드로 전체 흐름이 동작**해야 함을 `.env.example` 주석과 `빌드노트.md`에 한 줄 명시한다(실제 Mock 구현은 FEAT-05).

## 3. 입력 / 출력
### 입력
- 없음(신규 셋업). 설계서 2장(스택)·4-3(디자인 토큰).
### 출력
- 빌드되는 Next.js 프로젝트, 재사용 UI 컴포넌트 세트, 전역 레이아웃/네비, 디자인 토큰.

## 4. 동작 흐름
1. `05-개발/`에 Next.js(App Router, TS, Tailwind) 초기화. `package.json` 의존성: `next@14`, `react@18`, `react-dom@18`, `typescript`, `tailwindcss`, `postcss`, `autoprefixer`, `idb`, `@anthropic-ai/sdk`, `date-fns`(설치만, 사용은 후속 FEAT).
2. `globals.css`에 `:root` 토큰 정의 + body 배경 `var(--bg)`, 텍스트 `var(--text)`, Pretendard 적용.
3. `tailwind.config.ts`의 `theme.extend.colors`에 토큰을 매핑(예: `primary: 'var(--primary)'`, `accent`, `surface`, `muted`, `tagbg`, `borderc`)하고 radius/shadow 확장.
4. 공통 UI 컴포넌트 구현(아래 6장 시그니처대로).
5. `layout.tsx`에서 폰트·Provider·children·BottomNav 배치.
6. `npm run build`(또는 `next build`)가 에러 없이 통과하는지 확인.

## 5. 수정 예상 파일
- `05-개발/package.json`, `next.config.mjs`, `tsconfig.json`, `tailwind.config.ts`, `postcss.config.mjs`
- `05-개발/app/globals.css`, `app/layout.tsx`, `app/page.tsx`
- `05-개발/components/ui/Button.tsx`, `Card.tsx`, `Tag.tsx`, `Field.tsx`, `EmptyState.tsx`, `Spinner.tsx`, `SectionTitle.tsx`
- `05-개발/components/BottomNav.tsx`, `components/AppHeader.tsx`
- `05-개발/lib/petContext.tsx`(골격), `05-개발/.env.example`, `05-개발/빌드노트.md`

## 6. 데이터 구조 / 함수 / 클래스
```tsx
// components/ui/Button.tsx
type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'soft' | 'ghost'; // primary=살구 채움, soft=tagbg, ghost=테두리
  full?: boolean;
};
export function Button(props: ButtonProps): JSX.Element

// components/ui/Card.tsx — radius 16, surface 배경, 옅은 그림자
export function Card(props: { children: React.ReactNode; className?: string }): JSX.Element

// components/ui/Tag.tsx — pill 칩 (선택형/표시형)
export function Tag(props: { label: string; selected?: boolean; onClick?: () => void }): JSX.Element

// components/ui/Field.tsx — label + children(input/select 등) 묶음
export function Field(props: { label: string; required?: boolean; hint?: string; children: React.ReactNode }): JSX.Element

// components/ui/EmptyState.tsx — 🐾 아이콘 + 안내 문구 + 선택 액션
export function EmptyState(props: { icon?: string; title: string; description?: string; action?: React.ReactNode }): JSX.Element

// components/ui/Spinner.tsx — 로딩 표시
export function Spinner(props: { label?: string }): JSX.Element

// components/ui/SectionTitle.tsx — 섹션 제목(예: "오늘의 컨디션")
export function SectionTitle(props: { children: React.ReactNode }): JSX.Element

// components/BottomNav.tsx — usePathname()으로 활성 탭 표시
const NAV = [
  { href: '/today',   label: '오늘',  icon: '🐾' },
  { href: '/entries', label: '기록',  icon: '📖' },
  { href: '/ask',     label: '질문',  icon: '💬' },
  { href: '/profile/edit', label: '프로필', icon: '🐶' },
];

// lib/petContext.tsx (임시 골격 — 보정 지시 1)
// 본 FEAT에서는 빈 골격만. 실제 구현은 FEAT-03에서 아래 시그니처로 "교체"한다.
export function PetProvider({ children }: { children: React.ReactNode }): JSX.Element // 지금은 children 그대로 반환
export function usePet(): { /* 임시: 빌드 통과용 최소 형태. FEAT-03에서 { pet?, loading, refresh } 로 교체 */ }
```
- 디자인 톤: 설계서 4-3 토큰 사용. 모서리 둥글게, 여백 넉넉, 🐾는 악센트로만. 유아틱 금지.

## 7. 예외 처리
- 폰트 로드 실패 시 시스템 산세리프로 폴백(`font-family` 폴백 체인).
- 컴포넌트는 `className` 머지를 허용해 후속 FEAT가 확장 가능하게.

## 8. 완료 조건
- `npm install` 후 `npm run dev`가 에러 없이 기동되고 `/`가 따뜻한 톤으로 렌더된다.
- `npm run build`가 **타입/빌드 에러 0**으로 통과한다. (전역 규칙 — 설계서 7장)
- BottomNav 4탭이 보이고 클릭 시 해당 경로로 이동(대상 페이지는 후속 FEAT 전까지 404여도 무방, 네비 자체는 동작).
- 7종 공통 UI 컴포넌트가 존재하고 import 가능하다.
- `.env.example`에 `ANTHROPIC_MODEL=claude-opus-4-8`가 들어 있고, 키 없이 Mock 동작이 전제됨이 주석/빌드노트에 명시된다(보정 지시 2).

## 9. 테스트 방법
1. `cd 05-개발 && npm install && npm run dev` → 브라우저 `http://localhost:3000` 접속.
2. 배경이 아이보리(`#FFFBF6`), 글자가 브라운 톤인지 육안 확인.
3. 하단 4탭이 보이고 라벨/아이콘이 표시되는지 확인.
4. `npm run build` 통과 확인.

## 10. 금지 사항
- 이 기능과 관련 없는 화면/폼 선구현 금지(FEAT-03~08 영역).
- IndexedDB·AI 호출 로직 선구현 금지.
- 디자인 토큰 외 임의 색상 하드코딩 남발 금지(토큰 사용).
- 설계서에 없는 라이브러리 추가 금지.
- PetProvider/usePet에 activePet 로딩 등 **실상태 로직 구현 금지**(FEAT-03 소관 — 보정 지시 1).
- `.env.example`의 `ANTHROPIC_MODEL` 기본값을 `claude-opus-4-8` 외로 바꾸지 말 것(보정 지시 2).
