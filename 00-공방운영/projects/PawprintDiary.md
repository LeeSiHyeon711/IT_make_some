# PawprintDiary (발자국 일기) — 진행상황

> 작성일: 2026-06-24 · 최종 갱신: 2026-06-24
> 문서 목적: 반려동물 일기/건강 기록 + AI 요약 웹 앱의 진행 과정을 기록한다. 인덱스: [README.md](README.md)
> 한 줄 정의: 반려동물의 하루 컨디션·행동을 기록하고 **AI가 요약·해석**해 주는 **로컬 우선(IndexedDB)** 웹 앱. AI 호출은 **서버 Route Handler 프록시**로만(키 클라이언트 비노출).
> 코드 repo: `LeeSiHyeon711/pawprint-diary` (Next.js, 개발 코드·`.env`는 자체 repo).

---

## 현재 상태 (한 줄)
✅ **7단계 납품 완료** (2026-06-24) — 개발(전 이슈 #1~#9 closed/push) → 자동 QA(AC-01~16 전통과) → 수동 테스트 통과 → 납품 산출물 작성·반영 완료.

## 기술 스택
Next.js(App Router) + React + TypeScript + Tailwind, 로컬 저장 **IndexedDB(`idb`)**, AI **`@anthropic-ai/sdk`(서버 전용)**, `date-fns`.

---

## 7단계 진행
- 1 상담(consultant): 요구사항-정의서 — 게이트 승인.
- 2 기획(planner): PRD — 게이트 승인.
- 3 설계(architect): 설계서 + **FEAT-01~09**.
- 4 GitHub(2026-06-22): repo `pawprint-diary` 생성, 이슈 **#1~#9 등록(FEAT 1:1, 번호 일치)**, `05-개발/` git init + remote 연결.
- 5 개발(builder): 이슈 **#1~#9 전부 구현·closed·push**(예: #1 commit `9245d7c`). *builder·repo-manager Bash 차단 → 공방장이 `npm run build`·git/gh 대행, 매 FEAT 빌드 통과 확인, FEAT-05 Mock 가드레일 curl 실측 검증.*
- 6 검수(reviewer): ✅ 완료 — **AC-01~16 전통과, 결함 0**. Playwright MCP로 `localhost:3000` 실제 브라우저 검증, **실제 Anthropic 키로 AI 응답 검증**. 스크린샷 16종(`06-검수/screenshots/`). 가드레일 확인(AC-07 단정 표현 미포함, AI 답변이 단정 진단 회피 + 증상 지속 시 수의사 상담 권장).
- 수동 테스트(사람): ✅ 통과.
- 7 납품(delivery): ✅ 완료 — `실행안내.md` + `고객전달문.md` + `증거자료체크리스트.md`.

## 구현된 기능 (요지)
- 반려동물 프로필 등록/수정(`Pet`: 종·품종·나이·성별·성격·건강메모·사진 Blob)
- 오늘의 일기(식욕/활동/수면/배변 상태값 + 기분 태그 + 사진 + AI 요약)
- 기록 목록/상세(date desc → created_at desc 정렬)
- **AI 일기 요약**(`POST /api/ai/summary` → condition/behavior/observation/memory/vet_note)
- **AI 질문**(`POST /api/ai/ask` → answer)
- **AI 가드레일**(`lib/guardrails.ts`): 단정·의료표현 완화 + 병원권장(vet_note) 조건 판단, 실모델·Mock·폴백 **모든 경로**에 적용
- 안정성: 키 미설정 → 결정적 Mock, 호출 실패/타임아웃 → graceful Mock 폴백, **키 클라이언트 비노출**, AI 입력에서 **사진 Blob 제외**
- 저장: IndexedDB 스토어 `pets`/`entries`/`conversations`/`meta`(백엔드 DB 없음)

---

## 현재 상태 / 다음
- ✅ 상담~납품 **풀사이클 완주**(7단계 납품 완료). 공방 repo에 검수리포트·스크린샷·납품 산출물 반영(commit `28ebe44`).
- README는 구현 기준으로 작성 완료(자체 repo).
- 다음(선택): 사용자 피드백 기반 v2 버전업 또는 배포(호스팅) 검토.

## 비고 / 주의
- 사용자 데이터는 **기기 로컬에만** 저장(클라우드 동기화·계정 없음). 서버 라우트는 AI 프록시 용도.
- `.env`(ANTHROPIC_API_KEY / ANTHROPIC_MODEL)는 자체 repo에만.
