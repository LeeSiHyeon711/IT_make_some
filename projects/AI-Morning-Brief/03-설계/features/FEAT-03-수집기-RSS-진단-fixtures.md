# FEAT-03 — 수집기 (RSS + raw JSON) + 소스 접근성 진단 + 샘플 fixtures

- 매칭 이슈: #3
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
설정 파일의 소스 목록에서 RSS/Atom 기사를 수집하고, 발행 범위로 필터링하며, 원본을 raw JSON으로 보관한다. 또한 **소스 접근성 진단(`check_sources`)** 을 제공해 외부 RSS URL의 유효성을 파이프라인과 분리해 점검한다. `--dry-run` 무상태 보장을 위해 `collect()`는 raw JSON 저장 여부를 `save_raw` 인자로 끌 수 있다. `--test`에서는 실제 네트워크 없이 `sample_articles.json`으로 수집을 대체해 파이프라인 전체를 외부 의존 없이 검증한다.

## 2. 범위
### 구현할 것
- `src/collector.py`: `collect`(**save_raw 인자 포함**), `save_raw_json`, `check_sources`, `format_diagnostics`, 내부 파싱·범위필터·부분실패 처리.
- `tests/fixtures/sample_articles.json`: 최소 6건, **중복 URL 1쌍**, published_at 다양.
### 구현하지 않을 것
- DB 저장 (FEAT-02 함수를 pipeline이 호출), 분석/리포트/전송.
- `--check-sources` / `--dry-run` CLI 분기 배선 자체는 pipeline(FEAT-07)에서. 여기서는 함수 인자(`save_raw`)와 진단 함수만 제공.
- JS 렌더링 스크래핑·GitHub/Reddit 수집(V0.2).

## 3. 입력 / 출력
### 입력
- `collect`: `sources: list[{name,url,enabled}]`, `range_start`, `range_end` (ISO8601 str), `raw_dir`, `timeout`, `test_fixtures: str|None`, **`save_raw: bool=True`**.
- `check_sources`: `sources`, `timeout`.
### 출력
- `collect` → `(articles: list[dict], errors: list[dict])`. article은 설계서 3장 표준 형태(collected_at=now). `save_raw=True`일 때만 `data/raw/<date>/<source>.json` 파일 생성. **`save_raw=False`면 어떤 파일도 쓰지 않음(dry-run 무상태).**
- `check_sources` → `list[dict]` (설계서 9-1 진단 항목). `format_diagnostics`는 콘솔용 표 문자열.

## 4. 동작 흐름
### collect
1. `--test`(test_fixtures 지정): JSON 로드 → 표준 article dict 정규화 → 범위 필터 → 반환. `save_raw=True`일 때만 fixtures raw 저장.
2. 일반: `enabled=True` 소스만 반복:
   a. `requests.get(url, timeout)` → 본문을 `feedparser.parse(bytes)`.
   b. `save_raw`가 True면 raw(entries)를 `save_raw_json`. False면 저장 생략.
   c. 각 entry → article dict: title, link(=url), published(ISO8601 변환, 실패 None), summary→raw_excerpt(2000자), source=name, collected_at=now.
   d. published_at이 `[start,end]`에 들거나 None이면 채택(url 없으면 skip).
   e. 소스 예외 → `errors.append({"source":name,"error":str(e)})` 후 계속(부분 실패).
### check_sources (진단, 읽기 전용)
1. 각 소스(enabled 무관, 끔은 표시만)에 대해:
   a. `requests.get(url, timeout)` → http_status, timeout 여부 기록.
   b. 응답 본문을 `feedparser.parse` → entry_count, parseable(entry≥1).
   c. ok = (2xx and parseable).
   d. 예외별로 timeout/error 채움.
   e. 결과 dict 생성(설계서 9-1 항목 + checked_at).
2. 리스트 반환. DB/파일/Discord에 아무것도 쓰지 않는다.

## 5. 수정 예상 파일
- `05-개발/src/collector.py` (신규)
- `05-개발/tests/fixtures/sample_articles.json` (신규)

