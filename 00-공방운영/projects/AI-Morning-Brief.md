# AI-Morning-Brief — 진행상황 (V0.1 → V0.1.1)

> 작성일: 2026-06-24 · 최종 갱신: 2026-06-24
> 문서 목적: 내부 실사용 자동화 제품 AI-Morning-Brief의 **버전별 전 과정**을 기록한다. 인덱스: [README.md](README.md)
> 한 줄 정의: 매일 아침 **RSS 수집 → SQLite 저장 → Claude 분석(실패 시 fallback) → Markdown 리포트 → Discord 전송**을 자동 수행하는 Python CLI 파이프라인.
> 코드 repo: `LeeSiHyeon711/AI-Morning-Brief` (개발 코드·`.env`는 자체 repo).

---

## 버전 요약
| 버전 | 핵심 | 상태 |
|------|------|------|
| V0.1 | 파이프라인 8 FEAT 구현 + launchd 04:30 등록 + Discord 전송 성공 | ✅ 개발 완료 / **정기 실전 검증 성공** |
| V0.1.1 | 품질 개선 3건(테스트 격리·설정 노출·리포트 경로 구조화) | 개발 완료·푸시 |
| (실험) 월간 흐름 리포트 | 30일 백필·정제 실험 | **실험 성공 / V0.2 후보** |

---

## V0.1 — 파이프라인 구축
- **7단계 진행**:
  - 1 상담(Sonnet): 요구사항-정의서 — 게이트 승인.
  - 2 기획(Sonnet): PRD — 게이트 승인.
  - 3 설계(Opus): 설계서 + **FEAT-01~08**.
  - 4 GitHub(repo-manager): 이슈 #1~#8 등록(FEAT 1:1).
  - 5 개발(builder·Sonnet): 이슈 #1~#8 구현 + 빌드노트.
    - `src/config_loader.py`(설정·소스·비밀값·운영자 프로필), `storage.py`(SQLite), `collector.py`(RSS+소스 진단), `analyzer.py`(Claude+fallback), `reporter.py`(Markdown), `notifier.py`(Discord), `pipeline.py`(오케스트레이션), `tests/test_pipeline.py` + `scripts/...plist`.
  - 6 검수(reviewer): 검수리포트.
  - 7 납품(delivery): 실행안내 + 고객전달문.
- **V0.1 성공 기준(4개 명령 — 모두 통과)**:
  1. `python3 main.py --test --no-discord`
  2. `python3 main.py --test --no-discord --force-fallback`
  3. `python3 main.py --test --dry-run`
  4. `pytest -q tests/test_pipeline.py`
- **운영**: macOS **launchd**로 매일 **04:30 자동 실행 등록 완료**, **Discord 전송 성공** 확인.
- **안전장치**: 키 미설정 → fallback(키워드 요약), Discord 미설정/실패 → 로컬 리포트만(중단 없음), `--dry-run` 무상태, 소스 `--check-sources` 읽기전용 진단.
- **상태**: ✅ 개발 완료. ✅ **정기 04:30 실전 검증 성공** — 운영 정본(`~/AI-Morning-Brief-run/`)에서 launchd가 자동 실행되어 리포트가 정상 생성·Discord 수신 중.
  - 증거: `~/AI-Morning-Brief-run/reports/2026/06/` 에 **6/23·6/24 리포트가 04:31 자동 생성**(=04:30 스케줄 발화), 분석 모드 `claude`(폴백 아님). launchd 에이전트 active.

---

## V0.1.1 — 품질 개선 패치
- 1차 납품 후 발견한 품질 이슈 3건을 `/증상`·소규모 이슈로 처리:
  - **#9** pytest teardown 빈 디렉토리 잔류 해결 — `tests/test_pipeline.py`만, clean fixture `tmp_path` 전환(운영 데이터와 hermetic 격리).
  - **#10** `request_timeout_sec` 설정 키 노출 — `config/config.yaml` + `README.md` 2파일, 로직 무변경.
  - **#11** 리포트 저장 경로 구조 변경 — `reports/YYYY/MM/DD.md` 구조로(`reporter.py` + `notifier.py` docstring + 테스트 + README, 4파일).
- **상태**: ✅ 구현 완료. (progress.md 기준 commit/push 반영, 코드 자체 repo)

---

## 실험 — 월간 흐름 리포트 (V0.2 후보)
- **목적**: 단순 뉴스 수집을 넘어, OpenAI/Codex·Claude Code·Gemini·MCP·Agent·바이브코딩 흐름을 **"상상공방에 적용 가능한 인사이트"로 정제**(개인 AI 인텔리전스 레이더).
- **실험 결과**: 최근 30일 백필 — **수집 287건 → 1차 정제 통과 188건**.
- **상태**: 실험 성공. 노이즈 필터·신뢰도 개선(이슈 **#12**)은 **V0.2 후보로 보류**.

---

## 현재 상태 / 다음
- V0.1.1까지 개발 완료, 코드 자체 repo 반영.
- ✅ **정기 04:30 실전 실행 검증 성공** — 자동 발송·Discord 수신 정상 동작 중(운영 리포트: `~/AI-Morning-Brief-run/reports/`).
- **다음 후보**: V0.2 — 월간 흐름 리포트 노이즈 필터·신뢰도(#12).

## 비고 / 주의
- 정기 실행은 보호 폴더(TCC) 회피를 위해 **운영 정본을 `~/Desktop` 밖(예: `~/AI-Morning-Brief-run/`)**에 두고 등록한다(상세는 자체 repo README).
- `.env`(ANTHROPIC_API_KEY / DISCORD_WEBHOOK_URL)는 자체 repo에만, 공방 repo로 가져오지 않는다.
