# IT상상공방 — 프로젝트 진행상황 트래커 (인덱스)

> 작성일: 2026-06-24 · 최종 갱신: 2026-06-24
> 문서 목적: ChatGPT·Codex·Claude·Cursor 등 **어떤 AI든** 각 프로젝트의 현재 상태와 버전별 진행 과정을 빠르게 파악할 수 있게 한다. 버전이 있는 프로젝트는 **버전별로 전 과정을 생략 없이** 기록한다.
> 상위 문서: [../IT상상공방_온보딩.md](../IT상상공방_온보딩.md) · [../상상공방_작업흐름.md](../상상공방_작업흐름.md)

---

## 전체 상태 한눈 보기

| 프로젝트 | 형태 | 최신 버전 | 상태 | 코드 repo | 상세 |
|---|---|---|---|---|---|
| **PickUpMemo** | Android 앱(Kotlin) | **v3** | v3 빌드·실기기 테스트 완료 / 공정 산출물 공방 repo 반영 완료 | `LeeSiHyeon711/PickUpMemo`(v1), `LeeSiHyeon711/PickUpMemo_v2`(v2·v3) | [PickUpMemo.md](PickUpMemo.md) |
| **AI-Morning-Brief** | Python CLI 자동화 | **V0.1.1** | 개발 완료·푸시 / ✅ **정기 04:30 실전 검증 성공**(자동 발송 중) / 월간리포트=V0.2 후보 | `LeeSiHyeon711/AI-Morning-Brief` | [AI-Morning-Brief.md](AI-Morning-Brief.md) |
| **아이디어 건강검진**(sangsang-lite) | MCP 서버(Python) | 반복 이터레이션 | 코드 완성·푸시 / **Cloud Run 재배포 + PlayMCP 등록 대기(사용자 액션)** | `LeeSiHyeon711/sangsang-lite-mcp` | [아이디어-건강검진.md](아이디어-건강검진.md) |
| **PawprintDiary** | 웹(Next.js) | MVP | ✅ **납품 완료**(상담~납품 풀사이클, AC-01~16 전통과) | `LeeSiHyeon711/pawprint-diary` | [PawprintDiary.md](PawprintDiary.md) |
| **D-Day 카운터** | 정적 웹(HTML) | v1 | 풀사이클 완주(공방 라인 첫 검증 사례) | `LeeSiHyeon711/dday-test` | [D-Day카운터.md](D-Day카운터.md) |

> 개발 코드는 **프로젝트별 자체 git repo로 분리** 보관한다(공방 repo `IT_make_some`는 공정 산출물 문서만 추적). 각 자체 repo에는 `.env`/`local.properties` 등 비밀값이 있으므로 **절대 공방 repo로 끌어오지 않는다.**

---

## 이 트래커 갱신 규칙 (AI/운영자용)
1. **작업이 끝나면** 해당 프로젝트 문서의 "현재 상태 / 버전별 진행" 섹션과 본 인덱스 표를 갱신한다.
2. 각 문서 상단의 **최종 갱신일**을 함께 바꾼다.
3. **버전이 올라가면**(예: v2→v3) 이전 버전 기록을 **지우지 말고**, 새 버전 섹션을 **추가**한다(버전업 보존 원칙).
4. 사실만 적는다. 미검증/대기 항목은 "대기/예정"으로, 측정 안 된 값은 "측정 불가/미집계"로 표기한다. **과장 금지.**
5. 비밀값(키·웹훅·토큰)은 어떤 경우에도 본 문서에 적지 않는다.

## 상태 표기 약속
- `완료` 산출물·검증까지 끝남 · `진행중` 작업 중 · `대기` 사람 액션·게이트 대기 · `보류` 후속 버전으로 미룸 · `미반영` 만들어졌으나 git 미추적

> 각 프로젝트의 **버전별 7단계(상담→기획→설계→GitHub→개발→검수→납품) 전 과정**은 개별 문서에 기록한다.