## 6. 데이터 구조 / 함수 / 클래스
```python
# src/collector.py
import os, json, logging
from datetime import datetime
import requests, feedparser
from dateutil import parser as dtparser

def _to_iso(v):
    try:
        if v: return dtparser.parse(str(v)).isoformat()
    except Exception: pass
    return None

def _in_range(pub_iso, start, end) -> bool:
    if not pub_iso: return True
    return start <= pub_iso <= end

def save_raw_json(raw_dir, source, day, payload) -> str:
    d = os.path.join(raw_dir, day); os.makedirs(d, exist_ok=True)
    path = os.path.join(d, f"{source.replace(' ','_')}.json")
    with open(path,"w",encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, default=str)
    return path

def collect(sources, range_start, range_end, raw_dir, timeout=15,
            test_fixtures=None, save_raw=True) -> tuple[list[dict], list[dict]]:
    now = datetime.now().isoformat(); day = now[:10]
    articles, errors = [], []
    if test_fixtures:
        raw = json.load(open(test_fixtures, encoding="utf-8"))
        if save_raw: save_raw_json(raw_dir, "fixtures", day, raw)
        for it in raw:
            art = {"url":it["url"],"title":it["title"],
                   "published_at":_to_iso(it.get("published_at")),
                   "source":it.get("source","fixture"),"collected_at":now,
                   "raw_excerpt":it.get("raw_excerpt","")}
            if art["url"] and _in_range(art["published_at"], range_start, range_end):
                articles.append(art)
        return articles, errors
    for s in sources:
        if not s.get("enabled", True): continue
        try:
            resp = requests.get(s["url"], timeout=timeout,
                                headers={"User-Agent":"AI-Morning-Brief/0.1"})
            resp.raise_for_status()
            feed = feedparser.parse(resp.content)
            if save_raw: save_raw_json(raw_dir, s["name"], day, feed.entries)
            for e in feed.entries:
                pub = _to_iso(getattr(e,"published",None) or getattr(e,"updated",None))
                art = {"url":getattr(e,"link",""),"title":getattr(e,"title",""),
                       "published_at":pub,"source":s["name"],"collected_at":now,
                       "raw_excerpt":(getattr(e,"summary","") or "")[:2000]}
                if art["url"] and _in_range(pub, range_start, range_end):
                    articles.append(art)
        except Exception as ex:
            logging.warning("소스 수집 실패 %s: %s", s["name"], ex)
            errors.append({"source":s["name"],"error":str(ex)})
    return articles, errors

def check_sources(sources, timeout=15) -> list[dict]:
    """읽기 전용 진단. 설계서 9-1 항목."""
    out = []
    for s in sources:
        r = {"source_name":s["name"],"url":s["url"],"enabled":s.get("enabled",True),
             "http_status":None,"ok":False,"timeout":False,"parseable":False,
             "entry_count":0,"error":"","checked_at":datetime.now().isoformat()}
        try:
            resp = requests.get(s["url"], timeout=timeout,
                                headers={"User-Agent":"AI-Morning-Brief/0.1"})
            r["http_status"] = resp.status_code
            feed = feedparser.parse(resp.content)
            r["entry_count"] = len(feed.entries)
            r["parseable"] = len(feed.entries) > 0
            r["ok"] = resp.ok and r["parseable"]
            if not resp.ok: r["error"] = f"HTTP {resp.status_code}"
            elif not r["parseable"]: r["error"] = "파싱 불가 또는 엔트리 없음"
        except requests.exceptions.Timeout:
            r["timeout"] = True; r["error"] = "timeout"
        except Exception as ex:
            r["error"] = str(ex)
        out.append(r)
    return out

def format_diagnostics(rows) -> str:
    lines = [f"{'SOURCE':22} {'EN':3} {'HTTP':5} {'OK':3} {'TO':3} {'PARSE':6} {'N':5} ERROR"]
    ok_n = 0
    for r in rows:
        if r["ok"]: ok_n += 1
        lines.append("{:22} {:3} {:5} {:3} {:3} {:6} {:5} {}".format(
            r["source_name"][:22], "y" if r["enabled"] else "n",
            str(r["http_status"] or "-"), "OK" if r["ok"] else "X",
            "y" if r["timeout"] else "n", "yes" if r["parseable"] else "no",
            r["entry_count"], r["error"]))
    fails = [r["source_name"] for r in rows if not r["ok"]]
    lines.append(f"요약: 성공 {ok_n} / 실패 {len(rows)-ok_n}" + (f" → 실패: {fails}" if fails else ""))
    return "\n".join(lines)
```
**sample_articles.json** (6건, url=A 두 번으로 중복 검증):
```json
[
  {"url":"https://ex.com/a","title":"Claude Code 업데이트","published_at":"2026-06-20T09:00:00","source":"Test","raw_excerpt":"Anthropic이 Claude Code를 업데이트했다. MCP 지원이 강화되었다."},
  {"url":"https://ex.com/a","title":"Claude Code 업데이트 (중복)","published_at":"2026-06-20T09:00:00","source":"Test","raw_excerpt":"중복 URL 케이스"},
  {"url":"https://ex.com/b","title":"OpenAI GPT 신모델","published_at":"2026-06-19T12:00:00","source":"Test","raw_excerpt":"OpenAI가 새 GPT 모델을 발표했다."},
  {"url":"https://ex.com/c","title":"MCP 생태계 확장","published_at":"2026-06-10T08:00:00","source":"Test","raw_excerpt":"MCP 서버가 늘었다."},
  {"url":"https://ex.com/d","title":"GitHub Actions AI 자동화","published_at":"2026-06-05T08:00:00","source":"Test","raw_excerpt":"CI에서 AI 자동화."},
  {"url":"https://ex.com/e","title":"일반 펀딩 뉴스","published_at":"2026-06-02T08:00:00","source":"Test","raw_excerpt":"어느 스타트업이 투자 유치."}
]
```

