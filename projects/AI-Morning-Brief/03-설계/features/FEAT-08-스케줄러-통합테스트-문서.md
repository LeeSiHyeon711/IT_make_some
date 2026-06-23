# FEAT-08 — 스케줄러(launchd) + 통합테스트 + 문서

- 매칭 이슈: #8
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
파이프라인을 무인 자동 실행(launchd)으로 묶고, QA-1~QA-6를 자동 검증하는 통합 테스트와 운영 문서(README, 빌드노트)를 작성한다. **테스트는 운영 데이터(`data/morning_brief.db`·`reports/`)를 절대 건드리지 않고 격리된 경로에서만 실행**한다. 이 FEAT 완료로 V0.1이 "검증 가능한 자동화 파이프라인"으로 마감된다.

## 2. 범위
### 구현할 것
- `scripts/com.itsangsang.morningbrief.plist` (launchd, 04:30 실행).
- `tests/test_pipeline.py` (pytest, QA-1~6, **경로 env override로 운영 데이터와 격리**).
- `README.md` (설치·실행·옵션·`--check-sources` 절차·모델명 수정 안내·launchd 등록·V0.1 성공 기준 4개 명령).
- `빌드노트.md` (builder가 빌드 결과·이슈 기록).
### 구현하지 않을 것
- 파이프라인/모듈 로직(FEAT-01~07).
- cron 설정(launchd 채택), GitHub Actions(V0.2).

## 3. 입력 / 출력
### 입력
- 완성된 FEAT-01~07 코드, 설계서 9-2·9-3·9-5·9-6.
### 출력
- plist, test_pipeline.py, README.md, 빌드노트.md.

## 4. 동작 흐름
1. plist 작성(설계서 9-5). 경로는 `/ABS/PATH` 자리표시자 + README에 치환 안내.
2. test_pipeline.py: **경로 env override로 운영 데이터와 격리**.
   - 모든 테스트는 `MORNINGBRIEF_DB=data/test_morning_brief.db`, `MORNINGBRIEF_RAW_DIR=data/raw/test`, `MORNINGBRIEF_REPORTS_DIR=reports/test` 를 subprocess env에 주입(`os.environ` 복사본).
   - `config_loader`가 이 env를 읽어 paths를 override(FEAT-01).
   - 각 테스트 전 fixture가 테스트 DB/리포트 경로만 정리(운영 경로 미접촉).
   - test_qa1_db_insert: 테스트 DB articles COUNT > 0.
   - test_qa2_dedup: 2회 실행 → 테스트 DB 중복 url 0행.
   - test_qa3_report_created: `reports/test/<date>/report.md` 존재.
   - test_qa4_no_discord_exit0: 종료코드 0.
   - test_qa5_fallback: `--force-fallback` → 테스트 리포트에 "AI 분석 실패 / 기본 리포트 생성".
   - test_qa6_range: `--from/--to` → 테스트 리포트에 시작 날짜 문자열.
3. README: 설치 → .env → sources.yaml 복사 → `--check-sources` → `--test` 점검 → launchd 등록 + **V0.1 성공 기준 4개 명령**(설계서 9-3) + **모델명 수정 안내**(설계서 9-6).
4. 빌드노트: 실행 명령·결과·미해결 이슈 기록.

## 5. 수정 예상 파일
- `05-개발/scripts/com.itsangsang.morningbrief.plist` (신규)
- `05-개발/tests/test_pipeline.py` (신규)
- `05-개발/README.md` (신규)
- `05-개발/빌드노트.md` (신규)

