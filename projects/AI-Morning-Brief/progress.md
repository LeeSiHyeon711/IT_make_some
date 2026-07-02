<!--
중단/재개 추적 파일 (v2). 개발 시작 시 templates/progress.md 내용으로 채워 갱신한다.
재개는 `/개발재개 <프로젝트명>`. 이 파일 + GitHub open issue + git 상태가 재개의 3대 근거.
-->

# progress.md — AI-Morning-Brief

- 최종 갱신: 2026-07-02 (모든 이슈 closed — V0.1/V0.1.1/V0.2 기능 + 운영 버그 수정 + 문서 보정)
- 현재 단계: **모든 개발 이슈 완결** (이슈 #1~#21 closed, #12만 열림·QA·납품 이후 예정). 사용자 실사용 검증 완료(Discord 재전송 월간리포트 확인).

## 이슈별 진행 현황

| 이슈 | FEAT 문서 | 상태 | 비고 |
|------|-----------|------|------|
| #1 | FEAT-01 프로젝트골격-설정로더-CLI | 구현 완료 (commit `07e437f` push 완료) | 빌드노트.md 참조 |
| #2 | FEAT-02 저장계층-SQLite | 구현 완료 (commit `07e437f` push 완료) | src/storage.py 신규, 빌드노트.md 참조 |
| #3 | FEAT-03 수집기-RSS-진단-fixtures | 구현 완료 (commit `07e437f` push 완료) | src/collector.py + tests/fixtures/sample_articles.json 신규, 빌드노트.md 참조 |
| #4 | FEAT-04 Claude분석기-fallback | 구현 완료 (commit `07e437f` push 완료) | src/analyzer.py 신규, 빌드노트.md 참조 |
| #5 | FEAT-05 리포트생성기 | 구현 완료 (commit `07e437f` push 완료) | src/reporter.py 신규, 빌드노트.md 참조 |
| #6 | FEAT-06 Discord전송기 | 구현 완료 (commit `07e437f` push 완료) | src/notifier.py 신규, 빌드노트.md 참조 |
| #7 | FEAT-07 파이프라인-오케스트레이션 | 구현 완료 (commit `07e437f` push 완료) | src/pipeline.py 신규, 빌드노트.md 참조 |
| #8 | FEAT-08 스케줄러+통합테스트+문서 | 구현 완료 (commit `07e437f` push 완료) | tests/test_pipeline.py + scripts/plist + README.md 신규, 빌드노트.md 참조 |
| #9 | V0.1.1 pytest teardown 빈 디렉토리 잔류 해결 | 구현 완료 (commit `07e437f` push 완료) | tests/test_pipeline.py 1파일만, clean fixture tmp_path 전환 |
| #10 | V0.1.1 패치 request_timeout_sec 키 노출 | 구현 완료 (commit `07e437f` push 완료) | config/config.yaml + README.md 2파일만, 로직 무변경 |
| #11 | V0.1.1 리포트 저장 경로 구조 변경 | 구현 완료 (commit `07e437f` push 완료) | reporter.py + notifier.py(docstring) + test_pipeline.py + README.md 4파일 |

| #13 | (BUG, 운영 발견) | 구현 완료 (commit `07e437f` push 완료) | src/pipeline.py + tests/test_pipeline.py, pytest 44 passed |
| #14 | FEAT-09 주간보고-집계·생성엔진 | 구현 완료 (commit `07e437f` push 완료) | src/weekly.py 신규 + analyzer.py/reporter.py/main.py 수정, pytest 44 passed |
| #15 | FEAT-10 주간보고-Discord+스케줄러+통합테스트 | 구현 완료 (commit `07e437f` push 완료) | notifier.py/weekly.py 수정 + plist/test_weekly.py 신규 + README.md 수정, pytest 44 passed |
| #16 | FEAT-11 월간보고-신호·필터엔진 | 구현 완료 (commit `07e437f` push 완료) | src/monthly.py 신규 + tests/test_monthly.py 신규, pytest 44 passed |
| #17 | FEAT-12 월간보고-합성·전문생성·오케스트레이션 | 구현 완료 (commit `07e437f` push 완료) | src/monthly.py(run_monthly 추가)/analyzer.py/reporter.py/main.py 수정, pytest 44 passed |
| #18 | FEAT-13 월간보고-Discord다이제스트-스케줄러-통합테스트 | 구현 완료 (commit `07e437f` push 완료) | notifier.py(build_monthly_message 추가)/monthly.py(hook 연결) 수정 + monthly.plist/test_monthly.py e2e 신규 + README.md 수정, pytest 44 passed |
| #20 | (BUG, FEAT 미대응) 월간/주간 Discord 메시지 절삭 시 전체 리포트 링크 유실 | 구현 완료 (commit `07e437f` push 완료) | src/notifier.py만 수정(`_finalize_with_report_path` 헬퍼 추가, build_weekly_message/build_monthly_message 안전망 교체), pytest 44 passed |
| #21 | (BUG, FEAT 미대응) 월간/주간 합성 프롬프트 항목별 글자수 상한 지시 없음 | 구현 완료 (commit `07e437f` push 완료) | src/analyzer.py만 수정(`_build_weekly_prompt`/`_build_monthly_prompt` 출력 스키마 지시문에 글자수 상한 문구 추가), pytest 44 passed |
| #19 | (DOCS, 루트 저장소) FEAT-11 peak_day 동작 설명 정정 | 구현 완료 (commit `7ba5363` push 완료) | 03-설계/features/FEAT-11-월간보고-신호필터엔진.md peak_day 정정 반영(빈 월 시나리오 설명 추가) |

---

## 종료 현황 (2026-07-02)

### 모든 이슈 종료
- **개발 이슈**: #1~#8(V0.1 FEAT), #9~#11/#13(V0.1.1 운영 버그), #14/#15(V0.2 주간보고 FEAT), #16/#17/#18(V0.2 월간보고 FEAT), #20/#21(운영 버그 수정) — **모두 closed**
- **문서 이슈**: #19(`type:docs`, 루트 저장소) — **closed**
- **미완료**: #12(월간 상위 요청 enhancement) — QA·납품 통과 후 예정

### Git 상태
- **개발 repo** (`projects/AI-Morning-Brief/05-개발/`)
  - 최종 commit: `07e437f` (#20/#21 버그 수정)
  - push 완료: ✓ origin/main
  - pytest: **44 passed** (회귀 없음)
  
- **루트 저장소** (`/Users/lsh/Desktop/IT_make_some/`)
  - 최종 commit: `7ba5363` (#19 문서보정, FEAT-11 peak_day 정정)
  - push 완료: ✓ origin/main (원격 반영: `8d31873..7ba5363 main -> main`)

### 사용자 검증
- **2026-07-02 실사용 검증 게이트 통과**
  - 공방장이 개발 repo(`05-개발/`)에서 직접 월간리포트 재실행: `python main.py --monthly --month 2026-06`
  - 결과를 Discord 채널로 재전송
  - **사용자가 실제 Discord에서 받아 "확인 완료. 정상 확인."이라고 명시적으로 컨펌**
  - ✓ 6단계 자동 QA/7단계 납품 사전 승인 조건 충족

---

## 현황 메모

### ★ 버그 수정 사이클 성격
#19/#20/#21 버그 수정은 **운영 중 발견된 hotfix 성격**(#13 사례와 동일 패턴)이므로, 정식 FEAT 개발 사이클(6단계 자동 QA·7단계 납품)을 별도로 밟을 필요가 **없다**. 사용자가 이미 실사용으로 "정상"을 확인했으므로 이 사이클은 여기서 **완결**.

### ⚠️ 배포 미진행 항목 (별도 작업 필요)
월간보고 기능(`src/monthly.py`)은 여전히 **launchd 운영 디렉토리**(`~/AI-Morning-Brief-run`)에 배포되지 않았다.
- 현재 배포 상태: **일간보고**(`daily`) + **주간보고**(`weekly`) 만 자동 실행
- 월간보고: **개발 repo에서 수동 실행만 가능** (이번 검증은 공방장이 `05-개발/`에서 직접 실행)
- **필요한 조치**: 월간보고를 매달 자동으로 돌리려면 별도의 **배포 스크립트/plist 설정** 작업이 필요 (운영 구조 정비 후 진행 예정)
