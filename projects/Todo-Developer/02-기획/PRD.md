# PRD — Todo-Developer

> **① Planning 단계(Sonnet)에서 채운다.** 요구사항-정의서 기반으로 구현 계획·파일 구조를 확정한 뒤, 이 내용을 그대로 local_builder에게 전달한다.

## 확정 기능 (요구사항-정의서 §2 그대로)
- Todo 추가 / 삭제 / 완료 체크 / 진행중·완료 필터 / LocalStorage 저장

## 구현 계획 (Sonnet 작성)
- 파일 구조: 단일 파일 `05-개발/index.html`. `<style>`/`<script>` 인라인. 빌드 도구·외부 의존성 없음. 브라우저에서 더블클릭으로 바로 실행.
- 데이터 모델(할 일 항목 스키마):
  ```js
  {
    id: string,       // Date.now() + 임의문자 조합으로 생성 (crypto.randomUUID 미지원 환경 대비)
    text: string,     // 할 일 내용
    completed: boolean,
    createdAt: number // Date.now()
  }
  ```
- LocalStorage 키/직렬화 방식:
  - 키: `todo-developer:items`
  - 저장: `localStorage.setItem(key, JSON.stringify(items))` — 추가/삭제/토글 시마다 즉시 저장
  - 로드: `JSON.parse(localStorage.getItem(key)) || []` — 페이지 로드 시 1회
- 필터 동작 정의:
  - 상태값: `all`(전체) / `active`(진행중) / `completed`(완료), 기본값 `all`
  - 필터는 화면 표시(렌더링)에만 적용, 저장된 배열 자체는 건드리지 않음
  - 필터 버튼 3개, 현재 선택된 필터는 시각적으로 구분(예: 강조 스타일)

## MVP 경계 (넘지 말 것)
- 우선순위·마감일·정렬·다중 목록·서버 연동 등 일절 없음.
- 다크모드, 애니메이션, 드래그정렬 등 장식적 기능도 금지 — 심플한 MVP.

## Builder 전달용 요청 초안
> (Sonnet이 여기서 정리한 문장을 `ask_builder`에 그대로 투입)
- 아래 요구사항으로 순수 HTML/CSS/JS 단일 파일(index.html)을 만들어줘. 빌드 도구·프레임워크·외부 라이브러리 금지.
  1. 기능: 할 일 텍스트 입력 후 추가 / 항목 삭제 / 완료 체크 토글 / 필터(전체·진행중·완료) / LocalStorage 저장(새로고침해도 유지)
  2. 데이터 모델: `{id, text, completed, createdAt}` 배열
  3. LocalStorage 키: `todo-developer:items`, JSON 직렬화
  4. 필터: 화면 표시만 필터링, 저장 배열은 그대로 유지
  5. 디자인: 심플한 MVP 수준(중앙 정렬 카드, 기본 폰트, 최소한의 색상). 화려한 효과 금지.
  6. 완료된 항목은 취소선 등으로 시각적 구분
  7. 입력 필드 엔터키로도 추가 가능하게
  8. 파일 하나(index.html)에 HTML/CSS/JS 모두 포함