## 6. 데이터 구조 / 함수 / 클래스
```python
# tests/test_pipeline.py  (pytest, 경로 env override로 운영 데이터와 격리)
import os, sqlite3, subprocess, sys, datetime, shutil, pytest

ROOT = os.path.dirname(os.path.dirname(__file__))         # 05-개발
TODAY = datetime.date.today().isoformat()

TEST_DB      = "data/test_morning_brief.db"               # 운영 morning_brief.db와 분리
TEST_RAW     = "data/raw/test"
TEST_REPORTS = "reports/test"

def _env():
    e = os.environ.copy()
    e["MORNINGBRIEF_DB"] = TEST_DB
    e["MORNINGBRIEF_RAW_DIR"] = TEST_RAW
    e["MORNINGBRIEF_REPORTS_DIR"] = TEST_REPORTS
    return e

def _run(*flags):
    return subprocess.run([sys.executable, "main.py", "--test", "--no-discord", *flags],
                          cwd=ROOT, capture_output=True, text=True, env=_env())

@pytest.fixture(autouse=True)
def clean():
    # 테스트 경로만 정리. 운영 data/morning_brief.db, reports/ 는 절대 건드리지 않음.
    for p in (os.path.join(ROOT, TEST_DB),):
        if os.path.exists(p): os.remove(p)
    for d in (os.path.join(ROOT, TEST_RAW), os.path.join(ROOT, TEST_REPORTS)):
        shutil.rmtree(d, ignore_errors=True)
    yield

def _test_db_path(): return os.path.join(ROOT, TEST_DB)
def _test_report():  return os.path.join(ROOT, TEST_REPORTS, TODAY, "report.md")

def test_qa1_db_insert():
    _run()
    n = sqlite3.connect(_test_db_path()).execute("SELECT COUNT(*) FROM articles").fetchone()[0]
    assert n > 0

def test_qa2_dedup():
    _run(); _run()
    dups = sqlite3.connect(_test_db_path()).execute(
        "SELECT url,COUNT(*) c FROM articles GROUP BY url HAVING c>1").fetchall()
    assert dups == []

def test_qa3_report_created():
    _run()
    assert os.path.exists(_test_report())

def test_qa4_no_discord_exit0():
    assert _run().returncode == 0

def test_qa5_fallback():
    _run("--force-fallback")
    md = open(_test_report(), encoding="utf-8").read()
    assert "AI 분석 실패 / 기본 리포트 생성" in md

def test_qa6_range():
    _run("--from","2026-06-01T00:00:00","--to","2026-06-21T23:59:59")
    md = open(_test_report(), encoding="utf-8").read()
    assert "2026-06-01" in md
```
README 필수 섹션(설계서 9-3·9-6): 설치 / 환경설정(.env, sources.yaml) / **최초 실행 전 `python main.py --check-sources`** / **`config.yaml`의 analysis.model을 사용 가능한 Anthropic 모델명으로 수정 가능(틀리면 fallback 동작)** / 테스트(V0.1 성공 기준 4개 명령) / 스케줄 등록(launchd) / 옵션 표 / 문제 해결.

## 7. 예외 처리
- 테스트는 **반드시 TEST_DB/TEST_RAW/TEST_REPORTS 경로만** 사용한다. 운영 경로(`data/morning_brief.db`, `reports/`)를 삭제·덮어쓰면 안 된다.
- env override가 동작하지 않으면(FEAT-01 미반영) 테스트가 운영 DB를 만들 수 있으므로, 테스트 시작 시 `MORNINGBRIEF_DB`가 적용됐는지 경로로 확인.
- launchd 등록 시 절대경로 필요 → README에 `pwd`로 치환하는 안내.
- pytest 미설치 환경 → README에 `pip install pytest` 명시.

## 8. 완료 조건
- `pytest -q tests/test_pipeline.py` 6개 테스트 모두 통과(QA-1~6), **운영 `data/morning_brief.db`·`reports/`가 생성·변경되지 않음**.
- plist 파일이 유효한 XML이고 04:30 실행으로 설정됨.
- README에 `--check-sources` 절차(최초 실행 전)·모델명 수정 안내·V0.1 성공 기준 4개 명령이 포함됨.
- 빌드노트.md 존재.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
pip install pytest
cp config/sources.example.yaml config/sources.yaml
# 운영 DB 사전 상태 기록
BEFORE=$(test -e data/morning_brief.db && stat -f %m data/morning_brief.db || echo none)
pytest -q tests/test_pipeline.py            # 6 passed
# 운영 DB가 테스트로 변경되지 않았는지 확인
AFTER=$(test -e data/morning_brief.db && stat -f %m data/morning_brief.db || echo none)
[ "$BEFORE" = "$AFTER" ] && echo "운영 DB 무변경 OK"
ls data/test_morning_brief.db reports/test/ 2>/dev/null   # 테스트 산출물은 별도 경로
plutil -lint scripts/com.itsangsang.morningbrief.plist    # OK (XML 유효성)
grep -q "check-sources" README.md && echo "README OK"
```

## 10. 금지 사항
- 파이프라인/모듈 로직 수정 금지(FEAT-01~07 범위). 테스트가 실패하면 원인 FEAT로 되돌려 수정.
- 테스트에서 운영 데이터 경로(`data/morning_brief.db`, `data/raw/`(test 제외), `reports/`(test 제외))를 읽기 외 변경 금지.
- cron 설정 추가 금지(launchd 채택).
- GitHub Actions/서버 배포 문서화 금지(V0.2).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