## 7. 예외 처리
- 단일 소스 실패 → `collect`은 errors에 기록 후 계속; `check_sources`는 해당 행 error/timeout 채움. 둘 다 전체를 중단하지 않는다(설계서 8장, 추가 지시 3).
- published 파싱 실패 → published_at None, 기사 자체는 보호적 채택.
- url 없는 entry → 건너뜀.
- `check_sources`는 읽기 전용 — DB/리포트/Discord에 절대 쓰지 않는다(추가 지시 3·4).
- `save_raw=False`(dry-run)일 때 raw 파일을 단 하나도 만들지 않는다(무상태 보장).

## 8. 완료 조건
- `--test`경로: fixtures 6건 로드, 범위 무제한이면 6건(중복 포함) 반환.
- 범위 필터가 published_at 기준 동작.
- `save_raw=True`면 raw JSON 파일이 `data/raw/<date>/`에 생성, `save_raw=False`면 **raw 파일이 생성되지 않음**.
- 한 소스 실패해도 나머지 결과 반환 + errors에 실패 소스 기록.
- `check_sources`가 소스별 설계서 9-1 항목 dict 리스트를 반환하고, `format_diagnostics`가 표 + 요약 줄을 출력.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
python -c "
import os, glob
from src.collector import collect, check_sources, format_diagnostics
arts,errs=collect([], '2026-01-01T00:00:00','2026-12-31T00:00:00','data/raw_t',
                  test_fixtures='tests/fixtures/sample_articles.json')
print(len(arts), errs)   # 6 []
# save_raw=False면 raw 파일 0개 (dry-run 무상태)
import shutil; shutil.rmtree('data/raw_t2', ignore_errors=True)
collect([], '2026-01-01T00:00:00','2026-12-31T00:00:00','data/raw_t2',
        test_fixtures='tests/fixtures/sample_articles.json', save_raw=False)
print('raw files:', glob.glob('data/raw_t2/**/*.json', recursive=True))   # []
arts2,_=collect([], '2026-06-15T00:00:00','2026-06-30T00:00:00','data/raw_t',
                test_fixtures='tests/fixtures/sample_articles.json')
print(len(arts2))        # 범위 내(06-19,06-20) → 3
rows=check_sources([{'name':'Bad','url':'http://127.0.0.1:9/none','enabled':True}], timeout=2)
print(rows[0]['ok'], bool(rows[0]['error']))   # False True
print(format_diagnostics(rows))
"
```

## 10. 금지 사항
- DB 저장을 collector 안에서 하지 않는다(pipeline이 FEAT-02 호출).
- `check_sources`에서 상태 변경(DB/파일/전송) 금지 — 진단은 읽기 전용.
- `save_raw=False`일 때 raw 파일을 쓰지 않는다(dry-run 무상태 위반 금지).
- 분석/요약 로직 선구현 금지.
- 새 수집 소스 타입(GitHub/Reddit/스크래핑) 추가 금지(V0.2).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
