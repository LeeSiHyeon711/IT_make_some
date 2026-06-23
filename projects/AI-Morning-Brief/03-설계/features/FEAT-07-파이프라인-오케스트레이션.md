# FEAT-07 — 파이프라인 오케스트레이션 (catch-up + dry-run 무상태 + 실패복구 + check-sources 디스패치)

- 매칭 이슈: #7
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
FEAT-01~06 모듈을 하나의 실행 흐름으로 묶는다. 수집 범위 결정(catch-up), 단계 조율, `--dry-run`(**무상태**)/`--test`/`--force-fallback`/`--no-discord`/`--from`/`--to`/`--check-sources` 처리, 실패 복구 정책 적용, meta 갱신을 담당한다. 이 FEAT 완료로 V0.1 파이프라인이 end-to-end 동작한다.

## 2. 범위
### 구현할 것
- `src/pipeline.py`: `run(args) -> int`, `_determine_range`, `_dispatch_check_sources`.
- `--dry-run` 시 **DB·raw JSON·report·Discord 어떤 것도 변경하지 않는 무상태 실행** 보장(collect에 save_raw=False 전달 + 수집 직후 종료).
- main.py와의 연결 확인(FEAT-01에서 배선됨).
### 구현하지 않을 것
- 개별 모듈 내부 로직(FEAT-02~06).
- launchd/테스트/문서(FEAT-08).

## 3. 입력 / 출력
### 입력
- `args`(argparse Namespace): `test, dry_run, no_discord, force_fallback, from_dt, to_dt, check_sources`.
- config.yaml(+경로 env override) / sources / operator_profile / secrets.
### 출력
- 종료코드(int): 정상 0, DB 쓰기 등 치명 오류 1, `--check-sources`에서 실패 소스 있으면 1.
- 부수효과: (dry-run 아닐 때만) DB 갱신, `reports/<date>/report.md`, (옵션) Discord 전송, meta 갱신.

## 4. 동작 흐름
```
run(args):
 0. 설정 로드: cfg=load_config(); secrets=load_secrets(); sources=load_sources(cfg.paths.sources)
    profile,is_default = load_operator_profile(cfg.paths.operator_profile)
 0-1. if args.check_sources:  → _dispatch_check_sources(sources, timeout) → print → return (실패 있으면 1)
 1. conn = init_db(cfg.paths.db)        # dry-run이어도 스키마 생성은 무해(빈 테이블). 또는 dry-run 시 건너뛰어도 됨.
 2. start,end,catchup = _determine_range(args, conn, cfg)
 3. fixtures = "tests/fixtures/sample_articles.json" if args.test else None
    articles, errors = collect(sources, start, end, cfg.paths.raw_dir, timeout, fixtures,
                               save_raw=not args.dry_run)     # ★ dry-run이면 raw 저장 안 함
 4. if args.dry_run:  → 계획+미리보기 출력(수집 건수/실패소스/예상 리포트 헤더) → return 0
       # 이 시점까지 DB insert/report/Discord/meta/raw 어떤 쓰기도 없었음 (무상태 보장)
 5. new_count=0; new_articles=[]
    for a in articles:
        if insert_article(conn,a): new_count+=1; new_articles.append(a)   # DB 쓰기 오류시 예외→ return 1
 6. api_key = secrets["ANTHROPIC_API_KEY"]
    result = analyze(new_articles, profile, api_key, model, ..., force_fallback=args.force_fallback)
       # 모델명 오류 포함 모든 Claude 실패는 analyze 내부에서 fallback로 흡수
    by_url = {x["url"]:x for x in result["articles"]}
    merged=[]
    for a in new_articles:
        an = by_url.get(a["url"], {})
        update_analysis(conn, a["url"], an.get("summary",""), an.get("tags",[]),
                        an.get("importance",0), an.get("relevance",0),
                        1 if result["mode"]=="claude" else 0)
        merged.append({**a, **an})
 7. date = now().strftime("%Y-%m-%d")
    md = build_report(date,start,end,merged,result["briefing"],result["mode"],
                      catchup,errors,new_count,is_default)
    path = save_report(cfg.paths.reports_dir, date, md)
 8. if not args.no_discord:
        msg = build_briefing_message(...)
        send_discord(secrets["DISCORD_WEBHOOK_URL"], msg)   # 실패해도 계속
 9. set_meta(conn,"last_success_at",now); set_meta(...range...)
 10. return 0
```

