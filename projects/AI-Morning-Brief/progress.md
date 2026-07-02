<!--
중단/재개 추적 파일 (v2). 개발 시작 시 templates/progress.md 내용으로 채워 갱신한다.
재개는 `/개발재개 <프로젝트명>`. 이 파일 + GitHub open issue + git 상태가 재개의 3대 근거.
-->

# progress.md — AI-Morning-Brief

- 최종 갱신: 2026-07-02 (이슈 #21 버그 수정 — 월간/주간 합성 프롬프트 항목별 글자수 상한 지시 추가, 구현 완료)
- 현재 단계: 이슈 #21 구현 완료 (commit 대기). 이슈 #9/#10/#11/#13/#14/#15/#16/#17/#18/#20/#21 모두 commit 대기. 열린 개발 이슈 없음(공방장 확인 필요).

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
| #16 | FEAT-11 월간보고-신호·필터엔진 | 구현 완료 (commit 대기) | src/monthly.py 신규 + tests/test_monthly.py 신규, pytest 38 passed (기존 18 회귀 없음) |
| #17 | FEAT-12 월간보고-합성·전문생성·오케스트레이션 | 구현 완료 (commit 대기) | src/monthly.py(run_monthly 추가)/analyzer.py/reporter.py/main.py 수정, pytest 38 passed (신규 회귀 없음, FEAT-13 몫 e2e 테스트는 미작성) |
| #18 | FEAT-13 월간보고-Discord다이제스트-스케줄러-통합테스트 | 구현 완료 (commit 대기) | notifier.py(build_monthly_message 추가)/monthly.py(hook 연결) 수정 + monthly.plist/test_monthly.py e2e 신규 + README.md 수정, pytest 44 passed (기존 38 회귀 없음) |
| #20 | (BUG, FEAT 미대응) 월간/주간 Discord 메시지 절삭 시 전체 리포트 링크 유실 | 구현 완료 (commit 대기) | src/notifier.py만 수정(`_finalize_with_report_path` 헬퍼 추가, build_weekly_message/build_monthly_message 안전망 교체), pytest 44 passed (회귀 없음) |
| #21 | (BUG, FEAT 미대응) 월간/주간 합성 프롬프트 항목별 글자수 상한 지시 없음 | 구현 완료 (commit 대기) | src/analyzer.py만 수정(`_build_weekly_prompt`/`_build_monthly_prompt` 출력 스키마 지시문에 글자수 상한 문구 추가), pytest 44 passed (회귀 없음) |

## 재개 지점
이슈 #21 구현 완료 — #20(절삭 시 리포트 링크 유실)의 근본 원인이었던, 월간/주간 합성
프롬프트가 항목 개수만 제한하고 글자수는 전혀 제한하지 않던 문제를 수정.
src/analyzer.py의 `_build_weekly_prompt`/`_build_monthly_prompt` 출력 스키마 지시문에서
flow_themes/notable_events/workshop_actions/next_week_watch(next_month_watch) 각 항목에
글자수 상한 문구(80~100자, 월간은 [라벨] 포함 명시)를 기존 개수 제한 문구 뒤에 추가.
LLM 출력을 코드로 강제하는 로직은 이슈 범위 밖(#20 안전망이 최종 방어선) — 손대지 않음.
pytest 44 passed(회귀 없음), 프롬프트 문자열을 assert하는 테스트 없어 별도 갱신 불필요.
이슈 #21은 버그 이슈라 FEAT 문서 매칭 없음 — 이슈 본문 자체를 스펙으로 구현(공방 헌법 2장 7).
다음: 열린 개발 이슈 없음. GitHub 관리자가 commit/push +
이슈 #9/#10/#11/#13/#14/#15/#16/#17/#18/#20/#21 close →
QA 진입 체크리스트 → 사람 /승인 → 6단계 자동 QA.
