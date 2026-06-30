<!--
중단/재개 추적 파일 (v2). 개발 시작 시 templates/progress.md 내용으로 채워 갱신한다.
재개는 `/개발재개 <프로젝트명>`. 이 파일 + GitHub open issue + git 상태가 재개의 3대 근거.
-->

# progress.md — AI-Morning-Brief

- 최종 갱신: 2026-06-28 (이슈 #15 FEAT-10 주간보고 Discord 다이제스트 + launchd 주간 잡 + 통합테스트 구현 완료)
- 현재 단계: 이슈 #15 FEAT-10 구현 완료 (commit 대기). 이슈 #13/#14 BUG/FEAT-09도 commit 대기.

## 이슈별 진행 현황

| 이슈 | FEAT 문서 | 상태 | 비고 |
|------|-----------|------|------|
| #1 | FEAT-01 프로젝트골격-설정로더-CLI | 구현 완료 (commit 대기) | 빌드노트.md 참조 |
| #2 | FEAT-02 저장계층-SQLite | 구현 완료 (commit 대기) | src/storage.py 신규, 빌드노트.md 참조 |
| #3 | FEAT-03 수집기-RSS-진단-fixtures | 구현 완료 (commit 대기) | src/collector.py + tests/fixtures/sample_articles.json 신규, 빌드노트.md 참조 |
| #4 | FEAT-04 Claude분석기-fallback | 구현 완료 (commit 대기) | src/analyzer.py 신규, 빌드노트.md 참조 |
| #5 | FEAT-05 리포트생성기 | 구현 완료 (commit 대기) | src/reporter.py 신규, 빌드노트.md 참조 |
| #6 | FEAT-06 Discord전송기 | 구현 완료 (commit 대기) | src/notifier.py 신규, 빌드노트.md 참조 |
| #7 | FEAT-07 파이프라인-오케스트레이션 | 구현 완료 (commit 대기) | src/pipeline.py 신규, 빌드노트.md 참조 |
| #8 | FEAT-08 스케줄러+통합테스트+문서 | 구현 완료 (commit 대기) | tests/test_pipeline.py + scripts/plist + README.md 신규, 빌드노트.md 참조 |
| #9 | V0.1.1 pytest teardown 빈 디렉토리 잔류 해결 | 구현 완료 (commit 대기) | tests/test_pipeline.py 1파일만, clean fixture tmp_path 전환 |
| #10 | V0.1.1 패치 request_timeout_sec 키 노출 | 구현 완료 (commit 대기) | config/config.yaml + README.md 2파일만, 로직 무변경 |
| #11 | V0.1.1 리포트 저장 경로 구조 변경 | 구현 완료 (commit 대기) | reporter.py + notifier.py(docstring) + test_pipeline.py + README.md 4파일 |

| #13 | (BUG, 운영 발견) | 구현 완료 (commit 대기) | src/pipeline.py + tests/test_pipeline.py, pytest 11 passed |
| #14 | FEAT-09 주간보고-집계·생성엔진 | 구현 완료 (commit 대기) | src/weekly.py 신규 + analyzer.py/reporter.py/main.py 수정, pytest 11 passed |
| #15 | FEAT-10 주간보고-Discord+스케줄러+통합테스트 | 구현 완료 (commit 대기) | notifier.py/weekly.py 수정 + plist/test_weekly.py 신규 + README.md 수정, pytest 18 passed |

## 재개 지점
이슈 #15 FEAT-10 구현 완료 — src/notifier.py(`build_weekly_message`), src/weekly.py(Discord hook),
scripts/com.itsangsang.morningbrief.weekly.plist 신규, tests/test_weekly.py 신규, README.md 수정.
pytest 18 passed (기존 11 + 신규 7, 회귀 없음).
다음: GitHub 관리자가 commit/push + 이슈 #13/#14/#15 close → QA 진입 체크리스트 → 사람 /승인 → 6단계 자동 QA.