> **dry-run 무상태 핵심**: 4단계에서 즉시 return하기 *전*까지 어떤 쓰기도 일어나지 않아야 한다. collect에 `save_raw=False`를 넘겨 raw JSON도 막는다. (init_db는 빈 스키마 생성만 하므로 데이터 변경 아님 — 단, 더 엄격히 하려면 dry-run일 때 init_db도 건너뛸 수 있다. builder 재량이나 데이터 무변경이 원칙.)

`_determine_range(args, conn, cfg)`:
- `start = args.from_dt or get_meta(conn,"last_success_at") or (now - default_lookback_hours)`
- `end = args.to_dt or now`
- `catchup = bool(args.from_dt or args.to_dt) or (last_success_at가 default_lookback보다 더 과거)`
- 모든 시각은 ISO8601 문자열로 정규화(dateutil).

## 5. 수정 예상 파일
- `05-개발/src/pipeline.py` (신규)
- (확인만) `05-개발/main.py` — FEAT-01에서 `from src.pipeline import run` 배선 완료

## 6. 데이터 구조 / 함수 / 클래스
```python
# src/pipeline.py
import logging
from datetime import datetime, timedelta
from dateutil import parser as dtparser
from src.config_loader import load_config, load_sources, load_secrets, load_operator_profile
from src.storage import init_db, insert_article, update_analysis, get_meta, set_meta
from src.collector import collect, check_sources, format_diagnostics
from src.analyzer import analyze
from src.reporter import build_report, save_report
from src.notifier import build_briefing_message, send_discord

def _iso(dt) -> str: return dt.isoformat()

def _determine_range(args, conn, cfg):
    now = datetime.now()
    lookback = cfg["collection"]["default_lookback_hours"]
    last = get_meta(conn, "last_success_at")
    if args.from_dt: start = dtparser.parse(args.from_dt).isoformat()
    elif last:       start = last
    else:            start = _iso(now - timedelta(hours=lookback))
    end = dtparser.parse(args.to_dt).isoformat() if args.to_dt else _iso(now)
    catchup = bool(args.from_dt or args.to_dt) or (
        bool(last) and dtparser.parse(last) < now - timedelta(hours=lookback))
    return start, end, catchup

def _dispatch_check_sources(sources, timeout) -> int:
    rows = check_sources(sources, timeout)
    print(format_diagnostics(rows))
    return 0 if all(r["ok"] for r in rows) else 1

def run(args) -> int:
    cfg = load_config(); secrets = load_secrets()
    sources = load_sources(cfg["paths"]["sources"])
    profile, is_default = load_operator_profile(cfg["paths"]["operator_profile"])
    timeout = cfg["collection"]["request_timeout_sec"]
    if getattr(args, "check_sources", False):
        return _dispatch_check_sources(sources, timeout)
    conn = init_db(cfg["paths"]["db"])
    start, end, catchup = _determine_range(args, conn, cfg)
    fixtures = "tests/fixtures/sample_articles.json" if args.test else None
    articles, errors = collect(sources, start, end, cfg["paths"]["raw_dir"], timeout,
                               fixtures, save_raw=not args.dry_run)
    if args.dry_run:
        print(f"[dry-run] 범위 {start}~{end} catchup={catchup} 수집 {len(articles)}건 "
              f"실패소스 {[e['source'] for e in errors]} (DB/raw/report/Discord 변경 없음)")
        for a in articles[:5]: print(" -", a["title"])
        return 0
    new_count, new_articles = 0, []
    for a in articles:
        if insert_article(conn, a): new_count += 1; new_articles.append(a)
    result = analyze(new_articles, profile, secrets["ANTHROPIC_API_KEY"],
                     cfg["analysis"]["model"], cfg["analysis"]["max_output_tokens"],
                     cfg["analysis"]["max_articles"], force_fallback=args.force_fallback)
    by_url = {x["url"]: x for x in result["articles"]}; merged = []
    for a in new_articles:
        an = by_url.get(a["url"], {})
        update_analysis(conn, a["url"], an.get("summary",""), an.get("tags",[]),
                        an.get("importance",0), an.get("relevance",0),
                        1 if result["mode"]=="claude" else 0)
        merged.append({**a, **an})
    date = datetime.now().strftime("%Y-%m-%d")
    md = build_report(date, start, end, merged, result["briefing"], result["mode"],
                      catchup, errors, new_count, is_default)
    path = save_report(cfg["paths"]["reports_dir"], date, md)
    if not args.no_discord:
        msg = build_briefing_message(date, start, end, new_count, result["briefing"],
              result["mode"], catchup, errors, path, is_default,
              cfg["discord"]["char_limit"], cfg["discord"]["truncate_marker"])
        send_discord(secrets["DISCORD_WEBHOOK_URL"], msg)
    now_iso = datetime.now().isoformat()
    set_meta(conn, "last_success_at", now_iso)
    set_meta(conn, "last_report_range_start", start)
    set_meta(conn, "last_report_range_end", end)
    logging.info("완료: 신규 %d건, 모드 %s, 리포트 %s", new_count, result["mode"], path)
    return 0
```

## 7. 예외 처리
- `--check-sources`: 진단만, 파이프라인 미실행. 실패 소스 있으면 종료코드 1(진단 신호용), 그래도 예외로 죽지 않음.
- `--dry-run`: 수집 직후 return, **DB·raw JSON·report·Discord·meta 일절 변경 없음**. collect에 save_raw=False 전달.
- 단일 소스 실패: collect의 errors로 수신 → 리포트/Discord에 명시, 계속 진행.
- Claude 실패/키 없음/모델명 오류/`--force-fallback`: analyze가 fallback 반환(중단 없음).
- Discord 실패: send_discord False, 계속 진행, meta는 정상 갱신(설계서 8장).
- DB 쓰기 오류: 예외 전파 → run이 잡지 않고 종료코드 1로 끝나며 `last_success_at` 미갱신 → 다음 실행 catch-up. (try/except로 감싸 `return 1` 처리해도 됨)

## 8. 완료 조건
- `python main.py --test --no-discord` 종료코드 0, DB에 기사 저장, `reports/<date>/report.md` 생성.
- `--test`를 2회 실행해도 중복 URL이 1행만(QA-2).
- `--force-fallback` 시 리포트 fallback 문구(QA-5).
- `--from/--to` 지정 시 리포트에 범위 명시 + catchup 표시(QA-6).
- **`--dry-run`은 DB·raw JSON·report·Discord·meta 어떤 것도 변경하지 않는다** — 실행 후 새 report 파일/신규 DB 행/새 raw 파일이 없어야 한다.
- `--check-sources`는 진단표만 출력하고 파이프라인 미실행.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
cp config/sources.example.yaml config/sources.yaml   # 최초 1회
python main.py --test --no-discord ; echo "exit=$?"
sqlite3 data/morning_brief.db "SELECT COUNT(*) FROM articles;"
python main.py --test --no-discord                    # 2회차
sqlite3 data/morning_brief.db "SELECT url,COUNT(*) c FROM articles GROUP BY url HAVING c>1;"  # 0행
python main.py --test --no-discord --force-fallback
grep -q "AI 분석 실패 / 기본 리포트 생성" reports/$(date +%F)/report.md && echo OK5
python main.py --test --no-discord --from "2026-06-01T00:00:00" --to "2026-06-21T23:59:59"
grep -q "2026-06-01" reports/$(date +%F)/report.md && echo OK6
# dry-run 무상태: 깨끗한 임시 경로로 실행 후 산출물 0 확인
rm -rf data/dr.db reports/dr data/raw_dr
MORNINGBRIEF_DB=data/dr.db MORNINGBRIEF_REPORTS_DIR=reports/dr MORNINGBRIEF_RAW_DIR=data/raw_dr \
  python main.py --test --dry-run
test ! -e data/dr.db && test ! -d reports/dr && test ! -d data/raw_dr && echo "DRY-RUN 무상태 OK"
```

## 10. 금지 사항
- 모듈 내부 로직을 pipeline에서 재구현 금지(각 FEAT 함수 호출만).
- `--dry-run`에서 어떤 상태도 변경하지 않는다(DB/raw/report/Discord/meta 전부).
- Discord/Claude(모델명 오류 포함) 실패를 치명 오류로 처리해 파이프라인을 죽이지 않는다.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
